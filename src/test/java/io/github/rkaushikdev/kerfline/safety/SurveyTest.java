package io.github.rkaushikdev.kerfline.safety;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SurveyTest {

    private static SurveyRecord survey(String kind, List<Reading> readings) {
        return Survey.makeSurvey(kind, "2025-03-14", "Ludlum 3", readings);
    }

    private static Reading at(double atFt, double rateMrPerHour) {
        return new Reading(atFt, rateMrPerHour);
    }

    @Nested
    class MakeReading {

        @Test
        void roundsToWhatAMeterCanBeReadTo() {
            Reading reading = Survey.makeReading(25.04, 1.9994);
            assertThat(reading.atFt()).isEqualTo(25);
            assertThat(reading.rateMrPerHour()).isEqualTo(1.999);
        }

        @Test
        void allowsAReadingOfNothingAtAll() {
            assertThat(Survey.makeReading(300, 0).rateMrPerHour()).isEqualTo(0);
        }

        @Test
        void refusesADistanceAtZeroOrANegativeRate() {
            assertThatThrownBy(() -> Survey.makeReading(0, 1)).hasMessageContaining("greater than 0");
            assertThatThrownBy(() -> Survey.makeReading(10, -1)).hasMessageContaining("not be below 0");
        }
    }

    @Nested
    class MakeSurvey {

        @Test
        void namesTheThreeKinds() {
            assertThat(Survey.surveyKinds()).containsExactly("boundary", "perimeter", "retraction");
        }

        @Test
        void buildsASurvey() {
            SurveyRecord record = survey("boundary", List.of(at(300, 1.5)));
            assertThat(record.kind()).isEqualTo("boundary");
            assertThat(record.instrument()).isEqualTo("Ludlum 3");
            assertThat(record.readings()).hasSize(1);
        }

        @Test
        void refusesASurveyWithNoReadingsOnIt() {
            assertThatThrownBy(() -> survey("retraction", List.of()))
                    .hasMessageContaining("no readings is not a survey");
        }

        @Test
        void refusesASurveyWithNoMeterNamed() {
            assertThatThrownBy(() -> Survey.makeSurvey("boundary", "2025-03-14", " ", List.of()))
                    .hasMessageContaining("names the meter");
        }

        @Test
        void refusesAKindItDoesNotKnow() {
            assertThatThrownBy(() -> Survey.makeSurvey("walkaround", "2025-03-14", "Ludlum 3", List.of(at(1, 1))))
                    .hasMessageContaining("a survey is one of");
        }

        @Test
        void refusesADateThatIsNotARealDay() {
            assertThatThrownBy(() -> Survey.makeSurvey("boundary", "2025-02-30", "Ludlum 3", List.of(at(1, 1))))
                    .hasMessageContaining("not a real calendar day");
        }
    }

    @Nested
    class JudgeRetraction {

        @Test
        void saysTheSourceIsHomeWhenTheCameraReadsAtBackground() {
            SurveyRecord record = survey("retraction", List.of(at(1, 0.03)));
            RetractionVerdict verdict = Survey.judgeRetraction(record);
            assertThat(verdict.sourceIsHome()).isTrue();
            assertThat(verdict.atBackground()).isTrue();
            assertThat(verdict.action()).contains("approach permitted");
        }

        @Test
        void refusesToLetAnybodyApproachACameraStillReadingHot() {
            SurveyRecord record = survey("retraction", List.of(at(1, 450)));
            RetractionVerdict verdict = Survey.judgeRetraction(record);
            assertThat(verdict.sourceIsHome()).isFalse();
            assertThat(verdict.action()).contains("do not approach");
        }

        @Test
        void takesTheHighestReadingAndNotTheLastOne() {
            SurveyRecord record = survey("retraction", List.of(at(1, 450), at(1, 0.03)));
            assertThat(Survey.judgeRetraction(record).sourceIsHome()).isFalse();
        }

        @Test
        void flagsAReadingAboveBackgroundButUnderTheThreshold() {
            SurveyRecord record = survey("retraction", List.of(at(1, 1.5)));
            RetractionVerdict verdict = Survey.judgeRetraction(record);
            assertThat(verdict.sourceIsHome()).isTrue();
            assertThat(verdict.atBackground()).isFalse();
        }

        @Test
        void refusesToJudgeASurveyOfAnotherKind() {
            SurveyRecord record = survey("boundary", List.of(at(300, 1)));
            assertThatThrownBy(() -> Survey.judgeRetraction(record)).hasMessageContaining("not a retraction survey");
        }
    }

    @Nested
    class JudgeBoundary {

        @Test
        void passesWhenTheReadingsAtTheRopeAreUnderTheLimit() {
            SurveyRecord record = survey("boundary", List.of(at(300, 1.5)));
            assertThat(Survey.judgeBoundary(record, 300).pass()).isTrue();
        }

        @Test
        void failsWhenAReadingOutsideTheRopeIsOverTheLimit() {
            SurveyRecord record = survey("boundary", List.of(at(300, 4)));
            BoundaryVerdict verdict = Survey.judgeBoundary(record, 300);
            assertThat(verdict.pass()).isFalse();
            assertThat(verdict.breaches()).hasSize(1);
            assertThat(verdict.reason())
                    .hasValueSatisfying(reason -> assertThat(reason).contains("inside the two milliroentgen line"));
        }

        @Test
        void failsWhenNoReadingWasTakenAtTheRopeAtAll() {
            SurveyRecord record = survey("boundary", List.of(at(50, 40)));
            BoundaryVerdict verdict = Survey.judgeBoundary(record, 300);
            assertThat(verdict.pass()).isFalse();
            assertThat(verdict.reason())
                    .hasValueSatisfying(reason -> assertThat(reason).contains("no reading was taken"));
        }

        @Test
        void ignoresHotReadingsTakenInsideTheRope() {
            SurveyRecord record = survey("boundary", List.of(at(20, 900), at(300, 1.5)));
            assertThat(Survey.judgeBoundary(record, 300).pass()).isTrue();
        }

        @Test
        void refusesToJudgeASurveyOfAnotherKind() {
            SurveyRecord record = survey("retraction", List.of(at(1, 0.03)));
            assertThatThrownBy(() -> Survey.judgeBoundary(record, 300)).hasMessageContaining("not a boundary survey");
        }
    }

    @Nested
    class BoundaryFromReadings {

        @Test
        void movesTheRopeOutToWhereTheLimitActuallyFalls() {
            SurveyRecord record = survey("boundary", List.of(at(100, 8)));
            assertThat(Survey.boundaryFromReadings(record)).isCloseTo(200, digits(0));
        }

        @Test
        void takesTheWorstReadingRatherThanTheFurthest() {
            // 0.5 mR/h at 300 ft implies a weaker source than 8 mR/h at 100 ft, so
            // the near reading governs and the rope goes to 200 ft.
            SurveyRecord record = survey("boundary", List.of(at(100, 8), at(300, 0.5)));
            assertThat(Survey.boundaryFromReadings(record)).isCloseTo(200, digits(0));
        }

        @Test
        void letsADistantReadingGovernWhenItImpliesTheHotterSource() {
            // 1 mR/h still showing at 300 ft implies more than 8 mR/h at 100 ft does,
            // which is exactly the scatter the calculation did not know about.
            SurveyRecord record = survey("boundary", List.of(at(100, 8), at(300, 1)));
            assertThat(Survey.boundaryFromReadings(record)).isCloseTo(212, digits(0));
        }

        @Test
        void isNothingWhenEveryReadingIsClean() {
            SurveyRecord record = survey("boundary", List.of(at(300, 0)));
            assertThat(Survey.boundaryFromReadings(record)).isEqualTo(0);
        }
    }

    @Nested
    class HighRadiationReadings {

        @Test
        void picksOutReadingsOverTheHighRadiationLimit() {
            SurveyRecord record = survey("perimeter", List.of(at(10, 400), at(200, 5)));
            assertThat(Survey.highRadiationReadings(record)).hasSize(1);
        }

        @Test
        void findsNothingOnACleanSurvey() {
            SurveyRecord record = survey("perimeter", List.of(at(200, 5)));
            assertThat(Survey.highRadiationReadings(record)).isEmpty();
        }
    }

    @Nested
    class MissingSurveys {

        @Test
        void namesWhatHasNotBeenDone() {
            List<SurveyRecord> done = List.of(survey("boundary", List.of(at(300, 1))));
            assertThat(Survey.missingSurveys(done)).containsExactly("perimeter", "retraction");
        }

        @Test
        void isEmptyOnceAllThreeAreDone() {
            List<SurveyRecord> done = Survey.surveyKinds().stream()
                    .map(kind -> survey(kind, List.of(at(1, 1))))
                    .toList();
            assertThat(Survey.missingSurveys(done)).isEmpty();
        }

        @Test
        void namesAllThreeWhenNothingHasBeenDone() {
            assertThat(Survey.missingSurveys(List.of())).containsExactly("boundary", "perimeter", "retraction");
        }
    }

    @Nested
    class MeterCalibrations {

        private final MeterCalibration good = Survey.makeCalibration("Ludlum 3", "2025-01-10", 0.12, "Atlas Cal");

        @Test
        void passesAMeterWithinTwentyPercentAndHoldsItForSixMonths() {
            assertThat(good.pass()).isTrue();
            assertThat(good.dueBy()).isEqualTo("2025-07-10");
            assertThat(good.laboratory()).hasValue("Atlas Cal");
            assertThat(Survey.calibrationIntervalMonths()).isEqualTo(6);
            assertThat(Survey.makeCalibration("Ludlum 3", "2025-01-10", 0.2, null)
                            .pass())
                    .isTrue();
            assertThat(Survey.makeCalibration("Ludlum 3", "2025-01-10", 0.21, null)
                            .pass())
                    .isFalse();
        }

        @Test
        void saysWhetherTheMeterIsInCalibrationOnTheDay() {
            CalibrationStatus inside = Survey.meterCurrent(List.of(good), "Ludlum 3", "2025-03-14");
            assertThat(inside.current()).isTrue();
            assertThat(inside.daysLeft()).hasValue(118);
            assertThat(Survey.meterCurrent(List.of(good), "Ludlum 3", "2025-07-11")
                            .reason())
                    .hasValue("the calibration has run out");
            assertThat(Survey.meterCurrent(List.of(good), "Ludlum 9", "2025-03-14")
                            .reason())
                    .hasValue("no calibration on file");
        }

        @Test
        void neverLetsAFailedCalibrationComeGoodByWaiting() {
            MeterCalibration failed = Survey.makeCalibration("Ludlum 3", "2025-02-01", 0.3, null);
            CalibrationStatus status = Survey.meterCurrent(List.of(good, failed), "Ludlum 3", "2025-03-14");
            assertThat(status.current()).isFalse();
            assertThat(status.reason()).hasValue("the last calibration failed");
            assertThat(status.latest()).hasValue(failed);
        }

        @Test
        void countsASurveyOnlyWhenItsMeterWasInCalibration() {
            List<SurveyRecord> done = Survey.surveyKinds().stream()
                    .map(kind -> survey(kind, List.of(at(1, 1))))
                    .toList();
            assertThat(Survey.missingSurveys(done, List.of(good))).isEmpty();
            assertThat(Survey.missingSurveys(done, List.of())).containsExactly("boundary", "perimeter", "retraction");
            MeterCalibration late = Survey.makeCalibration("Ludlum 3", "2025-03-15", 0.05, null);
            assertThat(Survey.missingSurveys(done, List.of(late))).hasSize(3);
        }

        @Test
        void refusesACalibrationWithNoMeterOrANegativeError() {
            assertThatThrownBy(() -> Survey.makeCalibration(" ", "2025-01-10", 0.1, null))
                    .hasMessageContaining("names the meter");
            assertThatThrownBy(() -> Survey.makeCalibration("Ludlum 3", "2025-01-10", -0.1, null))
                    .hasMessageContaining("calibration error");
        }
    }
}
