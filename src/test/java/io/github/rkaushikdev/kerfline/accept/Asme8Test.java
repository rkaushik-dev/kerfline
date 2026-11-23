package io.github.rkaushikdev.kerfline.accept;

import static io.github.rkaushikdev.kerfline.accept.Indications.at;
import static io.github.rkaushikdev.kerfline.accept.Indications.ind;
import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.kerfline.indication.Indication;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class Asme8Test {

    @Nested
    class ElongatedLimitIn {

        @Test
        void isAQuarterInchOnThinWork() {
            assertThat(Asme8.elongatedLimitIn(0.25)).isEqualTo(0.25);
            assertThat(Asme8.elongatedLimitIn(0.75)).isEqualTo(0.25);
        }

        @Test
        void climbsWithTheThicknessThroughTheMiddleBand() {
            assertThat(Asme8.elongatedLimitIn(1.5)).isCloseTo(0.5, digits(4));
            assertThat(Asme8.elongatedLimitIn(2.25)).isCloseTo(0.75, digits(4));
        }

        @Test
        void stopsClimbingAtThreeQuartersOfAnInch() {
            assertThat(Asme8.elongatedLimitIn(3)).isEqualTo(0.75);
            assertThat(Asme8.elongatedLimitIn(10)).isEqualTo(0.75);
        }

        @Test
        void isContinuousAcrossBothBandEdges() {
            assertThat(Asme8.elongatedLimitIn(0.7501)).isCloseTo(0.25, digits(3));
            assertThat(Asme8.elongatedLimitIn(2.2501)).isCloseTo(0.75, digits(3));
        }

        @Test
        void neverGoesDownAsThePlateGetsThicker() {
            double previous = 0;
            for (double t = 0.1; t <= 4; t += 0.05) {
                double limit = Asme8.elongatedLimitIn(Num.parse(Num.toFixed(t, 2)));
                assertThat(limit).isGreaterThanOrEqualTo(previous - 1e-9);
                previous = limit;
            }
        }

        @Test
        void namesItsThreeBands() {
            assertThat(Asme8.limitBands().stream().map(LengthBand::rule))
                    .containsExactly("quarter", "third", "threequarter");
        }

        @Test
        void refusesAThicknessAtZero() {
            assertThatThrownBy(() -> Asme8.elongatedLimitIn(0)).hasMessageContaining("greater than 0");
        }
    }

    @Nested
    class AlignedGroups {

        @Test
        void groupsIndicationsThatSitCloseTogether() {
            List<Indication> set = List.of(at("SLAG", 0.1, 0), at("SLAG", 0.1, 0.3));
            assertThat(Asme8.alignedGroups(set)).hasSize(1);
        }

        @Test
        void splitsTheRunWhereTheGapExceedsSixTimesTheLongest() {
            List<Indication> set = List.of(at("SLAG", 0.1, 0), at("SLAG", 0.1, 5));
            assertThat(Asme8.alignedGroups(set)).hasSize(2);
        }

        @Test
        void measuresTheGapFromTheEndOfOneToTheStartOfTheNext() {
            // 0.5 in long ending at 0.5, next starts at 3.4, gap 2.9, under 6 x 0.5.
            List<Indication> together = List.of(at("SLAG", 0.5, 0), at("SLAG", 0.1, 3.4));
            assertThat(Asme8.alignedGroups(together)).hasSize(1);
            List<Indication> apart = List.of(at("SLAG", 0.5, 0), at("SLAG", 0.1, 3.6));
            assertThat(Asme8.alignedGroups(apart)).hasSize(2);
        }

        @Test
        void letsALongIndicationHoldAWiderGroupTogether() {
            List<Indication> withLong = List.of(at("SLAG", 1, 0), at("SLAG", 0.1, 5));
            assertThat(Asme8.alignedGroups(withLong)).hasSize(1);
        }

        @Test
        void sortsBeforeGroupingSoTheCallerDoesNotHaveTo() {
            List<Indication> set = List.of(at("SLAG", 0.1, 0.3), at("SLAG", 0.1, 0));
            assertThat(Asme8.alignedGroups(set)).hasSize(1);
        }

        @Test
        void ignoresIndicationsWithNoPosition() {
            assertThat(Asme8.alignedGroups(List.of(ind("SLAG", 0.1)))).isEmpty();
        }
    }

    @Nested
    class JudgePlanar {

        @Test
        void rejectsACrackWhateverItsSize() {
            assertThat(Asme8.judgePlanar(ind("CRACK", 0.02)).disposition()).isEqualTo(Disposition.REJECT);
        }

        @Test
        void rejectsIncompleteFusionAndIncompletePenetration() {
            assertThat(Asme8.judgePlanar(ind("IF", 0.02)).disposition()).isEqualTo(Disposition.REJECT);
            assertThat(Asme8.judgePlanar(ind("IP", 0.02)).disposition()).isEqualTo(Disposition.REJECT);
        }

        @Test
        void leavesAVolumetricIndicationAlone() {
            assertThat(Asme8.judgePlanar(ind("SLAG", 3)).disposition()).isEqualTo(Disposition.ACCEPT);
        }
    }

    @Nested
    class JudgeElongated {

        @Test
        void acceptsAnIndicationUnderTheBandedLimit() {
            assertThat(Asme8.judgeElongated(ind("SLAG", 0.2), 0.5).disposition())
                    .isEqualTo(Disposition.ACCEPT);
        }

        @Test
        void rejectsOneOverIt() {
            assertThat(Asme8.judgeElongated(ind("SLAG", 0.3), 0.5).disposition())
                    .isEqualTo(Disposition.REPAIR);
        }

        @Test
        void flipsTheVerdictOnTheSameIndicationWhenThePlateThickens() {
            Indication slag = ind("SLAG", 0.4);
            assertThat(Asme8.judgeElongated(slag, 0.5).disposition()).isEqualTo(Disposition.REPAIR);
            assertThat(Asme8.judgeElongated(slag, 1.5).disposition()).isEqualTo(Disposition.ACCEPT);
        }

        @Test
        void sendsAPlanarIndicationToThePlanarRule() {
            assertThat(Asme8.judgeElongated(ind("CRACK", 0.01), 1).disposition())
                    .isEqualTo(Disposition.REJECT);
        }

        @Test
        void leavesARoundedIndicationToTheRoundedRules() {
            assertThat(Asme8.judgeElongated(ind("POROSITY", 5), 1).disposition())
                    .isEqualTo(Disposition.ACCEPT);
        }
    }

    @Nested
    class JudgeGroups {

        @Test
        void acceptsAGroupTotallingUnderTheThickness() {
            List<Indication> set = List.of(at("SLAG", 0.1, 0), at("SLAG", 0.1, 0.3));
            assertThat(Asme8.judgeGroups(set, 1)).isEmpty();
        }

        @Test
        void catchesAGroupTotallingOverTheThicknessInsideTheWindow() {
            List<Indication> set = List.of(at("SLAG", 0.2, 0), at("SLAG", 0.2, 0.5), at("SLAG", 0.2, 1));
            assertThat(Asme8.judgeGroups(set, 0.5)).isNotEmpty();
        }

        @Test
        void letsTheSameTotalThroughOnceTheGroupIsSplitByTheGapRule() {
            List<Indication> set = List.of(at("SLAG", 0.2, 0), at("SLAG", 0.2, 30), at("SLAG", 0.2, 60));
            assertThat(Asme8.judgeGroups(set, 0.5)).isEmpty();
        }

        @Test
        void scalesTheWindowWithTheThickness() {
            List<Indication> set = List.of(at("SLAG", 0.6, 0), at("SLAG", 0.6, 2));
            // 12t is 6 in on a half inch weld, so the group is inside the window.
            assertThat(Asme8.judgeGroups(set, 0.5)).isNotEmpty();
        }
    }

    @Nested
    class RoundedIndications {

        @Test
        void judgesOnlyWhatIsMeasuredAcross() {
            assertThat(Asme8.isRounded(ind("POROSITY", 0.05))).isTrue();
            assertThat(Asme8.isRounded(ind("TUNGSTEN", 0.05))).isTrue();
            assertThat(Asme8.isRounded(ind("SLAG", 0.05))).isFalse();
            assertThat(Asme8.isRounded(ind("CRACK", 0.05))).isFalse();
        }

        @Test
        void ignoresSpecksTooSmallForTheThicknessBand() {
            assertThat(Asme8.relevantOverIn(0.1)).isCloseTo(0.01, digits(4));
            assertThat(Asme8.relevantOverIn(0.125)).isEqualTo(1.0 / 64);
            assertThat(Asme8.relevantOverIn(0.25)).isEqualTo(1.0 / 64);
            assertThat(Asme8.relevantOverIn(0.5)).isEqualTo(1.0 / 32);
            assertThat(Asme8.relevantOverIn(2)).isEqualTo(1.0 / 32);
            assertThat(Asme8.relevantOverIn(3)).isEqualTo(1.0 / 16);
        }

        @Test
        void allowsAPoreStandingAloneMoreThanOneWithNeighbours() {
            assertThat(Asme8.roundedLimitIn(0.5, false)).isEqualTo(0.125);
            assertThat(Asme8.roundedLimitIn(0.5, true)).isCloseTo(0.1667, digits(4));
            assertThat(Asme8.roundedLimitIn(1, false)).isEqualTo(0.1563);
            assertThat(Asme8.roundedLimitIn(1, true)).isEqualTo(0.25);
            assertThat(Asme8.roundedLimitIn(3, true)).isEqualTo(0.375);
        }

        @Test
        void acceptsAPoreAnInchClearOfAnyOther() {
            assertThat(Asme8.judgeRounded(List.of(at("POROSITY", 0.14, 1), at("POROSITY", 0.05, 2.2)), 0.5))
                    .isEmpty();
        }

        @Test
        void holdsTheSamePoreToTheTighterLimitWithANeighbourClose() {
            List<ClauseVerdict> verdicts =
                    Asme8.judgeRounded(List.of(at("POROSITY", 0.14, 1), at("POROSITY", 0.05, 1.5)), 0.5);
            assertThat(verdicts).singleElement().satisfies(verdict -> {
                assertThat(verdict.reason()).hasValue("porosity 0.14 in across, cap is 0.125 in across");
                assertThat(verdict.clause()).isEqualTo("Appendix 4");
            });
        }

        @Test
        void doesNotLetASpeckTooSmallToCountCrowdAPore() {
            assertThat(Asme8.judgeRounded(List.of(at("POROSITY", 0.14, 1), at("POROSITY", 0.02, 1.5)), 0.5))
                    .isEmpty();
        }

        @Test
        void givesAPoreWithNoPositionTheTighterLimit() {
            assertThat(Asme8.judgeRounded(List.of(ind("POROSITY", 0.14)), 0.5)).hasSize(1);
        }

        @Test
        void failsAJointOnAnOversizePoreStandingAlone() {
            JointVerdict result = Asme8.judgeJoint(List.of(at("POROSITY", 0.2, 3)), 0.5);
            assertThat(result.disposition()).isEqualTo(Disposition.REPAIR);
            assertThat(result.reasons())
                    .containsExactly("porosity standing alone 0.2 in across, cap is 0.1667 in across");
        }
    }

    @Nested
    class JudgeJoint {

        @Test
        void acceptsACleanJoint() {
            assertThat(Asme8.judgeJoint(List.of(), 1).disposition()).isEqualTo(Disposition.ACCEPT);
        }

        @Test
        void takesAJointOutForAPlanarIndication() {
            JointVerdict result = Asme8.judgeJoint(List.of(at("IF", 0.05, 1)), 1);
            assertThat(result.disposition()).isEqualTo(Disposition.REJECT);
        }

        @Test
        void callsForARepairOnAnOversizeElongatedIndication() {
            JointVerdict result = Asme8.judgeJoint(List.of(at("SLAG", 0.5, 1)), 0.5);
            assertThat(result.disposition()).isEqualTo(Disposition.REPAIR);
        }

        @Test
        void acceptsTheSameIndicationOnAThickerWeld() {
            JointVerdict result = Asme8.judgeJoint(List.of(at("SLAG", 0.5, 1)), 2);
            assertThat(result.disposition()).isEqualTo(Disposition.ACCEPT);
        }

        @Test
        void refusesAThicknessAtZero() {
            assertThatThrownBy(() -> Asme8.judgeJoint(List.of(), 0)).hasMessageContaining("weld thickness");
        }

        @Test
        void namesItselfInTheResult() {
            assertThat(Asme8.judgeJoint(List.of(), 1).code()).contains(Asme8.codeName());
        }
    }
}
