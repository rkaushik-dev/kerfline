package io.github.rkaushikdev.kerfline.repair;

import static io.github.rkaushikdev.kerfline.util.Rate.itemsToReach;
import static io.github.rkaushikdev.kerfline.util.Rate.rateOf;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * Welder repair rate.
 *
 * <p>The number itself is easy. What makes it worth a class is that a rate is meaningless without a
 * denominator big enough to carry it: one repair in the first three joints of a spread is a hundred
 * percent above the limit and says nothing at all about the welder. So every threshold here needs a
 * minimum sample before it will report an exceedance, and the rate is reported alongside the count
 * it was computed from rather than on its own.
 *
 * <p>The rolling window is the one the job actually runs on. A welder who had a bad afternoon in
 * March and has been clean since is not the same as one who is drifting now, and a rate over the
 * whole job cannot tell them apart.
 */
public final class Rate {

    private static final double THRESHOLD = 0.1;
    private static final int MINIMUM_SAMPLE = 20;
    private static final String UNKNOWN_WELDER = "unknown";
    private static final String ALL_WELDERS = "all";
    private static final String ACCEPT = "accept";

    private Rate() {}

    public static double defaultThreshold() {
        return THRESHOLD;
    }

    public static int minimumSample() {
        return MINIMUM_SAMPLE;
    }

    /** The joints that have a disposition, refusing anything that is not a joint record. */
    private static List<JointOutcome> countable(List<JointOutcome> joints) {
        Guard.requireArray(joints, "joints");
        List<JointOutcome> counted = new ArrayList<>();
        for (JointOutcome joint : joints) {
            if (joint == null) {
                throw new BadInputException("every entry must be a joint record", Details.of("got", null));
            }
            if (joint.disposition() != null) {
                counted.add(joint);
            }
        }
        return counted;
    }

    /**
     * Repairs over joints shot, with the counts it came from. Anything not accepted counts as a
     * repair, a rejection included; a joint with no disposition has not been shot and is left out.
     */
    public static RepairRate repairRate(List<JointOutcome> joints) {
        List<JointOutcome> counted = countable(joints);
        int repaired = (int) counted.stream()
                .filter(joint -> !ACCEPT.equals(joint.disposition()))
                .count();
        return new RepairRate(counted.size(), repaired, rateOf(repaired, counted.size()));
    }

    /**
     * The same, split by welder stencil, worst rate first and stencils in order between equal
     * rates. Joints with no stencil are gathered under {@code unknown}.
     */
    public static List<WelderRate> welderRates(List<JointOutcome> joints) {
        return byWelder(countable(joints), JointOutcome::welder).entrySet().stream()
                .map(entry -> WelderRate.of(entry.getKey(), repairRate(entry.getValue())))
                .sorted(worstFirst(WelderRate::rate, WelderRate::welder))
                .toList();
    }

    /** Records grouped under their welder's stencil, in the order the stencils first appear. */
    private static <T> Map<String, List<T>> byWelder(List<T> records, Function<T, String> welderOf) {
        Map<String, List<T>> grouped = new LinkedHashMap<>();
        for (T record : records) {
            String welder = Objects.requireNonNullElse(welderOf.apply(record), UNKNOWN_WELDER);
            grouped.computeIfAbsent(welder, key -> new ArrayList<>()).add(record);
        }
        return grouped;
    }

    private static <T> Comparator<T> worstFirst(ToDoubleFunction<T> rate, Function<T, String> welder) {
        Comparator<T> byRate = Comparator.comparingDouble(rate);
        return byRate.reversed().thenComparing(welder, Collator.getInstance(Locale.ROOT));
    }

    /**
     * The repair rate by length of weld rather than by joints: inches ground out and rewelded over
     * inches radiographed. A client paying by the foot reads it this way, and it is the fairer number
     * for a welder whose joints are mixed sizes, where one repair on a two inch joint and one on a
     * thirty six inch joint are not the same amount of bad weld.
     */
    public static LengthRate lengthRate(List<WeldLengthOutcome> joints) {
        return lengthRate(ALL_WELDERS, joints);
    }

    /** The length rate split by welder stencil, worst first and stencils in order between equals. */
    public static List<LengthRate> welderLengthRates(List<WeldLengthOutcome> joints) {
        Guard.requireArray(joints, "joints");
        return byWelder(joints, WeldLengthOutcome::welder).entrySet().stream()
                .map(entry -> lengthRate(entry.getKey(), entry.getValue()))
                .sorted(worstFirst(LengthRate::rate, LengthRate::welder))
                .toList();
    }

    private static LengthRate lengthRate(String welder, List<WeldLengthOutcome> joints) {
        Guard.requireArray(joints, "joints");
        double radiographed = 0;
        double repaired = 0;
        for (WeldLengthOutcome joint : joints) {
            if (joint == null) {
                throw new BadInputException("every entry must be a joint record", Details.of("got", null));
            }
            Guard.requireAtLeast(joint.radiographedIn(), 0, "length radiographed");
            Guard.requireAtLeast(joint.repairedIn(), 0, "length repaired");
            if (joint.repairedIn() > joint.radiographedIn()) {
                throw new BadInputException(
                        "a joint cannot have more weld repaired than was radiographed",
                        Details.of("radiographedIn", joint.radiographedIn(), "repairedIn", joint.repairedIn()));
            }
            radiographed += joint.radiographedIn();
            repaired += joint.repairedIn();
        }
        return new LengthRate(
                welder,
                Num.round(radiographed, 2),
                Num.round(repaired, 2),
                radiographed == 0 ? 0 : Num.round(repaired / radiographed, 4));
    }

    public static RepairRate rollingRate(List<JointOutcome> joints) {
        return rollingRate(joints, MINIMUM_SAMPLE);
    }

    /** The rate over the most recent joints only. */
    public static RepairRate rollingRate(List<JointOutcome> joints, int windowSize) {
        Guard.requireAbove(windowSize, 0, "window size");
        List<JointOutcome> counted = countable(joints);
        return repairRate(counted.subList(Math.max(0, counted.size() - windowSize), counted.size()));
    }

    public static ThresholdCheck exceedsThreshold(List<JointOutcome> joints) {
        return exceedsThreshold(joints, ThresholdOptions.DEFAULTS);
    }

    /**
     * Whether a welder is over the limit. Answers no while the sample is too small to say, and says
     * so with how many joints it is short by, rather than reporting an exceedance nobody should act
     * on. A rate exactly on the threshold is not over it.
     */
    public static ThresholdCheck exceedsThreshold(List<JointOutcome> joints, ThresholdOptions options) {
        double threshold = options.threshold();
        int sample = options.minimumSample();
        Guard.requireAbove(threshold, 0, "threshold");
        RepairRate measured = options.windowSize() > 0 ? rollingRate(joints, options.windowSize()) : repairRate(joints);
        if (measured.shot() < sample) {
            return new ThresholdCheck(
                    measured.shot(),
                    measured.repaired(),
                    measured.rate(),
                    threshold,
                    false,
                    false,
                    sample - measured.shot());
        }
        return new ThresholdCheck(
                measured.shot(), measured.repaired(), measured.rate(), threshold, measured.rate() > threshold, true, 0);
    }

    public static List<WelderThreshold> weldersOverThreshold(List<JointOutcome> joints) {
        return weldersOverThreshold(joints, ThresholdOptions.DEFAULTS);
    }

    /** Every welder currently over the limit, worst first. */
    public static List<WelderThreshold> weldersOverThreshold(List<JointOutcome> joints, ThresholdOptions options) {
        return byWelder(countable(joints), JointOutcome::welder).entrySet().stream()
                .map(entry -> WelderThreshold.of(entry.getKey(), exceedsThreshold(entry.getValue(), options)))
                .filter(WelderThreshold::exceeds)
                .sorted(Comparator.comparingDouble(WelderThreshold::rate).reversed())
                .toList();
    }

    public static int cleanJointsToRecover(List<JointOutcome> joints) {
        return cleanJointsToRecover(joints, ThresholdOptions.DEFAULTS);
    }

    /**
     * How many more clean joints it would take to bring a welder back to or under the limit. This
     * is the number a foreman actually asks for. Only the threshold of the options is read.
     */
    public static int cleanJointsToRecover(List<JointOutcome> joints, ThresholdOptions options) {
        double threshold = options.threshold();
        RepairRate measured = repairRate(joints);
        if (measured.shot() == 0 || measured.rate() <= threshold) {
            return 0;
        }
        // repaired / (shot + n) <= threshold
        return itemsToReach(measured.repaired(), measured.shot(), threshold);
    }
}
