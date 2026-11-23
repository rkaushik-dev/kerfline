package io.github.rkaushikdev.kerfline.accept;

import io.github.rkaushikdev.kerfline.indication.Indication;
import io.github.rkaushikdev.kerfline.indication.Measure;
import io.github.rkaushikdev.kerfline.indication.MeasuredBy;
import io.github.rkaushikdev.kerfline.util.Band;
import io.github.rkaushikdev.kerfline.util.BandTable;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * ASME Section VIII Division 1, UW-51, for fully radiographed pressure welds.
 *
 * <p>Two things here are worth reading twice.
 *
 * <p>The first is that the length limit for an elongated indication is banded on thickness and the
 * middle band is a fraction of the thickness rather than a fixed number. So the limit is 1/4 in on
 * thin work, then climbs with the plate through the middle band, then stops climbing at 3/4 in. A
 * limit that is constant, then proportional, then constant again catches people out at both
 * corners.
 *
 * <p>The second is the aligned group rule. A run of small indications in a line is judged as one,
 * with the total capped at t in any 12t of weld, but only while they are close together. Once the
 * gap between two of them is more than six times the longest one in the group, the group is not a
 * group any more and the run is split there. That exception is what stops a code written for
 * aligned porosity from condemning a weld with two unrelated specks at opposite ends.
 */
public final class Asme8 {

    private static final String CODE = "ASME8";
    private static final String CLAUSE = "UW-51(b)";

    private static final BandTable<String> LENGTH_LIMIT = Band.bandTable(
            List.of(
                    Band.upTo(0.75, "quarter"),
                    Band.upTo(2.25, "third"),
                    Band.upTo(Double.POSITIVE_INFINITY, "threequarter")),
            "UW-51 length limit");

    private static final double GROUP_WINDOW_MULTIPLE = 12;
    private static final double GAP_MULTIPLE = 6;
    private static final String ROUNDED_CLAUSE = "Appendix 4";
    private static final double ISOLATION_IN = 1;
    private static final double THIN_IN = 0.125;
    private static final int THIN_COUNT = 12;
    private static final double COUNT_WINDOW_IN = 6;

    private Asme8() {}

    public static String codeName() {
        return CODE;
    }

    /** The cap on a single elongated indication, banded on the weld thickness. */
    public static double elongatedLimitIn(double thicknessIn) {
        Guard.requireAbove(thicknessIn, 0, "weld thickness");
        return switch (Band.bandValue(LENGTH_LIMIT, thicknessIn)) {
            case "quarter" -> 0.25;
            case "third" -> Num.round(thicknessIn / 3, 4);
            default -> 0.75;
        };
    }

    public static List<LengthBand> limitBands() {
        return LENGTH_LIMIT.rows().stream()
                .map(row -> new LengthBand(row.over(), row.upTo(), row.value()))
                .toList();
    }

    /**
     * Split indications into aligned groups, in order along the weld. Successive ones stay in the
     * same group while the gap between the end of one and the start of the next is no more than six
     * times the longest indication in the group so far. Indications with no position take no part.
     */
    public static List<List<Indication>> alignedGroups(List<Indication> indications) {
        List<Indication> placed = Measure.alongTheWeld(indications);
        if (placed.isEmpty()) {
            return List.of();
        }

        List<List<Indication>> groups = new ArrayList<>();
        groups.add(new ArrayList<>(List.of(placed.get(0))));
        for (int i = 1; i < placed.size(); i++) {
            List<Indication> current = groups.get(groups.size() - 1);
            Indication previous = current.get(current.size() - 1);
            double longest = Double.NEGATIVE_INFINITY;
            for (Indication member : current) {
                longest = Math.max(longest, Measure.governingSizeIn(member));
            }
            if (Measure.gapIn(previous, placed.get(i)) > GAP_MULTIPLE * longest) {
                groups.add(new ArrayList<>(List.of(placed.get(i))));
            } else {
                current.add(placed.get(i));
            }
        }
        return groups.stream().map(List::copyOf).toList();
    }

    /** Any crack, lack of fusion or lack of penetration takes the weld out. */
    public static ClauseVerdict judgePlanar(Indication indication) {
        if (!indication.planar()) {
            return Verdict.accept(CODE, CLAUSE);
        }
        return Common.rejectPlanar(CODE, CLAUSE + "(1)", indication);
    }

    /**
     * A single elongated indication against the banded limit. A planar one goes to the planar rule
     * and one judged on its diameter is left to the rounded indication rules.
     */
    public static ClauseVerdict judgeElongated(Indication indication, double thicknessIn) {
        if (indication.planar()) {
            return judgePlanar(indication);
        }
        if (indication.measure() != MeasuredBy.LENGTH) {
            return Verdict.accept(CODE, CLAUSE);
        }
        return Common.capVerdict(CapCheck.of(CODE, CLAUSE + "(2)", indication.label())
                .size(Measure.governingSizeIn(indication))
                .cap(elongatedLimitIn(thicknessIn))
                .units("in long")
                .indication(indication));
    }

    /** True for what Appendix 4 judges: an indication measured across rather than along. */
    public static boolean isRounded(Indication indication) {
        return !indication.planar() && indication.measure() == MeasuredBy.DIAMETER;
    }

    /**
     * The size a rounded indication has to exceed before Appendix 4 counts it at all, banded on the
     * thickness: a tenth of it under an eighth of an inch, then 1/64, 1/32 and 1/16 in.
     */
    public static double relevantOverIn(double thicknessIn) {
        Guard.requireAbove(thicknessIn, 0, "weld thickness");
        if (thicknessIn < THIN_IN) {
            return Num.round(thicknessIn / 10, 4);
        }
        if (thicknessIn <= 0.25) {
            return 1.0 / 64;
        }
        return thicknessIn <= 2 ? 1.0 / 32 : 1.0 / 16;
    }

    /**
     * The largest rounded indication Appendix 4 accepts. One standing alone, with an inch or more of
     * clear weld to its nearest neighbour, is allowed a third of the thickness up to a quarter inch,
     * or three eighths on work over two inches; any other is held to a quarter of the thickness up to
     * five thirty seconds.
     */
    public static double roundedLimitIn(double thicknessIn, boolean isolated) {
        Guard.requireAbove(thicknessIn, 0, "weld thickness");
        if (!isolated) {
            return Num.round(Math.min(thicknessIn / 4, 5.0 / 32), 4);
        }
        return thicknessIn > 2 ? 0.375 : Num.round(Math.min(thicknessIn / 3, 0.25), 4);
    }

    /** The rounded indications Appendix 4 counts, the ones too small to matter left out. */
    public static List<Indication> relevantRounded(List<Indication> indications, double thicknessIn) {
        Guard.requireArray(indications, "indications");
        double over = relevantOverIn(thicknessIn);
        return indications.stream()
                .filter(Asme8::isRounded)
                .filter(indication -> Measure.governingSizeIn(indication) > over)
                .toList();
    }

    /**
     * Every relevant rounded indication against its size limit. Whether one stands alone is read
     * from the clear weld to its nearest relevant neighbour either side; one with no position cannot
     * be shown to stand alone and gets the tighter limit.
     */
    public static List<ClauseVerdict> judgeRounded(List<Indication> indications, double thicknessIn) {
        List<Indication> relevant = relevantRounded(indications, thicknessIn);
        List<Indication> placed = Measure.alongTheWeld(relevant);
        List<ClauseVerdict> verdicts = new ArrayList<>();
        for (Indication indication : relevant) {
            int i = placed.indexOf(indication);
            boolean isolated = i >= 0
                    && (i == 0 || Measure.gapIn(placed.get(i - 1), indication) >= ISOLATION_IN)
                    && (i == placed.size() - 1 || Measure.gapIn(indication, placed.get(i + 1)) >= ISOLATION_IN);
            ClauseVerdict verdict = Common.capVerdict(CapCheck.of(
                            CODE,
                            ROUNDED_CLAUSE,
                            isolated ? indication.label() + " standing alone" : indication.label())
                    .size(Measure.governingSizeIn(indication))
                    .cap(roundedLimitIn(thicknessIn, isolated))
                    .units("in across")
                    .indication(indication));
            if (!Verdict.isAcceptable(verdict)) {
                verdicts.add(verdict);
            }
        }
        return List.copyOf(verdicts);
    }

    /**
     * The aligned group rule. Each group lying inside twelve thicknesses of weld is capped at the
     * weld thickness.
     */
    public static List<ClauseVerdict> judgeGroups(List<Indication> indications, double thicknessIn) {
        Guard.requireAbove(thicknessIn, 0, "weld thickness");
        double window = GROUP_WINDOW_MULTIPLE * thicknessIn;
        List<ClauseVerdict> verdicts = new ArrayList<>();
        for (List<Indication> group : alignedGroups(indications)) {
            double sum = 0;
            for (Indication indication : group) {
                sum += Measure.governingSizeIn(indication);
            }
            double total = Num.round(sum, 4);
            Indication last = group.get(group.size() - 1);
            double span = Common.at(last) + Measure.governingSizeIn(last) - Common.at(group.get(0));
            if (span > window || total <= thicknessIn) {
                continue;
            }
            verdicts.add(Verdict.makeVerdict(
                    Disposition.REPAIR,
                    CODE,
                    CLAUSE + "(3)",
                    "aligned group totals " + Num.text(total) + " in in " + Num.text(Num.round(window, 3))
                            + " in of weld, cap is " + Num.text(Num.round(thicknessIn, 3)) + " in",
                    total));
        }
        return List.copyOf(verdicts);
    }

    /**
     * The count of rounded indications Appendix 4 allows on work under an eighth of an inch: twelve
     * in any six inches of weld, and on a weld shorter than six inches proportionally fewer, rounded
     * down. Thicker work is judged on the charts instead and has no count here.
     */
    public static OptionalInt roundedCountLimit(double thicknessIn, OptionalDouble weldLengthIn) {
        Guard.requireAbove(thicknessIn, 0, "weld thickness");
        if (thicknessIn >= THIN_IN) {
            return OptionalInt.empty();
        }
        if (weldLengthIn.isPresent() && weldLengthIn.getAsDouble() < COUNT_WINDOW_IN) {
            double length = weldLengthIn.getAsDouble();
            Guard.requireAbove(length, 0, "weld length");
            return OptionalInt.of((int) Math.floor(Num.round(THIN_COUNT * length / COUNT_WINDOW_IN, 6)));
        }
        return OptionalInt.of(THIN_COUNT);
    }

    /**
     * The thin work count, over the relevant rounded indications in the worst six inches. When none
     * of them was located the whole set is counted as one run.
     */
    public static List<ClauseVerdict> judgeRoundedCount(
            List<Indication> indications, double thicknessIn, OptionalDouble weldLengthIn) {
        OptionalInt limit = roundedCountLimit(thicknessIn, weldLengthIn);
        List<Indication> relevant = relevantRounded(indications, thicknessIn);
        if (limit.isEmpty() || relevant.isEmpty()) {
            return List.of();
        }
        double window =
                Measure.worstWindow(relevant, COUNT_WINDOW_IN, indication -> 1).totalIn();
        int count = (int) (window > 0 ? window : relevant.size());
        if (count <= limit.getAsInt()) {
            return List.of();
        }
        return List.of(Verdict.makeVerdict(
                Disposition.REPAIR,
                CODE,
                ROUNDED_CLAUSE,
                count + " rounded indications in " + Num.text(COUNT_WINDOW_IN) + " in of weld, " + limit.getAsInt()
                        + " allowed on " + Num.text(thicknessIn) + " in",
                count));
    }

    /** Judge a whole joint when the length of the seam was not given. */
    public static JointVerdict judgeJoint(List<Indication> indications, double thicknessIn) {
        return judgeJoint(indications, thicknessIn, OptionalDouble.empty());
    }

    /**
     * Judge a whole joint: each indication on its own, then the aligned groups of the volumetric ones,
     * then the rounded ones under Appendix 4, sizes first and then, on thin work, how many.
     */
    public static JointVerdict judgeJoint(
            List<Indication> indications, double thicknessIn, OptionalDouble weldLengthIn) {
        Guard.requireArray(indications, "indications");
        Guard.requireAbove(thicknessIn, 0, "weld thickness");
        List<ClauseVerdict> verdicts = new ArrayList<>();
        for (Indication indication : indications) {
            verdicts.add(judgeElongated(indication, thicknessIn));
        }
        verdicts.addAll(judgeGroups(
                indications.stream().filter(indication -> !indication.planar()).toList(), thicknessIn));
        verdicts.addAll(judgeRounded(indications, thicknessIn));
        verdicts.addAll(judgeRoundedCount(indications, thicknessIn, weldLengthIn));
        return Common.settle(verdicts, CODE, CLAUSE);
    }
}
