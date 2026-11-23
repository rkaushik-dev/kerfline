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
        double limit = elongatedLimitIn(thicknessIn);
        double size = Measure.governingSizeIn(indication);
        if (size <= limit) {
            return Verdict.accept(CODE, CLAUSE + "(2)");
        }
        return Verdict.makeVerdict(
                Disposition.REPAIR,
                CODE,
                CLAUSE + "(2)",
                indication.label() + " " + Num.text(Num.round(size, 3)) + " in long, cap is " + Num.text(limit) + " in",
                indication,
                size);
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

    /** Judge a whole joint: each indication on its own, then the aligned groups of the volumetric ones. */
    public static JointVerdict judgeJoint(List<Indication> indications, double thicknessIn) {
        Guard.requireArray(indications, "indications");
        Guard.requireAbove(thicknessIn, 0, "weld thickness");
        List<ClauseVerdict> verdicts = new ArrayList<>();
        for (Indication indication : indications) {
            verdicts.add(judgeElongated(indication, thicknessIn));
        }
        verdicts.addAll(judgeGroups(
                indications.stream().filter(indication -> !indication.planar()).toList(), thicknessIn));
        return Common.settle(verdicts, CODE, CLAUSE);
    }
}
