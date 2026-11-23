package io.github.rkaushikdev.kerfline.accept;

import static io.github.rkaushikdev.kerfline.accept.Indications.at;
import static io.github.rkaushikdev.kerfline.accept.Indications.ind;
import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.kerfline.indication.Indication;
import io.github.rkaushikdev.kerfline.indication.IndicationType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class Api1104Test {

    @Nested
    class TheCodeItself {

        @Test
        void namesItselfAndItsWindow() {
            assertThat(Api1104.codeName()).isEqualTo("API1104");
            assertThat(Api1104.windowLengthIn()).isEqualTo(12);
        }

        @Test
        void carriesAClauseForEveryTypeItRulesOn() {
            for (IndicationType type : List.of(
                    IndicationType.IP,
                    IndicationType.IF,
                    IndicationType.SLAG,
                    IndicationType.HOLLOW_BEAD,
                    IndicationType.BURN_THROUGH,
                    IndicationType.UNDERCUT)) {
                assertThat(Api1104.ruleFor(type).orElseThrow().clause()).matches("9\\.3.*");
            }
        }

        @Test
        void hasNoRuleForATypeItDoesNotCover() {
            assertThat(Api1104.ruleFor(IndicationType.TUNGSTEN)).isEmpty();
        }
    }

    @Nested
    class JudgeCrack {

        @Test
        void rejectsACrackOfAnySize() {
            assertThat(Api1104.judgeCrack(ind("CRACK", 0.05)).disposition()).isEqualTo(Disposition.REJECT);
            assertThat(Api1104.judgeCrack(ind("CRACK", 2)).disposition()).isEqualTo(Disposition.REJECT);
        }

        @Test
        void letsAShallowCraterCrackUnderFiveThirtySecondsThrough() {
            Indication crater = ind("CRACK", 0.1, input -> input.note("crater"));
            assertThat(Api1104.judgeCrack(crater).disposition()).isEqualTo(Disposition.ACCEPT);
        }

        @Test
        void rejectsACraterCrackThatIsTooLong() {
            Indication crater = ind("CRACK", 0.2, input -> input.note("crater"));
            assertThat(Api1104.judgeCrack(crater).disposition()).isEqualTo(Disposition.REJECT);
        }

        @Test
        void rejectsAnOrdinaryCrackOfTheSameLengthAsAToleratedCraterOne() {
            assertThat(Api1104.judgeCrack(ind("CRACK", 0.1)).disposition()).isEqualTo(Disposition.REJECT);
        }
    }

    @Nested
    class JudgePorosity {

        @Test
        void acceptsAPoreInsideBothCaps() {
            assertThat(Api1104.judgePorosity(ind("POROSITY", 0.05), 0.5).disposition())
                    .isEqualTo(Disposition.ACCEPT);
        }

        @Test
        void capsAPoreAtAnEighthOfAnInchOnAThickWall() {
            assertThat(Api1104.judgePorosity(ind("POROSITY", 0.13), 1).disposition())
                    .isEqualTo(Disposition.REPAIR);
            assertThat(Api1104.judgePorosity(ind("POROSITY", 0.125), 1).disposition())
                    .isEqualTo(Disposition.ACCEPT);
        }

        @Test
        void capsAPoreAtAQuarterOfTheWallOnAThinOne() {
            // A quarter of a 0.25 in wall is 0.0625 in, tighter than the 0.125 cap.
            assertThat(Api1104.judgePorosity(ind("POROSITY", 0.1), 0.25).disposition())
                    .isEqualTo(Disposition.REPAIR);
            assertThat(Api1104.judgePorosity(ind("POROSITY", 0.06), 0.25).disposition())
                    .isEqualTo(Disposition.ACCEPT);
        }

        @Test
        void flipsTheVerdictOnTheSamePoreWhenTheWallChanges() {
            Indication pore = ind("POROSITY", 0.1);
            assertThat(Api1104.judgePorosity(pore, 0.25).disposition()).isEqualTo(Disposition.REPAIR);
            assertThat(Api1104.judgePorosity(pore, 0.5).disposition()).isEqualTo(Disposition.ACCEPT);
        }

        @Test
        void refusesAWallAtZero() {
            assertThatThrownBy(() -> Api1104.judgePorosity(ind("POROSITY", 0.05), 0))
                    .hasMessageContaining("greater than 0");
        }
    }

    @Nested
    class JudgeClusterAndJudgeSlag {

        @Test
        void capsAClusterAtHalfAnInch() {
            assertThat(Api1104.judgeCluster(ind("CLUSTER", 0.4)).disposition()).isEqualTo(Disposition.ACCEPT);
            assertThat(Api1104.judgeCluster(ind("CLUSTER", 0.6)).disposition()).isEqualTo(Disposition.REPAIR);
        }

        @Test
        void capsSlagAtTwoInchesLong() {
            assertThat(Api1104.judgeSlag(ind("SLAG", 1.5)).disposition()).isEqualTo(Disposition.ACCEPT);
            assertThat(Api1104.judgeSlag(ind("SLAG", 2.5)).disposition()).isEqualTo(Disposition.REPAIR);
        }

        @Test
        void capsSlagSeparatelyOnWidth() {
            Indication wide = ind("SLAG", 0.5, input -> input.widthIn(0.1));
            ClauseVerdict verdict = Api1104.judgeSlag(wide);
            assertThat(verdict.disposition()).isEqualTo(Disposition.REPAIR);
            assertThat(verdict.reason().orElseThrow()).contains("wide");
        }

        @Test
        void takesTheWidthCapBeforeTheLengthOne() {
            Indication wideAndLong = ind("SLAG", 3, input -> input.widthIn(0.1));
            assertThat(Api1104.judgeSlag(wideAndLong).reason().orElseThrow()).contains("wide");
        }

        @Test
        void acceptsSlagInsideBothCaps() {
            assertThat(Api1104.judgeSlag(ind("SLAG", 1, input -> input.widthIn(0.05)))
                            .disposition())
                    .isEqualTo(Disposition.ACCEPT);
        }
    }

    @Nested
    class IsolatedSlag {

        private Indication isolated(double lengthIn, double at) {
            return ind("SLAG", lengthIn, input -> input.note("isolated").at(at));
        }

        @Test
        void tellsIsolatedSlagFromAnElongatedLine() {
            assertThat(Api1104.isIsolatedSlag(isolated(0.1, 0))).isTrue();
            assertThat(Api1104.isIsolatedSlag(ind("SLAG", 0.1))).isFalse();
            assertThat(Api1104.isIsolatedSlag(ind("POROSITY", 0.1, input -> input.note("isolated"))))
                    .isFalse();
        }

        @Test
        void allowsIsolatedSlagTwiceTheWidthOfALine() {
            Indication line = ind("SLAG", 0.3, input -> input.widthIn(0.1));
            Indication inclusion = ind("SLAG", 0.3, input -> input.widthIn(0.1).note("isolated"));
            assertThat(Api1104.judgeSlag(line).disposition()).isEqualTo(Disposition.REPAIR);
            assertThat(Api1104.judgeSlag(inclusion).disposition()).isEqualTo(Disposition.ACCEPT);
        }

        @Test
        void capsIsolatedSlagAtAnEighthWide() {
            Indication wide = ind("SLAG", 0.3, input -> input.widthIn(0.15).note("isolated"));
            ClauseVerdict verdict = Api1104.judgeSlag(wide);
            assertThat(verdict.disposition()).isEqualTo(Disposition.REPAIR);
            assertThat(verdict.reason()).hasValue("isolated slag 0.15 in wide, cap is 0.125 in wide");
        }

        @Test
        void leavesTheLengthOfOneIsolatedInclusionToTheFootOfWeldItSitsIn() {
            assertThat(Api1104.judgeSlag(isolated(0.4, 0)).disposition()).isEqualTo(Disposition.ACCEPT);
            assertThat(Api1104.judgeSlag(ind("SLAG", 2.5)).reason()).hasValue("slag 2.5 in long, cap is 2 in long");
        }

        @Test
        void capsIsolatedSlagAtHalfAnInchInAFootReadRoundTheTieIn() {
            List<ClauseVerdict> verdicts =
                    Api1104.judgeIsolatedSlag(List.of(isolated(0.3, 19), isolated(0.3, 1)), 20.8);
            assertThat(verdicts).singleElement().satisfies(verdict -> {
                assertThat(verdict.reason()).hasValue("isolated slag totals 0.6 in in 12 in, cap is 0.5 in");
                assertThat(verdict.measured()).hasValue(0.6);
            });
            assertThat(Api1104.judgeIsolatedSlag(List.of(isolated(0.3, 19), isolated(0.3, 1)), 60))
                    .isEmpty();
        }

        @Test
        void allowsFourIsolatedInclusionsInAFootButNotFive() {
            List<Indication> four = List.of(isolated(0.05, 1), isolated(0.05, 2), isolated(0.05, 3), isolated(0.05, 4));
            assertThat(Api1104.judgeIsolatedSlag(four, 20.8)).isEmpty();
            List<Indication> five = new ArrayList<>(four);
            five.add(isolated(0.05, 5));
            assertThat(Api1104.judgeIsolatedSlag(five, 20.8)).singleElement().satisfies(verdict -> assertThat(
                            verdict.reason())
                    .hasValue("5 isolated slag inclusions in 12 in, cap is 4"));
        }

        @Test
        void countsUnplacedInclusionsAcrossTheWholeWeld() {
            List<Indication> unplaced = List.of(
                    ind("SLAG", 0.3, input -> input.note("isolated")),
                    ind("SLAG", 0.3, input -> input.note("isolated")));
            assertThat(Api1104.judgeIsolatedSlag(unplaced, 20.8)).hasSize(1);
            assertThat(Api1104.judgeIsolatedSlag(List.of(ind("SLAG", 0.3)), 20.8))
                    .isEmpty();
        }

        @Test
        void keepsIsolatedSlagOutOfTheElongatedTotal() {
            List<Indication> set = List.of(at("SLAG", 1.8, 0), isolated(0.4, 1));
            assertThat(Api1104.judgeAggregate(set, IndicationType.SLAG, 20.8)).isEmpty();
        }

        @Test
        void failsAJointOnIsolatedSlagAlone() {
            List<Indication> five = List.of(
                    isolated(0.05, 1), isolated(0.05, 2), isolated(0.05, 3), isolated(0.05, 4), isolated(0.05, 5));
            JointVerdict result = Api1104.judgeJoint(five, 0.28, 20.8);
            assertThat(result.disposition()).isEqualTo(Disposition.REPAIR);
            assertThat(result.reasons()).containsExactly("5 isolated slag inclusions in 12 in, cap is 4");
        }
    }

    @Nested
    class JudgeByLength {

        @Test
        void capsIncompletePenetrationAtOneInch() {
            assertThat(Api1104.judgeByLength(ind("IP", 0.9)).disposition()).isEqualTo(Disposition.ACCEPT);
            assertThat(Api1104.judgeByLength(ind("IP", 1.1)).disposition()).isEqualTo(Disposition.REPAIR);
        }

        @Test
        void capsHollowBeadAtHalfAnInch() {
            assertThat(Api1104.judgeByLength(ind("HOLLOW_BEAD", 0.4)).disposition())
                    .isEqualTo(Disposition.ACCEPT);
            assertThat(Api1104.judgeByLength(ind("HOLLOW_BEAD", 0.6)).disposition())
                    .isEqualTo(Disposition.REPAIR);
        }

        @Test
        void capsBurnThroughAtAQuarterInch() {
            assertThat(Api1104.judgeByLength(ind("BURN_THROUGH", 0.3)).disposition())
                    .isEqualTo(Disposition.REPAIR);
        }

        @Test
        void acceptsATypeTheCodeHasNoRuleFor() {
            assertThat(Api1104.judgeByLength(ind("TUNGSTEN", 0.9)).disposition())
                    .isEqualTo(Disposition.ACCEPT);
        }
    }

    @Nested
    class HighLow {

        private Indication highLow(double lengthIn, double at) {
            return ind("IP", lengthIn, input -> input.note("high-low").at(at));
        }

        @Test
        void tellsHighLowFromAnUnfilledRoot() {
            assertThat(Api1104.isHighLow(highLow(0.5, 0))).isTrue();
            assertThat(Api1104.isHighLow(ind("IP", 0.5))).isFalse();
            assertThat(Api1104.isHighLow(ind("IF", 0.5, input -> input.note("high-low"))))
                    .isFalse();
        }

        @Test
        void allowsTwoInchesOfHighLowWhereAPlainRootGetsOne() {
            assertThat(Api1104.judgeByLength(ind("IP", 1.5)).disposition()).isEqualTo(Disposition.REPAIR);
            assertThat(Api1104.judgeByLength(highLow(1.5, 0)).disposition()).isEqualTo(Disposition.ACCEPT);
            ClauseVerdict over = Api1104.judgeByLength(highLow(2.5, 0));
            assertThat(over.disposition()).isEqualTo(Disposition.REPAIR);
            assertThat(over.reason()).hasValue("incomplete penetration due to high-low 2.5 in long, cap is 2 in long");
        }

        @Test
        void capsHighLowAtThreeInchesInAFoot() {
            List<Indication> inside = List.of(highLow(1.5, 0), highLow(1.2, 5));
            assertThat(Api1104.judgeJoint(inside, 0.28, 37.7).disposition()).isEqualTo(Disposition.ACCEPT);
            List<Indication> over = List.of(highLow(1.5, 0), highLow(1.2, 5), highLow(0.5, 8));
            JointVerdict result = Api1104.judgeJoint(over, 0.28, 37.7);
            assertThat(result.disposition()).isEqualTo(Disposition.REPAIR);
            assertThat(result.reasons())
                    .containsExactly("incomplete penetration due to high-low totals 3.2 in in 12 in, cap is 3 in");
        }

        @Test
        void keepsHighLowOutOfThePlainPenetrationTotal() {
            List<Indication> set = List.of(at("IP", 0.6, 0), highLow(0.6, 1));
            assertThat(Api1104.judgeAggregate(set, IndicationType.IP, 37.7)).isEmpty();
            assertThat(Api1104.judgeAggregate(List.of(at("IP", 0.6, 0), at("IP", 0.6, 1)), IndicationType.IP, 37.7))
                    .hasSize(1);
        }

        @Test
        void readsTheRuleFromTheNoteAsWellAsTheType() {
            assertThat(Api1104.ruleOf(highLow(1, 0)).orElseThrow().singleIn()).isEqualTo(2);
            assertThat(Api1104.ruleOf(ind("IP", 1)).orElseThrow().singleIn()).isEqualTo(1);
            assertThat(Api1104.ruleOf(ind("SLAG", 1, input -> input.note("isolated"))))
                    .isEmpty();
            assertThat(Api1104.ruleOf(ind("TUNGSTEN", 1))).isEmpty();
        }

        @Test
        void listsTheHighLowRuleNextToThePenetrationOne() {
            List<Api1104Rule> rules = Api1104.lengthRules();
            assertThat(rules).hasSize(7);
            assertThat(rules.get(0).label()).isEqualTo("incomplete penetration");
            assertThat(rules.get(1).label()).isEqualTo("incomplete penetration due to high-low");
            assertThat(rules.get(1).windowIn()).isEqualTo(3);
        }

        @Test
        void saysWhatThePoreWasMeasuredAcross() {
            assertThat(Api1104.judgePorosity(ind("POROSITY", 0.13), 1).reason())
                    .hasValue("pore 0.13 in across, cap is 0.125 in across");
            assertThat(Api1104.judgeCluster(ind("CLUSTER", 0.6)).reason())
                    .hasValue("cluster 0.6 in across, cap is 0.5 in across");
        }
    }

    @Nested
    class JudgeOne {

        @Test
        void routesEachTypeToItsOwnRule() {
            assertThat(Api1104.judgeOne(ind("CRACK", 0.05), 0.5).clause()).isEqualTo("9.3.9");
            assertThat(Api1104.judgeOne(ind("POROSITY", 0.05), 0.5).clause()).isEqualTo("9.3.3");
            assertThat(Api1104.judgeOne(ind("SLAG", 0.5), 0.5).clause()).isEqualTo("9.3.6");
            assertThat(Api1104.judgeOne(ind("IP", 0.5), 0.5).clause()).isEqualTo("9.3.2");
        }
    }

    @Nested
    class JudgeAggregate {

        @Test
        void findsNothingWhenThereIsNothingOfThatType() {
            assertThat(Api1104.judgeAggregate(List.of(ind("SLAG", 0.5)), IndicationType.IP, 20))
                    .isEmpty();
        }

        @Test
        void catchesATotalOverTheWindowCapEvenWhenEachOnePasses() {
            List<Indication> set = List.of(at("IP", 0.4, 0), at("IP", 0.4, 1), at("IP", 0.4, 2));
            List<ClauseVerdict> verdicts = Api1104.judgeAggregate(set, IndicationType.IP, 40);
            assertThat(verdicts).isNotEmpty();
            assertThat(verdicts.get(0).reason().orElseThrow()).contains("totals");
        }

        @Test
        void acceptsTheSameTotalSpreadPastTheWindow() {
            List<Indication> spread = List.of(at("IP", 0.4, 0), at("IP", 0.4, 20), at("IP", 0.4, 40));
            List<ClauseVerdict> verdicts = Api1104.judgeAggregate(spread, IndicationType.IP, 80);
            assertThat(verdicts.stream()
                            .filter(v -> v.reason().orElseThrow().contains("in 12 in"))
                            .toList())
                    .isEmpty();
        }

        @Test
        void catchesThePercentageCapOnAShortWeld() {
            // 0.9 in of incomplete penetration on a 7.5 in weld is 12 percent.
            List<Indication> set = List.of(at("IP", 0.9, 0));
            List<ClauseVerdict> verdicts = Api1104.judgeAggregate(set, IndicationType.IP, 7.5);
            assertThat(verdicts).anyMatch(v -> v.reason().orElseThrow().contains("percent"));
        }

        @Test
        void letsTheSameTotalThroughOnALongWeld() {
            List<Indication> set = List.of(at("IP", 0.9, 0));
            List<ClauseVerdict> verdicts = Api1104.judgeAggregate(set, IndicationType.IP, 60);
            assertThat(verdicts).noneMatch(v -> v.reason().orElseThrow().contains("percent"));
        }

        @Test
        void readsTheTwelveInchesRoundPastTheTieIn() {
            // On a 20.8 in girth weld these are 1.6 in of IP inside one foot of weld.
            List<Indication> set =
                    List.of(at("IP", 0.4, 19.5), at("IP", 0.4, 20.3), at("IP", 0.4, 0.2), at("IP", 0.4, 0.9));
            List<ClauseVerdict> verdicts = Api1104.judgeAggregate(set, IndicationType.IP, 20.8);
            assertThat(verdicts).anyMatch(v -> v.reason().orElseThrow().contains("totals 1.6 in in 12 in"));
        }

        @Test
        void keepsTheSameRunsApartOnAWeldTooLongToMeetRoundTheBack() {
            List<Indication> set =
                    List.of(at("IP", 0.4, 19.5), at("IP", 0.4, 20.3), at("IP", 0.4, 0.2), at("IP", 0.4, 0.9));
            List<ClauseVerdict> verdicts = Api1104.judgeAggregate(set, IndicationType.IP, 60);
            assertThat(verdicts).noneMatch(v -> v.reason().orElseThrow().contains("in 12 in"));
        }

        @Test
        void saysTheEightPercentCapIsTheShortWeldOne() {
            List<Indication> set = List.of(at("IP", 0.9, 0));
            ClauseVerdict verdict =
                    Api1104.judgeAggregate(set, IndicationType.IP, 7.5).get(0);
            assertThat(verdict.reason().orElseThrow())
                    .isEqualTo("incomplete penetration totals 0.9 in, over 8 percent of a weld under 12 in");
            assertThat(verdict.measured()).hasValue(0.9);
        }

        @Test
        void leavesTheEightPercentCapOffAWeldOfTwelveInchesOrMore() {
            // Three inches of IP is over 8 percent of a 36 in weld, but no foot of it holds more than one.
            List<Indication> set = List.of(at("IP", 1, 0), at("IP", 1, 12), at("IP", 1, 24));
            assertThat(Api1104.judgeAggregate(set, IndicationType.IP, 36)).isEmpty();
            assertThat(Api1104.judgeAggregate(set, IndicationType.IF, 36)).isEmpty();
        }

        @Test
        void capsUndercutAtASixthOfTheWeldRatherThanEightPercent() {
            Api1104Rule rule = Api1104.ruleFor(IndicationType.UNDERCUT).orElseThrow();
            assertThat(rule.percent()).isEmpty();
            assertThat(rule.wholeWeldFraction().orElseThrow()).isCloseTo(1.0 / 6, digits(6));
            // A sixth of 7.5 in is 1.25 in, where eight percent would have been 0.6 in.
            assertThat(Api1104.judgeAggregate(List.of(at("UNDERCUT", 1, 0)), IndicationType.UNDERCUT, 7.5))
                    .isEmpty();
            List<ClauseVerdict> over =
                    Api1104.judgeAggregate(List.of(at("UNDERCUT", 1.5, 0)), IndicationType.UNDERCUT, 7.5);
            assertThat(over).singleElement().satisfies(verdict -> assertThat(
                            verdict.reason().orElseThrow())
                    .isEqualTo("undercut totals 1.5 in, over 1.25 in allowed in a 7.5 in weld"));
        }

        @Test
        void holdsTheSixthOnALongWeldToo() {
            // 3.6 in of undercut is over a sixth of a 20.8 in weld, which is 3.4667 in.
            List<Indication> set = List.of(at("UNDERCUT", 1.8, 0), at("UNDERCUT", 1.8, 10.4));
            List<ClauseVerdict> verdicts = Api1104.judgeAggregate(set, IndicationType.UNDERCUT, 20.8);
            assertThat(verdicts).anyMatch(v -> v.reason().orElseThrow().contains("over 3.4667 in allowed"));
        }

        @Test
        void hasNoPercentageCapOnSlag() {
            assertThat(Api1104.ruleFor(IndicationType.SLAG).orElseThrow().percent())
                    .isEmpty();
            List<Indication> set = List.of(at("SLAG", 1.5, 0));
            List<ClauseVerdict> verdicts = Api1104.judgeAggregate(set, IndicationType.SLAG, 7.5);
            assertThat(verdicts).noneMatch(v -> v.reason().orElseThrow().contains("percent"));
        }
    }

    @Nested
    class JudgeJoint {

        private static final double WALL = 0.28;
        private static final double WELD = 20.8;

        @Test
        void acceptsACleanJoint() {
            JointVerdict result = Api1104.judgeJoint(List.of(), WALL, WELD);
            assertThat(result.disposition()).isEqualTo(Disposition.ACCEPT);
            assertThat(result.findings()).isEmpty();
        }

        @Test
        void acceptsAJointWhoseIndicationsAreAllInsideTheCaps() {
            List<Indication> indications = List.of(at("POROSITY", 0.05, 2), at("SLAG", 0.5, 8));
            assertThat(Api1104.judgeJoint(indications, WALL, WELD).disposition())
                    .isEqualTo(Disposition.ACCEPT);
        }

        @Test
        void takesAJointOutForACrackWhateverElseIsOnIt() {
            List<Indication> indications = List.of(at("POROSITY", 0.05, 2), at("CRACK", 0.1, 8));
            JointVerdict result = Api1104.judgeJoint(indications, WALL, WELD);
            assertThat(result.disposition()).isEqualTo(Disposition.REJECT);
            assertThat(result.clause()).contains("9.3.9");
        }

        @Test
        void carriesEveryFindingAndNotOnlyTheGoverningOne() {
            List<Indication> indications = List.of(at("SLAG", 3, 2), at("CRACK", 0.1, 8));
            JointVerdict result = Api1104.judgeJoint(indications, WALL, WELD);
            assertThat(result.findings().size()).isGreaterThanOrEqualTo(2);
        }

        @Test
        void failsAJointOnTheAggregateAlone() {
            List<Indication> indications = List.of(at("IP", 0.4, 0), at("IP", 0.4, 1), at("IP", 0.4, 2));
            JointVerdict result = Api1104.judgeJoint(indications, WALL, WELD);
            assertThat(result.disposition()).isEqualTo(Disposition.REPAIR);
            assertThat(result.reasons()).anyMatch(reason -> reason.contains("totals"));
        }

        @Test
        void refusesAJointWithNoWallOrWeldLength() {
            assertThatThrownBy(() -> Api1104.judgeJoint(List.of(), 0, 20)).hasMessageContaining("wall thickness");
            assertThatThrownBy(() -> Api1104.judgeJoint(List.of(), 0.28, 0)).hasMessageContaining("weld length");
        }
    }
}
