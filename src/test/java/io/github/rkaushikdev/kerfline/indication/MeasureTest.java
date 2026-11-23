package io.github.rkaushikdev.kerfline.indication;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MeasureTest {

    private static Indication indication(IndicationInput input) {
        return Measure.makeIndication(input);
    }

    private static IndicationInput slag(double measuredIn) {
        return IndicationInput.of("SLAG", measuredIn);
    }

    @Nested
    class MakeIndication {

        @Test
        void takesTheMeasurementAsReadOnAContactShot() {
            Indication made = indication(slag(0.25));
            assertThat(made.sizeIn()).isEqualTo(0.25);
            assertThat(made.magnification()).isEqualTo(1);
        }

        @Test
        void backsTheMagnificationOutOfTheMeasurement() {
            Indication made = indication(slag(0.3125).magnification(1.25));
            assertThat(made.sizeIn()).isCloseTo(0.25, digits(4));
            assertThat(made.measuredIn()).isEqualTo(0.3125);
        }

        @Test
        void keepsWhatWasMeasuredSoTheFilmCanBeCheckedAgain() {
            Indication made = indication(slag(0.3125).magnification(1.25));
            assertThat(made.measuredIn()).isEqualTo(0.3125);
            assertThat(made.sizeIn()).isNotEqualTo(made.measuredIn());
        }

        @Test
        void carriesTheTypeThrough() {
            Indication made = indication(IndicationInput.of("crack", 0.1));
            assertThat(made.type()).isEqualTo(IndicationType.CRACK);
            assertThat(made.planar()).isTrue();
            assertThat(made.measure()).isEqualTo(MeasuredBy.LENGTH);
        }

        @Test
        void scalesAWidthTheSameWay() {
            Indication made = indication(
                    IndicationInput.of("POROSITY", 0.05).widthIn(0.0625).magnification(1.25));
            assertThat(made.widthIn().getAsDouble()).isCloseTo(0.05, digits(4));
        }

        @Test
        void leavesTheWidthNullWhenNoneWasTaken() {
            assertThat(indication(slag(0.25)).widthIn()).isEmpty();
        }

        @Test
        void refusesAMagnificationBelowOne() {
            assertThatThrownBy(() -> indication(slag(0.25).magnification(0.9))).hasMessageContaining("not be below 1");
        }

        @Test
        void refusesANegativeMeasurement() {
            assertThatThrownBy(() -> indication(slag(-0.1))).hasMessageContaining("not be below 0");
        }

        @Test
        void refusesATypeItDoesNotCarry() {
            assertThatThrownBy(() -> indication(IndicationInput.of("LAMINATION", 0.1)))
                    .hasMessageContaining("no indication type");
        }

        @Test
        void refusesSomethingThatIsNotAnObject() {
            assertThatThrownBy(() -> Measure.makeIndication(null)).hasMessageContaining("described by an object");
        }
    }

    @Nested
    class GoverningSizeIn {

        @Test
        void isTheLengthForSomethingJudgedOnLength() {
            Indication made = indication(slag(0.25).widthIn(0.4));
            assertThat(Measure.governingSizeIn(made)).isEqualTo(0.25);
        }

        @Test
        void isTheLargerDimensionForSomethingJudgedOnDiameter() {
            Indication pore = indication(IndicationInput.of("POROSITY", 0.05).widthIn(0.08));
            assertThat(Measure.governingSizeIn(pore)).isCloseTo(0.08, digits(4));
        }

        @Test
        void fallsBackToTheLengthWhenNoWidthWasTaken() {
            Indication pore = indication(IndicationInput.of("POROSITY", 0.05));
            assertThat(Measure.governingSizeIn(pore)).isEqualTo(0.05);
        }
    }

    @Nested
    class CumulativeLengthIn {

        @Test
        void addsTheLengths() {
            List<Indication> set = List.of(indication(slag(0.25)), indication(slag(0.125)));
            assertThat(Measure.cumulativeLengthIn(set)).isCloseTo(0.375, digits(4));
        }

        @Test
        void leavesTheRoundOnesOutOfTheTotal() {
            List<Indication> set = List.of(indication(slag(0.25)), indication(IndicationInput.of("POROSITY", 0.06)));
            assertThat(Measure.cumulativeLengthIn(set)).isCloseTo(0.25, digits(4));
        }

        @Test
        void isZeroForAnEmptySet() {
            assertThat(Measure.cumulativeLengthIn(List.of())).isEqualTo(0);
        }

        @Test
        void addsTheTrueLengthsAndNotTheMeasuredOnes() {
            List<Indication> set = List.of(indication(slag(0.5).magnification(2)));
            assertThat(Measure.cumulativeLengthIn(set)).isCloseTo(0.25, digits(4));
        }

        @Test
        void refusesAnythingThatIsNotAnArray() {
            assertThatThrownBy(() -> Measure.cumulativeLengthIn(null)).hasMessageContaining("must be an array");
        }
    }

    @Nested
    class LongestInAndOverLimit {

        private final List<Indication> set = List.of(
                indication(slag(0.25)), indication(slag(0.5)), indication(IndicationInput.of("POROSITY", 0.06)));

        @Test
        void findsTheLongest() {
            assertThat(Measure.longestIn(set)).isEqualTo(0.5);
        }

        @Test
        void isZeroForAnEmptySet() {
            assertThat(Measure.longestIn(List.of())).isEqualTo(0);
        }

        @Test
        void listsWhatIsOverALimit() {
            assertThat(Measure.overLimit(set, 0.25)).hasSize(1);
            assertThat(Measure.overLimit(set, 0.5)).isEmpty();
            assertThat(Measure.overLimit(set, 0.05)).hasSize(3);
        }

        @Test
        void treatsALimitAsInclusive() {
            assertThat(Measure.overLimit(set, 0.5)).isEqualTo(List.of());
        }
    }

    @Nested
    class WorstWindow {

        private final List<Indication> set = List.of(
                indication(slag(0.2).at(0)),
                indication(slag(0.3).at(1)),
                indication(slag(0.4).at(2)),
                indication(slag(0.5).at(20)));

        @Test
        void findsTheRunOfWeldCarryingTheMost() {
            WeldWindow worst = Measure.worstWindow(set, 6);
            assertThat(worst.startIn()).isEqualTo(0);
            assertThat(worst.totalIn()).isCloseTo(0.9, digits(4));
            assertThat(worst.indications()).hasSize(3);
        }

        @Test
        void shrinksTheTotalAsTheWindowNarrows() {
            assertThat(Measure.worstWindow(set, 1.5).totalIn()).isCloseTo(0.7, digits(4));
            assertThat(Measure.worstWindow(set, 0.5).totalIn()).isCloseTo(0.5, digits(4));
        }

        @Test
        void picksTheIsolatedOneUpWhenTheWindowIsSmallEnough() {
            assertThat(Measure.worstWindow(set, 0.5).startIn()).isEqualTo(20);
        }

        @Test
        void returnsNothingWhenNoIndicationCarriesAPosition() {
            List<Indication> unplaced = List.of(indication(slag(0.2)));
            assertThat(Measure.worstWindow(unplaced, 6)).isEqualTo(new WeldWindow(0, 0, List.of()));
        }

        @Test
        void refusesAWindowAtZero() {
            assertThatThrownBy(() -> Measure.worstWindow(set, 0)).hasMessageContaining("greater than 0");
        }
    }

    @Nested
    class WorstWindowRound {

        // A 6 in pipe is 20.8 in round; two short runs sit either side of the tie-in.
        private static final double ROUND = 20.8;

        private final List<Indication> set = List.of(
                indication(slag(0.4).at(19.5)),
                indication(slag(0.4).at(20.3)),
                indication(slag(0.4).at(0.2)),
                indication(slag(0.4).at(0.9)));

        @Test
        void readsTheRunsEitherSideOfTheTieInAsOne() {
            WeldWindow worst = Measure.worstWindowRound(set, 12, ROUND);
            assertThat(worst.totalIn()).isCloseTo(1.6, digits(4));
            assertThat(worst.startIn()).isEqualTo(19.5);
            assertThat(worst.indications()).hasSize(4);
        }

        @Test
        void seesOnlyHalfOfThemOnAWeldWithEnds() {
            assertThat(Measure.worstWindow(set, 12).totalIn()).isCloseTo(0.8, digits(4));
        }

        @Test
        void stopsShortOfWhatTheWindowCannotReach() {
            assertThat(Measure.worstWindowRound(set, 2, ROUND).totalIn()).isCloseTo(1.2, digits(4));
            assertThat(Measure.worstWindowRound(set, 1, ROUND).totalIn()).isCloseTo(0.8, digits(4));
        }

        @Test
        void holdsTheWholeWeldWhenTheWindowIsLongerThanIt() {
            List<Indication> more = new ArrayList<>(set);
            more.add(indication(slag(0.3).at(10)));
            WeldWindow worst = Measure.worstWindowRound(more, 30, ROUND);
            assertThat(worst.totalIn()).isCloseTo(1.9, digits(4));
            assertThat(worst.indications()).hasSize(5);
        }

        @Test
        void countsOnlyWhatIsMeasuredByLength() {
            List<Indication> mixed = List.of(
                    indication(slag(0.4).at(20)),
                    indication(IndicationInput.of("POROSITY", 0.1).at(0.5)));
            assertThat(Measure.worstWindowRound(mixed, 12, ROUND).totalIn()).isCloseTo(0.4, digits(4));
        }

        @Test
        void refusesAWeldWithNoLength() {
            assertThatThrownBy(() -> Measure.worstWindowRound(set, 12, 0)).hasMessageContaining("round the joint");
        }
    }
}
