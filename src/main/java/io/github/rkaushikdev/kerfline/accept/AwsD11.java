package io.github.rkaushikdev.kerfline.accept;

import io.github.rkaushikdev.kerfline.indication.Indication;
import io.github.rkaushikdev.kerfline.indication.Measure;
import io.github.rkaushikdev.kerfline.indication.WeldWindow;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AWS D1.1 for structural welds.
 *
 * <p>Three rules stack and they catch different things.
 *
 * <p>The size rule caps any single discontinuity, scaled on the weld thickness and floored so that
 * thin material is not held to something unmeasurable.
 *
 * <p>The sum rule catches a scatter of small ones. Anything under an eighth of an inch is
 * individually acceptable at any size the size rule allows, so without this rule a weld could be
 * full of them. The sum is read over one linear inch, which is a much tighter window than either
 * pressure code uses.
 *
 * <p>The clearance rule catches two acceptable discontinuities sitting on top of each other. Metal
 * between two voids is doing less work than metal beside one, so the code wants a gap proportional
 * to the larger of the pair.
 *
 * <p>Cyclically loaded work is judged on the same three rules with everything roughly halved,
 * because a discontinuity in a member that flexes is a crack starter rather than a void.
 */
public final class AwsD11 {

    private static final String CODE = "AWSD1.1";
    private static final String DEFAULT_LOADING = "static";
    private static final List<String> LOADINGS = List.of("static", "cyclic");

    private static final Map<String, AwsD11Rules> RULES = Map.of(
            "static", new AwsD11Rules("6.12.1", 1.0 / 3, 0.125, 0.75, 0.125, 0.375, 1, 3),
            "cyclic", new AwsD11Rules("6.12.2", 1.0 / 6, 0.0625, 0.375, 0.0625, 0.1875, 1, 6));

    private AwsD11() {}

    public static String codeName() {
        return CODE;
    }

    public static List<String> loadings() {
        return LOADINGS;
    }

    public static AwsD11Rules rulesFor(String loading) {
        Guard.requireOneOf(loading, LOADINGS, "loading");
        return RULES.get(loading);
    }

    public static double maxDiscontinuityIn(double thicknessIn) {
        return maxDiscontinuityIn(thicknessIn, DEFAULT_LOADING);
    }

    /** The cap on any single discontinuity, scaled on the weld and bracketed by the floor and ceiling. */
    public static double maxDiscontinuityIn(double thicknessIn, String loading) {
        Guard.requireAbove(thicknessIn, 0, "weld thickness");
        AwsD11Rules rules = rulesFor(loading);
        double scaled = thicknessIn * rules.fraction();
        return Num.round(Math.min(rules.ceilingIn(), Math.max(rules.floorIn(), scaled)), 4);
    }

    public static double requiredClearanceIn(double largestIn) {
        return requiredClearanceIn(largestIn, DEFAULT_LOADING);
    }

    /** The clearance a discontinuity needs from the next one along. */
    public static double requiredClearanceIn(double largestIn, String loading) {
        Guard.requireAbove(largestIn, 0, "discontinuity size");
        return Num.round(largestIn * rulesFor(loading).clearanceMultiple(), 4);
    }

    public static ClauseVerdict judgeOne(Indication indication, double thicknessIn) {
        return judgeOne(indication, thicknessIn, DEFAULT_LOADING);
    }

    /** One discontinuity against the size rule; a planar one is rejected outright. */
    public static ClauseVerdict judgeOne(Indication indication, double thicknessIn, String loading) {
        AwsD11Rules rules = rulesFor(loading);
        if (indication.planar()) {
            return Common.rejectPlanar(CODE, rules.clause(), indication);
        }
        double size = Measure.governingSizeIn(indication);
        double cap = maxDiscontinuityIn(thicknessIn, loading);
        if (size <= cap) {
            return Verdict.accept(CODE, rules.clause());
        }
        return Verdict.makeVerdict(
                Disposition.REPAIR,
                CODE,
                rules.clause(),
                indication.label() + " " + Num.text(Num.round(size, 4)) + " in, cap is " + Num.text(cap) + " in",
                indication,
                size);
    }

    public static List<ClauseVerdict> judgeSmallSum(List<Indication> indications) {
        return judgeSmallSum(indications, DEFAULT_LOADING);
    }

    /**
     * The sum rule over a sliding inch. Only the small discontinuities count towards it; a large one
     * has already been judged on its own size and counting it twice would be judging the same metal
     * twice.
     */
    public static List<ClauseVerdict> judgeSmallSum(List<Indication> indications, String loading) {
        Guard.requireArray(indications, "indications");
        AwsD11Rules rules = rulesFor(loading);
        List<Indication> small = indications.stream()
                .filter(indication -> !indication.planar())
                .filter(indication -> indication.at().isPresent())
                .filter(indication -> Measure.governingSizeIn(indication) < rules.smallIn())
                .toList();
        if (small.isEmpty()) {
            return List.of();
        }

        WeldWindow worst = Common.windowTotal(small, rules.windowIn());
        if (worst.totalIn() <= rules.sumIn()) {
            return List.of();
        }
        return List.of(Verdict.makeVerdict(
                Disposition.REPAIR,
                CODE,
                rules.clause(),
                "small discontinuities total " + Num.text(worst.totalIn()) + " in in one inch of weld, cap is "
                        + Num.text(rules.sumIn()) + " in",
                worst.totalIn()));
    }

    public static List<ClauseVerdict> judgeClearance(List<Indication> indications) {
        return judgeClearance(indications, DEFAULT_LOADING);
    }

    /** The clearance rule between neighbouring discontinuities, in order along the weld. */
    public static List<ClauseVerdict> judgeClearance(List<Indication> indications, String loading) {
        List<Indication> placed = Measure.alongTheWeld(indications).stream()
                .filter(indication -> !indication.planar())
                .toList();

        List<ClauseVerdict> verdicts = new ArrayList<>();
        for (int i = 1; i < placed.size(); i++) {
            Indication previous = placed.get(i - 1);
            Indication current = placed.get(i);
            double gap = Measure.gapIn(previous, current);
            double largest = Math.max(Measure.governingSizeIn(previous), Measure.governingSizeIn(current));
            double wanted = requiredClearanceIn(largest, loading);
            if (gap < wanted) {
                double clearance = Num.round(Math.max(0, gap), 4);
                verdicts.add(Verdict.makeVerdict(
                        Disposition.REPAIR,
                        CODE,
                        rulesFor(loading).clause(),
                        Num.text(clearance) + " in clearance where " + Num.text(wanted) + " in is needed",
                        clearance));
            }
        }
        return List.copyOf(verdicts);
    }

    /** Judge a whole joint under static loading. */
    public static JointVerdict judgeJoint(List<Indication> indications, double thicknessIn) {
        return judgeJoint(indications, thicknessIn, DEFAULT_LOADING);
    }

    /** Judge a whole joint: each discontinuity on its size, then the sum rule, then the clearances. */
    public static JointVerdict judgeJoint(List<Indication> indications, double thicknessIn, String loading) {
        Guard.requireArray(indications, "indications");
        Guard.requireAbove(thicknessIn, 0, "weld thickness");
        Guard.requireOneOf(loading, LOADINGS, "loading");
        List<ClauseVerdict> verdicts = new ArrayList<>();
        for (Indication indication : indications) {
            verdicts.add(judgeOne(indication, thicknessIn, loading));
        }
        verdicts.addAll(judgeSmallSum(indications, loading));
        verdicts.addAll(judgeClearance(indications, loading));
        return Common.settle(verdicts, CODE, rulesFor(loading).clause());
    }
}
