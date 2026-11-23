package io.github.rkaushikdev.kerfline.safety;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DosimetryTest {

    private static BadgeRecord badge(String from, String to, double mrem) {
        return Dosimetry.makeBadgeRecord("R. Ibarra", from, to, mrem);
    }

    private static BadgeRecord badge(String from, String to, Double mrem, boolean returned) {
        return Dosimetry.makeBadgeRecord("R. Ibarra", from, to, mrem, returned);
    }

    @Nested
    class TheLimits {

        @Test
        void setsTheAnnualLimitAtFiveThousandMillirem() {
            assertThat(Dosimetry.annualLimitMrem()).isEqualTo(5000);
        }

        @Test
        void putsTheInvestigationLevelsWellBelowIt() {
            InvestigationLevels levels = Dosimetry.investigationLevels();
            assertThat(levels.levelOneMrem()).isEqualTo(500);
            assertThat(levels.levelTwoMrem()).isEqualTo(1500);
            assertThat(levels.levelTwoMrem()).isLessThan(Dosimetry.annualLimitMrem());
        }
    }

    @Nested
    class MakeBadgeRecord {

        @Test
        void buildsAReturnedBadge() {
            BadgeRecord record = badge("2025-01-01", "2025-03-31", 42);
            assertThat(record.mrem()).hasValue(42);
            assertThat(record.returned()).isTrue();
        }

        @Test
        void leavesAnUnreturnedBadgeWithNoReadingAtAll() {
            BadgeRecord record = badge("2025-01-01", "2025-03-31", null, false);
            assertThat(record.mrem()).isEmpty();
        }

        @Test
        void refusesAReadingOnABadgeThatHasNotComeBack() {
            assertThatThrownBy(() -> badge("2025-01-01", "2025-03-31", 42.0, false))
                    .hasMessageContaining("cannot carry a reading");
        }

        @Test
        void refusesAPeriodThatEndsBeforeItStarts() {
            assertThatThrownBy(() -> badge("2025-03-31", "2025-01-01", 42))
                    .hasMessageContaining("cannot end before it starts");
        }

        @Test
        void refusesANegativeDose() {
            assertThatThrownBy(() -> badge("2025-01-01", "2025-03-31", -1)).hasMessageContaining("not be below 0");
        }

        @Test
        void refusesARecordWithNobodyOnIt() {
            assertThatThrownBy(() -> Dosimetry.makeBadgeRecord("  ", "2025-01-01", "2025-03-31", 1))
                    .hasMessageContaining("names the person");
        }
    }

    @Nested
    class DoseInPeriod {

        private final List<BadgeRecord> records = List.of(
                badge("2025-01-01", "2025-03-31", 120),
                badge("2025-04-01", "2025-06-30", 210),
                badge("2025-07-01", "2025-09-30", 90));

        @Test
        void addsTheBadgesInsideTheWindow() {
            DosePeriod period = Dosimetry.doseInPeriod(records, "2025-01-01", "2025-06-30");
            assertThat(period.mrem()).isCloseTo(330, digits(1));
            assertThat(period.records()).isEqualTo(2);
        }

        @Test
        void leavesOutBadgesOutsideTheWindow() {
            DosePeriod period = Dosimetry.doseInPeriod(records, "2025-07-01", "2025-09-30");
            assertThat(period.mrem()).isCloseTo(90, digits(1));
        }

        @Test
        void isCompleteWhenEveryBadgeCameBack() {
            assertThat(Dosimetry.doseInPeriod(records, "2025-01-01", "2025-12-31")
                            .complete())
                    .isTrue();
        }

        @Test
        void doesNotTreatAMissingBadgeAsAZero() {
            List<BadgeRecord> withMissing = new ArrayList<>(records);
            withMissing.add(badge("2025-10-01", "2025-12-31", null, false));
            DosePeriod period = Dosimetry.doseInPeriod(withMissing, "2025-01-01", "2025-12-31");
            assertThat(period.complete()).isFalse();
            assertThat(period.outstandingBadges()).isEqualTo(1);
        }

        @Test
        void isZeroOverAWindowWithNothingInIt() {
            DosePeriod period = Dosimetry.doseInPeriod(records, "2024-01-01", "2024-06-30");
            assertThat(period.mrem()).isEqualTo(0);
            assertThat(period.records()).isZero();
        }
    }

    @Nested
    class ABadgeLongerThanTheWindow {

        @Test
        void countsABadgeThatRunsRightAcrossTheWindow() {
            List<BadgeRecord> annual = List.of(badge("2025-01-01", "2025-12-31", 400));
            DosePeriod quarter = Dosimetry.doseInPeriod(annual, "2025-04-01", "2025-06-30");
            assertThat(quarter.records()).isEqualTo(1);
            assertThat(quarter.mrem()).isEqualTo(400);
        }

        @Test
        void stillLeavesOutABadgeThatEndsTheDayBeforeTheWindow() {
            List<BadgeRecord> early = List.of(badge("2025-01-01", "2025-03-31", 400));
            assertThat(Dosimetry.doseInPeriod(early, "2025-04-01", "2025-06-30").records())
                    .isZero();
            assertThat(Dosimetry.doseInPeriod(early, "2025-03-31", "2025-06-30").records())
                    .isEqualTo(1);
        }
    }

    @Nested
    class DeclaredPregnancy {

        private final List<BadgeRecord> records = List.of(
                badge("2025-01-01", "2025-01-31", 120),
                badge("2025-02-01", "2025-02-28", 100),
                badge("2025-03-01", "2025-03-31", 60),
                badge("2025-04-01", "2025-04-30", 80));

        @Test
        void holdsTheWholePregnancyToFiveHundredMillirem() {
            assertThat(Dosimetry.gestationLimitMrem()).isEqualTo(500);
            GestationStanding standing = Dosimetry.judgeGestation(records, "2025-02-01", "2025-03-01", "2025-04-30");
            assertThat(standing.beforeDeclaration().mrem()).isEqualTo(100);
            assertThat(standing.sinceDeclaration().mrem()).isEqualTo(140);
            assertThat(standing.allowedSinceMrem()).isEqualTo(400);
            assertThat(standing.remainingMrem()).isEqualTo(260);
            assertThat(standing.standing()).isEqualTo(DoseStanding.WITHIN_LIMITS);
        }

        @Test
        void allowsOnlyFiftyMoreOnceTheDoseWasNearTheLimitAtDeclaration() {
            List<BadgeRecord> heavy =
                    List.of(badge("2025-01-01", "2025-02-28", 470), badge("2025-03-01", "2025-03-31", 60));
            GestationStanding standing = Dosimetry.judgeGestation(heavy, "2025-01-01", "2025-03-01", "2025-03-31");
            assertThat(standing.allowedSinceMrem()).isEqualTo(50);
            assertThat(standing.remainingMrem()).isZero();
            assertThat(standing.standing()).isEqualTo(DoseStanding.OVER_THE_GESTATION_LIMIT);
            assertThat(standing.standing().label()).isEqualTo("over the gestation limit");
        }

        @Test
        void allowsFiftyMoreEvenPastTheLimitWhenItWasPassedBeforeAnybodyKnew() {
            List<BadgeRecord> over =
                    List.of(badge("2025-01-01", "2025-02-28", 620), badge("2025-03-01", "2025-03-31", 40));
            GestationStanding standing = Dosimetry.judgeGestation(over, "2025-01-01", "2025-03-01", "2025-03-31");
            assertThat(standing.standing()).isEqualTo(DoseStanding.WITHIN_LIMITS);
            assertThat(standing.remainingMrem()).isEqualTo(10);
        }

        @Test
        void neverCallsItWithinLimitsWithABadgeStillOut() {
            List<BadgeRecord> out = new ArrayList<>(records);
            out.add(badge("2025-05-01", "2025-05-31", null, false));
            assertThat(Dosimetry.judgeGestation(out, "2025-02-01", "2025-03-01", "2025-05-31")
                            .standing())
                    .isEqualTo(DoseStanding.INCOMPLETE_RECORD);
        }

        @Test
        void refusesADeclarationBeforeConceptionOrAJudgementBeforeTheDeclaration() {
            assertThatThrownBy(() -> Dosimetry.judgeGestation(records, "2025-03-01", "2025-02-01", "2025-04-30"))
                    .hasMessageContaining("declared after conception");
            assertThatThrownBy(() -> Dosimetry.judgeGestation(records, "2025-02-01", "2025-03-01", "2025-02-15"))
                    .hasMessageContaining("judged after the declaration");
        }
    }

    @Nested
    class AnnualDose {

        @Test
        void looksBackTwelveMonths() {
            List<BadgeRecord> records =
                    List.of(badge("2024-01-01", "2024-03-31", 500), badge("2025-01-01", "2025-03-31", 120));
            DosePeriod period = Dosimetry.annualDose(records, "2025-06-30");
            assertThat(period.mrem()).isCloseTo(120, digits(1));
        }
    }

    @Nested
    class JudgeAnnual {

        @Test
        void callsAQuietYearWithinLimits() {
            List<BadgeRecord> records = List.of(badge("2025-01-01", "2025-03-31", 120));
            AnnualStanding verdict = Dosimetry.judgeAnnual(records, "2025-06-30");
            assertThat(verdict.standing()).isEqualTo(DoseStanding.WITHIN_LIMITS);
            assertThat(verdict.standing().label()).isEqualTo("within limits");
            assertThat(verdict.remainingMrem()).isCloseTo(4880, digits(1));
        }

        @Test
        void raisesALevelOneInvestigationPastATenthOfTheLimit() {
            List<BadgeRecord> records = List.of(badge("2025-01-01", "2025-03-31", 600));
            assertThat(Dosimetry.judgeAnnual(records, "2025-06-30").standing())
                    .isEqualTo(DoseStanding.LEVEL_ONE_INVESTIGATION);
        }

        @Test
        void raisesALevelTwoInvestigationPastThreeTenths() {
            List<BadgeRecord> records = List.of(badge("2025-01-01", "2025-03-31", 1600));
            assertThat(Dosimetry.judgeAnnual(records, "2025-06-30").standing())
                    .isEqualTo(DoseStanding.LEVEL_TWO_INVESTIGATION);
        }

        @Test
        void reportsGoingOverTheAnnualLimit() {
            List<BadgeRecord> records = List.of(badge("2025-01-01", "2025-03-31", 5200));
            AnnualStanding verdict = Dosimetry.judgeAnnual(records, "2025-06-30");
            assertThat(verdict.standing()).isEqualTo(DoseStanding.OVER_THE_ANNUAL_LIMIT);
            assertThat(verdict.remainingMrem()).isEqualTo(0);
        }

        @Test
        void neverReportsWithinLimitsOnAnIncompleteRecord() {
            List<BadgeRecord> records =
                    List.of(badge("2025-01-01", "2025-03-31", 120), badge("2025-04-01", "2025-06-30", null, false));
            AnnualStanding verdict = Dosimetry.judgeAnnual(records, "2025-06-30");
            assertThat(verdict.standing()).isEqualTo(DoseStanding.INCOMPLETE_RECORD);
            assertThat(verdict.standing().label()).isEqualTo("incomplete record");
        }

        @Test
        void reportsTheFractionOfTheLimitUsed() {
            List<BadgeRecord> records = List.of(badge("2025-01-01", "2025-03-31", 500));
            assertThat(Dosimetry.judgeAnnual(records, "2025-06-30").fractionUsed())
                    .isCloseTo(0.1, digits(4));
        }
    }

    @Nested
    class ComparePocket {

        @Test
        void agreesWhenTheTwoReadingsAreClose() {
            assertThat(Dosimetry.comparePocket(100, 110).agrees()).isTrue();
        }

        @Test
        void disagreesWhenTheyAreFarApart() {
            PocketComparison check = Dosimetry.comparePocket(100, 400);
            assertThat(check.agrees()).isFalse();
            assertThat(check.differenceMrem()).isEqualTo(300);
        }

        @Test
        void keepsTheBadgeAsTheRecordEitherWay() {
            PocketComparison check = Dosimetry.comparePocket(100, 400);
            assertThat(check.ofRecord()).isEqualTo(100);
            assertThat(check.note()).contains("badge is the record");
        }

        @Test
        void agreesWhenBothReadNothingRatherThanDividingByZero() {
            PocketComparison check = Dosimetry.comparePocket(0, 0);
            assertThat(check.agrees()).isTrue();
            assertThat(check.differenceMrem()).isEqualTo(0);
        }

        @Test
        void takesATighterTolerance() {
            assertThat(Dosimetry.comparePocket(100, 120, 0.1).agrees()).isFalse();
        }

        @Test
        void refusesANegativeReading() {
            assertThatThrownBy(() -> Dosimetry.comparePocket(-1, 0)).hasMessageContaining("not be below 0");
        }
    }
}
