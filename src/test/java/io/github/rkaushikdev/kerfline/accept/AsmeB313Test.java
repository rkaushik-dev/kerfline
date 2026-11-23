package io.github.rkaushikdev.kerfline.accept;

import static io.github.rkaushikdev.kerfline.accept.Indications.at;
import static io.github.rkaushikdev.kerfline.accept.Indications.ind;
import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.kerfline.indication.Indication;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AsmeB313Test {

    @Nested
    class TheServices {

        @Test
        void namesTheThreeColumnsOfTheTable() {
            assertThat(AsmeB313.services()).containsExactly("normal", "severe_cyclic", "category_d");
            assertThat(AsmeB313.codeName()).isEqualTo("B31.3");
        }

        @Test
        void refusesAServiceTheTableHasNoColumnFor() {
            assertThatThrownBy(() -> AsmeB313.rulesFor("lethal")).hasMessageContaining("fluid service must be one of");
        }
    }

    @Nested
    class WindowLengthIn {

        @Test
        void isTwelveTimesTheThinnerComponentAndNotTwelveInches() {
            assertThat(AsmeB313.windowLengthIn(0.25)).isEqualTo(3);
            assertThat(AsmeB313.windowLengthIn(1)).isEqualTo(12);
        }

        @Test
        void growsWithTheThickness() {
            assertThat(AsmeB313.windowLengthIn(0.5)).isLessThan(AsmeB313.windowLengthIn(0.75));
        }

        @Test
        void refusesAThicknessAtZero() {
            assertThatThrownBy(() -> AsmeB313.windowLengthIn(0)).hasMessageContaining("greater than 0");
        }
    }

    @Nested
    class InclusionLimitsFor {

        @Test
        void capsTheLengthAtAThirdOfTheThickness() {
            assertThat(AsmeB313.inclusionLimits(0.75).lengthIn()).isCloseTo(0.25, digits(4));
        }

        @Test
        void capsTheWidthAtTheLesserOfTheFixedLimitAndAThird() {
            assertThat(AsmeB313.inclusionLimits(0.75).widthIn()).isCloseTo(0.098, digits(4));
            assertThat(AsmeB313.inclusionLimits(0.15).widthIn()).isCloseTo(0.05, digits(4));
        }

        @Test
        void capsTheCumulativeLengthAtTheThicknessItself() {
            assertThat(AsmeB313.inclusionLimits(0.5).cumulativeIn()).isEqualTo(0.5);
        }

        @Test
        void scalesEveryLimitTogether() {
            InclusionLimits thin = AsmeB313.inclusionLimits(0.25);
            InclusionLimits thick = AsmeB313.inclusionLimits(1);
            assertThat(thick.lengthIn()).isGreaterThan(thin.lengthIn());
            assertThat(thick.cumulativeIn()).isGreaterThan(thin.cumulativeIn());
            assertThat(thick.windowIn()).isGreaterThan(thin.windowIn());
        }
    }

    @Nested
    class UndercutLimitIn {

        @Test
        void isTighterInSevereCyclicService() {
            assertThat(AsmeB313.undercutLimitIn("severe_cyclic", 1)).isLessThan(AsmeB313.undercutLimitIn("normal", 1));
        }

        @Test
        void fallsBackToAQuarterOfTheWallOnThinWork() {
            assertThat(AsmeB313.undercutLimitIn("normal", 0.1)).isCloseTo(0.025, digits(4));
        }

        @Test
        void isTheFixedLimitOnThickWork() {
            assertThat(AsmeB313.undercutLimitIn("normal", 2)).isCloseTo(0.0625, digits(4));
        }
    }

    @Nested
    class JudgeOne {

        @Test
        void rejectsACrackInAnyService() {
            for (String service : AsmeB313.services()) {
                assertThat(AsmeB313.judgeOne(ind("CRACK", 0.02), service, 0.5).disposition())
                        .isEqualTo(Disposition.REJECT);
            }
        }

        @Test
        void rejectsIncompleteFusionInAnyService() {
            for (String service : AsmeB313.services()) {
                assertThat(AsmeB313.judgeOne(ind("IF", 0.02), service, 0.5).disposition())
                        .isEqualTo(Disposition.REJECT);
            }
        }

        @Test
        void rejectsIncompletePenetrationInNormalService() {
            assertThat(AsmeB313.judgeOne(ind("IP", 0.02), "normal", 0.5).disposition())
                    .isEqualTo(Disposition.REJECT);
        }

        @Test
        void permitsIncompletePenetrationInCategoryDService() {
            ClauseVerdict verdict = AsmeB313.judgeOne(ind("IP", 0.02), "category_d", 0.5);
            assertThat(verdict.disposition()).isEqualTo(Disposition.ACCEPT);
        }

        @Test
        void capsAnInclusionOnLength() {
            assertThat(AsmeB313.judgeOne(ind("SLAG", 0.1), "normal", 0.5).disposition())
                    .isEqualTo(Disposition.ACCEPT);
            assertThat(AsmeB313.judgeOne(ind("SLAG", 0.2), "normal", 0.5).disposition())
                    .isEqualTo(Disposition.REPAIR);
        }

        @Test
        void capsAnInclusionOnWidthSeparately() {
            Indication wide = ind("SLAG", 0.1, input -> input.widthIn(0.15));
            assertThat(AsmeB313.judgeOne(wide, "normal", 0.5).reason().orElseThrow())
                    .contains("wide");
        }

        @Test
        void movesTheInclusionVerdictWithTheThickness() {
            Indication slag = ind("SLAG", 0.2);
            assertThat(AsmeB313.judgeOne(slag, "normal", 0.5).disposition()).isEqualTo(Disposition.REPAIR);
            assertThat(AsmeB313.judgeOne(slag, "normal", 1).disposition()).isEqualTo(Disposition.ACCEPT);
        }

        @Test
        void movesTheUndercutVerdictWithTheService() {
            Indication undercut = ind("UNDERCUT", 0.05);
            assertThat(AsmeB313.judgeOne(undercut, "normal", 1).disposition()).isEqualTo(Disposition.ACCEPT);
            assertThat(AsmeB313.judgeOne(undercut, "severe_cyclic", 1).disposition())
                    .isEqualTo(Disposition.REPAIR);
        }

        @Test
        void capsAPoreAgainstTheWall() {
            assertThat(AsmeB313.judgeOne(ind("POROSITY", 0.1), "normal", 0.5).disposition())
                    .isEqualTo(Disposition.ACCEPT);
            assertThat(AsmeB313.judgeOne(ind("POROSITY", 0.2), "normal", 0.5).disposition())
                    .isEqualTo(Disposition.REPAIR);
        }

        @Test
        void acceptsATypeTheTableHasNoRowFor() {
            assertThat(AsmeB313.judgeOne(ind("ROOT_CONCAVITY", 0.5), "normal", 0.5)
                            .disposition())
                    .isEqualTo(Disposition.ACCEPT);
        }
    }

    @Nested
    class JudgeCumulative {

        private final List<Indication> run =
                List.of(at("SLAG", 0.15, 0), at("SLAG", 0.15, 1), at("SLAG", 0.15, 2), at("SLAG", 0.15, 3));

        @Test
        void findsNothingWhenThereAreNoInclusions() {
            assertThat(AsmeB313.judgeCumulative(List.of(at("POROSITY", 0.05, 0)), 0.5))
                    .isEmpty();
        }

        @Test
        void catchesInclusionsTotallingOverTheThicknessInsideTheWindow() {
            assertThat(AsmeB313.judgeCumulative(run, 0.5)).isNotEmpty();
        }

        @Test
        void letsTheSameTotalThroughOnceTheWindowIsWiderThanTheRun() {
            // On a 1 in wall the cumulative cap is 1 in and 0.6 in is under it.
            assertThat(AsmeB313.judgeCumulative(run, 1)).isEmpty();
        }
    }

    @Nested
    class JudgeJoint {

        @Test
        void acceptsACleanJoint() {
            assertThat(AsmeB313.judgeJoint(List.of(), 0.5, "normal").disposition())
                    .isEqualTo(Disposition.ACCEPT);
        }

        @Test
        void defaultsToNormalService() {
            JointVerdict result = AsmeB313.judgeJoint(List.of(at("IP", 0.02, 1)), 0.5);
            assertThat(result.disposition()).isEqualTo(Disposition.REJECT);
        }

        @Test
        void givesTheSameJointDifferentAnswersInDifferentServices() {
            List<Indication> indications = List.of(at("IP", 0.02, 1));
            JointVerdict normal = AsmeB313.judgeJoint(indications, 0.5, "normal");
            JointVerdict categoryD = AsmeB313.judgeJoint(indications, 0.5, "category_d");
            assertThat(normal.disposition()).isEqualTo(Disposition.REJECT);
            assertThat(categoryD.disposition()).isEqualTo(Disposition.ACCEPT);
        }

        @Test
        void refusesAServiceItDoesNotCarry() {
            assertThatThrownBy(() -> AsmeB313.judgeJoint(List.of(), 0.5, "lethal"))
                    .hasMessageContaining("fluid service");
        }
    }

    @Nested
    class CategoryDPenetration {

        @Test
        void allowsAnInchAndAHalfOnlyInCategoryD() {
            assertThat(AsmeB313.rulesFor("category_d").penetrationIn()).hasValue(1.5);
            assertThat(AsmeB313.rulesFor("category_d").penetrationPermitted()).isTrue();
            assertThat(AsmeB313.rulesFor("normal").penetrationIn()).isEmpty();
            assertThat(AsmeB313.rulesFor("severe_cyclic").penetrationPermitted())
                    .isFalse();
        }

        @Test
        void capsASingleRunOfPenetrationAtTheAllowance() {
            ClauseVerdict verdict = AsmeB313.judgeOne(ind("IP", 2), "category_d", 0.5);
            assertThat(verdict.disposition()).isEqualTo(Disposition.REPAIR);
            assertThat(verdict.reason()).hasValue("incomplete penetration 2 in long, cap is 1.5 in long");
            assertThat(AsmeB313.judgeOne(ind("IP", 1.5), "category_d", 0.5).disposition())
                    .isEqualTo(Disposition.ACCEPT);
        }

        @Test
        void totalsThePenetrationInAnySixInches() {
            List<ClauseVerdict> verdicts =
                    AsmeB313.judgePenetration(List.of(at("IP", 0.8, 0), at("IP", 0.8, 4)), "category_d");
            assertThat(verdicts).singleElement().satisfies(verdict -> {
                assertThat(verdict.reason())
                        .hasValue("incomplete penetration totals 1.6 in in 6 in of weld, cap is 1.5 in");
                assertThat(verdict.measured()).hasValue(1.6);
            });
            assertThat(AsmeB313.judgePenetration(List.of(at("IP", 0.8, 0), at("IP", 0.8, 7)), "category_d"))
                    .isEmpty();
        }

        @Test
        void totalsUnplacedPenetrationAcrossTheWeld() {
            assertThat(AsmeB313.judgePenetration(List.of(ind("IP", 0.8), ind("IP", 0.8)), "category_d"))
                    .hasSize(1);
        }

        @Test
        void hasNothingToTotalWhereNoneIsAllowed() {
            assertThat(AsmeB313.judgePenetration(List.of(at("IP", 0.8, 0), at("IP", 0.8, 4)), "normal"))
                    .isEmpty();
            assertThat(AsmeB313.judgePenetration(List.of(at("SLAG", 0.8, 0)), "category_d"))
                    .isEmpty();
        }

        @Test
        void sendsACategoryDJointForRepairOnTheTotal() {
            JointVerdict result = AsmeB313.judgeJoint(List.of(at("IP", 0.8, 0), at("IP", 0.8, 4)), 0.5, "category_d");
            assertThat(result.disposition()).isEqualTo(Disposition.REPAIR);
            assertThat(result.reasons()).singleElement().asString().contains("in 6 in of weld");
        }

        @Test
        void saysWhichWayAnInclusionWasTooBig() {
            assertThat(AsmeB313.judgeOne(ind("SLAG", 0.1, input -> input.widthIn(0.12)), "normal", 0.5)
                            .reason())
                    .hasValue("inclusion 0.12 in wide, cap is 0.098 in wide");
            assertThat(AsmeB313.judgeOne(ind("SLAG", 0.2), "normal", 0.5).reason())
                    .hasValue("inclusion 0.2 in long, cap is 0.1667 in long");
        }
    }
}
