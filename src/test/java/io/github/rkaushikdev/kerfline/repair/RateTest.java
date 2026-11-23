package io.github.rkaushikdev.kerfline.repair;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RateTest {

    private static JointOutcome joint(String welder, String disposition) {
        return new JointOutcome(welder, disposition);
    }

    private static List<JointOutcome> run(String welder, int total, int repaired) {
        return IntStream.range(0, total)
                .mapToObj(i -> joint(welder, i < repaired ? "repair" : "accept"))
                .toList();
    }

    private static List<JointOutcome> concat(List<JointOutcome> first, List<JointOutcome> second) {
        return Stream.concat(first.stream(), second.stream()).toList();
    }

    private static List<JointOutcome> concat(
            List<JointOutcome> first, List<JointOutcome> second, List<JointOutcome> third) {
        return concat(concat(first, second), third);
    }

    @Nested
    class TheThresholds {

        @Test
        void sitsAtTenPercentOverTwentyJoints() {
            assertThat(Rate.defaultThreshold()).isEqualTo(0.1);
            assertThat(Rate.minimumSample()).isEqualTo(20);
        }
    }

    @Nested
    class RepairRateOf {

        @Test
        void countsRepairsOverJointsShot() {
            RepairRate rate = Rate.repairRate(run("A17", 20, 2));
            assertThat(rate.shot()).isEqualTo(20);
            assertThat(rate.repaired()).isEqualTo(2);
            assertThat(rate.rate()).isCloseTo(0.1, digits(4));
        }

        @Test
        void countsARejectionAsARepairForRatePurposes() {
            RepairRate rate = Rate.repairRate(List.of(joint("A17", "reject"), joint("A17", "accept")));
            assertThat(rate.repaired()).isEqualTo(1);
        }

        @Test
        void isZeroOnAnEmptyListRatherThanDividingByNothing() {
            assertThat(Rate.repairRate(List.of())).isEqualTo(new RepairRate(0, 0, 0));
        }

        @Test
        void ignoresJointsThatHaveNotBeenShotYet() {
            RepairRate rate = Rate.repairRate(List.of(joint("A17", "accept"), joint("A17", null)));
            assertThat(rate.shot()).isEqualTo(1);
        }

        @Test
        void refusesAnEntryThatIsNotARecord() {
            assertThatThrownBy(() -> Rate.repairRate(Arrays.asList((JointOutcome) null)))
                    .hasMessageContaining("must be a joint record");
            assertThatThrownBy(() -> Rate.repairRate(null)).hasMessageContaining("must be an array");
        }
    }

    @Nested
    class WelderRates {

        @Test
        void splitsTheRateByStencil() {
            List<WelderRate> rates = Rate.welderRates(concat(run("A17", 10, 1), run("B04", 10, 3)));
            assertThat(rates).hasSize(2);
            assertThat(rates.get(0).welder()).isEqualTo("B04");
            assertThat(rates.get(0).rate()).isCloseTo(0.3, digits(4));
        }

        @Test
        void putsTheWorstWelderFirst() {
            List<JointOutcome> joints = concat(run("A17", 10, 5), run("B04", 10, 1));
            assertThat(Rate.welderRates(joints).get(0).welder()).isEqualTo("A17");
        }

        @Test
        void gathersJointsWithNoWelderUnderOneHeading() {
            List<WelderRate> rates = Rate.welderRates(List.of(joint(null, "accept"), joint(null, "repair")));
            assertThat(rates).hasSize(1);
            assertThat(rates.get(0).welder()).isEqualTo("unknown");
        }
    }

    @Nested
    class RollingRate {

        @Test
        void looksOnlyAtTheMostRecentJoints() {
            List<JointOutcome> joints = concat(run("A17", 10, 10), run("A17", 20, 0));
            assertThat(Rate.repairRate(joints).rate()).isCloseTo(0.3333, digits(3));
            assertThat(Rate.rollingRate(joints, 20).rate()).isEqualTo(0);
        }

        @Test
        void takesEverythingWhenTheWindowIsLongerThanTheHistory() {
            assertThat(Rate.rollingRate(run("A17", 5, 1), 20).shot()).isEqualTo(5);
        }

        @Test
        void refusesAWindowThatIsNotAWholeNumberAboveZero() {
            assertThatThrownBy(() -> Rate.rollingRate(List.of(), 0)).hasMessageContaining("greater than 0");
            assertThatThrownBy(() -> Rate.rollingRate(List.of(), -3)).hasMessageContaining("window size");
        }
    }

    @Nested
    class ExceedsThreshold {

        @Test
        void holdsBackWhileTheSampleIsTooSmallToSay() {
            ThresholdCheck result = Rate.exceedsThreshold(run("A17", 3, 3));
            assertThat(result.rate()).isEqualTo(1);
            assertThat(result.exceeds()).isFalse();
            assertThat(result.enoughToSay()).isFalse();
            assertThat(result.shortBy()).isEqualTo(17);
        }

        @Test
        void reportsAnExceedanceOnceTheSampleIsBigEnough() {
            ThresholdCheck result = Rate.exceedsThreshold(run("A17", 20, 3));
            assertThat(result.enoughToSay()).isTrue();
            assertThat(result.exceeds()).isTrue();
        }

        @Test
        void doesNotCallTheThresholdItselfAnExceedance() {
            ThresholdCheck result = Rate.exceedsThreshold(run("A17", 20, 2));
            assertThat(result.rate()).isCloseTo(0.1, digits(4));
            assertThat(result.exceeds()).isFalse();
        }

        @Test
        void takesAJobSpecificThresholdAndSample() {
            ThresholdCheck result = Rate.exceedsThreshold(
                    run("A17", 10, 1), ThresholdOptions.DEFAULTS.threshold(0.05).minimumSample(10));
            assertThat(result.exceeds()).isTrue();
        }

        @Test
        void canBeAskedOverARollingWindowInstead() {
            List<JointOutcome> joints = concat(run("A17", 20, 20), run("A17", 20, 0));
            assertThat(Rate.exceedsThreshold(joints).exceeds()).isTrue();
            assertThat(Rate.exceedsThreshold(joints, ThresholdOptions.DEFAULTS.windowSize(20))
                            .exceeds())
                    .isFalse();
        }
    }

    @Nested
    class WeldersOverThreshold {

        @Test
        void namesOnlyTheWeldersPastTheLimitWithEnoughJointsToSaySo() {
            List<JointOutcome> joints = concat(run("A17", 20, 5), run("B04", 20, 1), run("C09", 3, 3));
            assertThat(Rate.weldersOverThreshold(joints).stream().map(WelderThreshold::welder))
                    .containsExactly("A17");
        }

        @Test
        void isEmptyWhenEverybodyIsInsideTheLimit() {
            assertThat(Rate.weldersOverThreshold(run("A17", 20, 1))).isEqualTo(List.of());
        }

        @Test
        void sortsTheWorstFirst() {
            List<JointOutcome> joints = concat(run("A17", 20, 4), run("B04", 20, 8));
            assertThat(Rate.weldersOverThreshold(joints).get(0).welder()).isEqualTo("B04");
        }
    }

    @Nested
    class CleanJointsToRecover {

        @Test
        void isNothingWhenTheWelderIsAlreadyUnder() {
            assertThat(Rate.cleanJointsToRecover(run("A17", 20, 1))).isZero();
            assertThat(Rate.cleanJointsToRecover(List.of())).isZero();
        }

        @Test
        void countsTheCleanJointsNeededToGetBackUnder() {
            // 4 repairs in 20 is 20 percent; 40 joints total brings it to 10 percent.
            assertThat(Rate.cleanJointsToRecover(run("A17", 20, 4))).isEqualTo(20);
        }

        @Test
        void actuallyBringsTheRateBackUnderWhenThoseJointsAreShot() {
            List<JointOutcome> history = run("A17", 20, 4);
            int needed = Rate.cleanJointsToRecover(history);
            List<JointOutcome> after = new ArrayList<>(history);
            after.addAll(run("A17", needed, 0));
            assertThat(Rate.repairRate(after).rate()).isLessThanOrEqualTo(0.1);
        }

        @Test
        void takesAJobSpecificThreshold() {
            assertThat(Rate.cleanJointsToRecover(run("A17", 20, 2), ThresholdOptions.DEFAULTS.threshold(0.05)))
                    .isEqualTo(20);
        }
    }

    @Nested
    class ByLengthOfWeld {

        private final List<WeldLengthOutcome> joints = List.of(
                new WeldLengthOutcome("W-7", 7.5, 0),
                new WeldLengthOutcome("W-7", 113.1, 2),
                new WeldLengthOutcome("W-9", 7.5, 1.5),
                new WeldLengthOutcome("W-9", 7.5, 0),
                new WeldLengthOutcome(null, 20.8, 0));

        @Test
        void putsInchesRepairedOverInchesRadiographed() {
            LengthRate all = Rate.lengthRate(joints);
            assertThat(all.welder()).isEqualTo("all");
            assertThat(all.radiographedIn()).isEqualTo(156.4);
            assertThat(all.repairedIn()).isEqualTo(3.5);
            assertThat(all.rate()).isEqualTo(0.0224);
        }

        @Test
        void tellsTheSmallBoreWelderFromTheBigPipeOne() {
            // Each welder has one repair in two joints; by length W-9 put in far more bad weld.
            List<LengthRate> rates = Rate.welderLengthRates(joints);
            assertThat(rates.stream().map(LengthRate::welder)).containsExactly("W-9", "W-7", "unknown");
            assertThat(rates.get(0).rate()).isEqualTo(0.1);
            assertThat(rates.get(1).rate()).isEqualTo(0.0166);
            assertThat(Rate.welderRates(List.of(
                                    new JointOutcome("W-7", "accept"),
                                    new JointOutcome("W-7", "repair"),
                                    new JointOutcome("W-9", "repair"),
                                    new JointOutcome("W-9", "accept")))
                            .stream()
                            .map(WelderRate::rate))
                    .containsExactly(0.5, 0.5);
        }

        @Test
        void isNoughtOverNothingRadiographed() {
            assertThat(Rate.lengthRate(List.of()).rate()).isZero();
            assertThat(Rate.welderLengthRates(List.of())).isEmpty();
        }

        @Test
        void refusesMoreRepairedThanWasShot() {
            assertThatThrownBy(() -> Rate.lengthRate(List.of(new WeldLengthOutcome("W-7", 7.5, 8))))
                    .hasMessageContaining("more weld repaired than was radiographed");
            assertThatThrownBy(() -> Rate.lengthRate(List.of(new WeldLengthOutcome("W-7", -1, 0))))
                    .hasMessageContaining("length radiographed");
            List<WeldLengthOutcome> withHole = new ArrayList<>();
            withHole.add(null);
            assertThatThrownBy(() -> Rate.lengthRate(withHole)).hasMessageContaining("joint record");
        }
    }
}
