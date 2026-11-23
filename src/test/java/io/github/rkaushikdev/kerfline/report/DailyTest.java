package io.github.rkaushikdev.kerfline.report;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.kerfline.safety.MeterCalibration;
import io.github.rkaushikdev.kerfline.safety.Survey;
import io.github.rkaushikdev.kerfline.safety.SurveyRecord;
import io.github.rkaushikdev.kerfline.store.EntryDraft;
import io.github.rkaushikdev.kerfline.store.Journal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DailyTest {

    private static final Journal JOURNAL = Journal.appendAll(
            Journal.makeJournal("C-4471"),
            List.of(
                    EntryDraft.of("shot", "2025-03-14", "J-2", Map.of("films", 3)),
                    EntryDraft.of("shot", "2025-03-14", "J-3", Map.of("films", 3)),
                    EntryDraft.of("verdict", "2025-03-14", "J-2", Map.of("disposition", "accept")),
                    EntryDraft.of("verdict", "2025-03-14", "J-3", Map.of("disposition", "repair")),
                    EntryDraft.of("shot", "2025-03-15", "J-4", Map.of("films", 2))));

    private static SurveyRecord survey(String kind, String day) {
        return Survey.makeSurvey(kind, day, "Ludlum 3", List.of(Survey.makeReading(10, 1)));
    }

    private static List<SurveyRecord> allThreeOn(String day) {
        return List.of("boundary", "perimeter", "retraction").stream()
                .map(kind -> survey(kind, day))
                .toList();
    }

    private static final List<SurveyRecord> ALL_SURVEYS = allThreeOn("2025-03-14");

    private static DailyRequest.Builder on(String day) {
        return DailyRequest.builder().day(day).journal(JOURNAL);
    }

    @Nested
    class BuildDaily {

        private final DailyReport report = Daily.buildDaily(on("2025-03-14")
                .job("C-4471")
                .crew(List.of("R. Ibarra", "D. Whyte"))
                .surveys(ALL_SURVEYS)
                .source("IR-7741")
                .build());

        @Test
        void takesOnlyThatDayEntries() {
            assertThat(report.entries()).isEqualTo(4);
            assertThat(report.kinds()).isEqualTo(Map.of("shot", 2, "verdict", 2));
        }

        @Test
        void listsTheJointsShotInRegisterOrder() {
            assertThat(report.jointsShot()).containsExactly("J-2", "J-3");
        }

        @Test
        void addsTheFilmsUp() {
            assertThat(report.films()).isEqualTo(6);
        }

        @Test
        void listsTheReadingsWithTheirDispositions() {
            assertThat(report.verdicts())
                    .containsExactly(DailyVerdict.of("J-2", "accept"), DailyVerdict.of("J-3", "repair"));
        }

        @Test
        void recordsTheCrewByName() {
            assertThat(report.crew()).containsExactly("R. Ibarra", "D. Whyte");
        }

        @Test
        void isCompleteWhenAllThreeSurveysWereDone() {
            assertThat(report.complete()).isTrue();
            assertThat(report.surveysMissing()).isEmpty();
        }

        @Test
        void leavesTheNextDayOut() {
            assertThat(report.jointsShot()).doesNotContain("J-4");
        }

        @Test
        void refusesAReportWithNoJournal() {
            assertThatThrownBy(() -> Daily.buildDaily(
                            DailyRequest.builder().day("2025-03-14").build()))
                    .hasMessageContaining("needs a journal");
        }

        @Test
        void refusesADateThatIsNotARealDay() {
            assertThatThrownBy(() -> Daily.buildDaily(on("2025-02-30").build()))
                    .hasMessageContaining("not a real calendar day");
        }
    }

    @Nested
    class TheSurveyCheck {

        @Test
        void namesWhatIsMissingWhenASourceWentOut() {
            DailyReport report = Daily.buildDaily(on("2025-03-14")
                    .surveys(List.of(survey("boundary", "2025-03-14")))
                    .source("IR-7741")
                    .build());
            assertThat(report.complete()).isFalse();
            assertThat(report.surveysMissing()).containsExactly("perimeter", "retraction");
        }

        @Test
        void asksForNothingWhenNoSourceLeftTheStore() {
            DailyReport report = Daily.buildDaily(
                    on("2025-03-14").surveys(List.of()).source(null).build());
            assertThat(report.complete()).isTrue();
            assertThat(report.surveysMissing()).isEmpty();
        }

        @Test
        void doesNotCountASurveyFromAnotherDay() {
            DailyReport report = Daily.buildDaily(on("2025-03-14")
                    .surveys(allThreeOn("2025-03-13"))
                    .source("IR-7741")
                    .build());
            assertThat(report.surveysMissing()).hasSize(3);
        }

        @Test
        void checksTheSameDayAgainOnceTheSurveysAreFiled() {
            DailyRequest early = on("2025-03-14").source("IR-7741").build();
            DailyReport later =
                    Daily.buildDaily(early.toBuilder().surveys(ALL_SURVEYS).build());
            assertThat(Daily.buildDaily(early).complete()).isFalse();
            assertThat(later.complete()).isTrue();
            assertThat(later.surveysDone()).containsExactly("boundary", "perimeter", "retraction");
        }
    }

    @Nested
    class DailyTotalsOfTheDay {

        private final DailyReport report = Daily.buildDaily(
                on("2025-03-14").surveys(ALL_SURVEYS).source("IR-7741").build());

        @Test
        void countsTheDay() {
            DailyTotals totals = Daily.dailyTotals(report);
            assertThat(totals.joints()).isEqualTo(2);
            assertThat(totals.films()).isEqualTo(6);
            assertThat(totals.read()).isEqualTo(2);
            assertThat(totals.accepted()).isEqualTo(1);
            assertThat(totals.repaired()).isEqualTo(1);
        }

        @Test
        void reportsARateWithTheCountItCameFrom() {
            assertThat(Daily.dailyTotals(report).rate()).isCloseTo(0.5, digits(4));
        }

        @Test
        void isZeroOnADayWithNothingRead() {
            DailyReport quiet = Daily.buildDaily(on("2025-03-15").source(null).build());
            assertThat(Daily.dailyTotals(quiet).rate()).isEqualTo(0);
            assertThat(Daily.dailyTotals(quiet).read()).isZero();
        }
    }

    @Nested
    class RenderDaily {

        private final DailyReport report = Daily.buildDaily(on("2025-03-14")
                .job("C-4471")
                .crew(List.of("R. Ibarra"))
                .surveys(ALL_SURVEYS)
                .source("IR-7741")
                .build());

        @Test
        void printsTheHeadingTheJobAndTheDate() {
            String text = Daily.renderDaily(report);
            assertThat(text).contains("DAILY FIELD REPORT");
            assertThat(text).contains("C-4471");
            assertThat(text).contains("2025-03-14");
        }

        @Test
        void printsTheWorkAndTheReadings() {
            String text = Daily.renderDaily(report);
            assertThat(text).contains("Films exposed");
            assertThat(text).contains("Films exposed        6");
            assertThat(text).contains("J-2");
            assertThat(text).contains("repair");
        }

        @Test
        void saysTheSurveysAreCompleteWhenTheyAre() {
            assertThat(Daily.renderDaily(report)).contains("Surveys complete");
        }

        @Test
        void shoutsAboutMissingSurveys() {
            DailyReport shortDay = Daily.buildDaily(
                    on("2025-03-14").surveys(List.of()).source("IR-7741").build());
            assertThat(Daily.renderDaily(shortDay)).contains("MISSING SURVEYS");
        }

        @Test
        void saysSoWhenNoSourceWentOut() {
            DailyReport quiet = Daily.buildDaily(on("2025-03-16").source(null).build());
            String text = Daily.renderDaily(quiet);
            assertThat(text).contains("No source left the store.");
            assertThat(text).contains("Nothing read today.");
        }

        @Test
        void keepsEveryLineInsideThePaperWidth() {
            for (String line : Daily.renderDaily(report).split("\n")) {
                assertThat(line.length()).isLessThanOrEqualTo(78);
            }
        }
    }

    @Nested
    class DaysMissingSurveys {

        @Test
        void findsTheDaysASourceWentOutAndTheSurveysAreShort() {
            DailyReport shortDay = Daily.buildDaily(
                    on("2025-03-14").surveys(List.of()).source("IR-7741").build());
            DailyReport good = Daily.buildDaily(on("2025-03-15")
                    .surveys(allThreeOn("2025-03-15"))
                    .source("IR-7741")
                    .build());
            List<MissedSurveys> found = Daily.daysMissingSurveys(List.of(shortDay, good));
            assertThat(found).hasSize(1);
            assertThat(found.get(0).day()).isEqualTo("2025-03-14");
        }

        @Test
        void leavesOutDaysWhenNoSourceWentOut() {
            DailyReport quiet = Daily.buildDaily(on("2025-03-16").source(null).build());
            assertThat(Daily.daysMissingSurveys(List.of(quiet))).isEmpty();
        }
    }

    @Nested
    class TheMetersTheSurveysWereTakenOn {

        private final MeterCalibration current = Survey.makeCalibration("Ludlum 3", "2025-01-10", 0.1, null);
        private final MeterCalibration lapsed = Survey.makeCalibration("Ludlum 3", "2024-08-01", 0.1, null);

        @Test
        void acceptsSurveysOffAMeterInCalibration() {
            DailyReport report = Daily.buildDaily(on("2025-03-14")
                    .surveys(ALL_SURVEYS)
                    .source("IR-7741")
                    .calibrations(List.of(current))
                    .build());
            assertThat(report.complete()).isTrue();
            assertThat(report.metersOutOfCalibration()).isEmpty();
        }

        @Test
        void countsNoSurveyOffAMeterWhoseCalibrationRanOut() {
            DailyReport report = Daily.buildDaily(on("2025-03-14")
                    .surveys(ALL_SURVEYS)
                    .source("IR-7741")
                    .calibrations(List.of(lapsed))
                    .build());
            assertThat(report.complete()).isFalse();
            assertThat(report.surveysMissing()).containsExactly("boundary", "perimeter", "retraction");
            assertThat(report.metersOutOfCalibration()).containsExactly("Ludlum 3");
            assertThat(report.surveysDone()).hasSize(3);
        }

        @Test
        void saysWhichMeterWasOutOnThePrintedReport() {
            DailyReport report = Daily.buildDaily(on("2025-03-14")
                    .surveys(ALL_SURVEYS)
                    .source("IR-7741")
                    .calibrations(List.of(lapsed))
                    .build());
            String text = Daily.renderDaily(report);
            assertThat(text).contains("MISSING SURVEYS: boundary, perimeter, retraction");
            assertThat(text).contains("Meter out of calibration: Ludlum 3");
        }

        @Test
        void leavesTheMetersAloneWhenNoCalibrationsWereBrought() {
            DailyRequest request =
                    on("2025-03-14").surveys(ALL_SURVEYS).source("IR-7741").build();
            assertThat(request.calibrations()).isNull();
            DailyReport report = Daily.buildDaily(request);
            assertThat(report.complete()).isTrue();
            assertThat(report.metersOutOfCalibration()).isEmpty();
            assertThat(request.toBuilder().calibrations(List.of(lapsed)).build().calibrations())
                    .containsExactly(lapsed);
        }

        @Test
        void namesAMeterOutEvenOnADayNoSourceWentOut() {
            DailyReport report = Daily.buildDaily(on("2025-03-14")
                    .surveys(ALL_SURVEYS)
                    .calibrations(List.of(lapsed))
                    .build());
            assertThat(report.complete()).isTrue();
            assertThat(report.metersOutOfCalibration()).containsExactly("Ludlum 3");
        }
    }
}
