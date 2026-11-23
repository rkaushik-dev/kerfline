package io.github.rkaushikdev.kerfline.accept;

import io.github.rkaushikdev.kerfline.indication.Indication;
import io.github.rkaushikdev.kerfline.indication.IndicationType;
import io.github.rkaushikdev.kerfline.indication.Measure;
import io.github.rkaushikdev.kerfline.indication.WeldWindow;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.ToDoubleFunction;

/**
 * The API 1104 rules that read the weld as a whole rather than one indication at a time: the caps
 * on how much of each imperfection any twelve inches may carry and how much the weld may carry in
 * all, the isolated slag count, the spacing of long hollow beads, and the accumulation of every
 * kind together.
 *
 * <p>Every one of them is read round the joint, because a girth weld has no ends. And every finding
 * about a foot of weld says where that foot starts, measured from the tie-in, because the crew who
 * grind it out need to know where to put the disc rather than only that the joint failed. When none
 * of the indications was located the total for the whole weld stands in for the window, and there
 * is no start to give.
 */
public final class Api1104Weld {

    private static final double ISOLATED_WINDOW_IN = 0.5;
    private static final int ISOLATED_COUNT = 4;
    private static final double HOLLOW_BEAD_SPACED_IN = 0.25;
    private static final double HOLLOW_BEAD_GAP_IN = 2;
    private static final String ACCUMULATION_CLAUSE = "9.3.10";
    private static final double ACCUMULATION_WINDOW_IN = 2;

    private Api1104Weld() {}

    /** The heaviest twelve inches round the joint, or the whole weld when nothing was located. */
    private record Foot(double totalIn, OptionalDouble startIn) {}

    private static Foot worstFoot(
            List<Indication> indications, double weldLengthIn, ToDoubleFunction<Indication> sizeOf) {
        WeldWindow window = Measure.worstWindowRound(indications, Api1104.windowLengthIn(), weldLengthIn, sizeOf);
        if (window.totalIn() > 0) {
            return new Foot(window.totalIn(), OptionalDouble.of(window.startIn()));
        }
        double sum = 0;
        for (Indication indication : indications) {
            sum += sizeOf.applyAsDouble(indication);
        }
        return new Foot(Num.round(sum, 4), OptionalDouble.empty());
    }

    /** The aggregate caps of one length rule, over the indications judged under it and no others. */
    public static List<ClauseVerdict> judgeAggregate(
            List<Indication> indications, Api1104Rule rule, double weldLengthIn) {
        Guard.requireAbove(weldLengthIn, 0, "weld length");
        Guard.requireArray(indications, "indications");
        List<Indication> ofRule = indications.stream()
                .filter(indication ->
                        Api1104.ruleOf(indication).filter(rule::equals).isPresent())
                .toList();
        if (ofRule.isEmpty()) {
            return List.of();
        }
        double total = Measure.cumulativeLengthIn(ofRule);
        Foot foot = worstFoot(ofRule, weldLengthIn, Measure::countedLengthIn);
        double window = Api1104.windowLengthIn();

        List<ClauseVerdict> verdicts = new ArrayList<>();
        if (foot.totalIn() > rule.windowIn()) {
            verdicts.add(inAFoot(rule.clause(), rule.label(), foot, rule.windowIn()));
        }
        if (rule.percent().isPresent() && weldLengthIn < window) {
            double percent = rule.percent().getAsDouble();
            if (total > Num.round(weldLengthIn * percent, 4)) {
                verdicts.add(totals(
                        rule.clause(),
                        rule.label(),
                        total,
                        ", over " + Num.text(percent * 100) + " percent of a weld under " + Num.text(window) + " in"));
            }
        }
        if (rule.wholeWeldFraction().isPresent()) {
            double cap = Num.round(weldLengthIn * rule.wholeWeldFraction().getAsDouble(), 4);
            if (total > cap) {
                verdicts.add(totals(
                        rule.clause(),
                        rule.label(),
                        total,
                        ", over " + Num.text(cap) + " in allowed in a " + Num.text(Num.round(weldLengthIn, 3))
                                + " in weld"));
            }
        }
        return List.copyOf(verdicts);
    }

    /**
     * Isolated slag in any twelve inches, read round the joint: no more than half an inch of it in
     * all, and no more than four inclusions of it.
     */
    public static List<ClauseVerdict> judgeIsolatedSlag(List<Indication> indications, double weldLengthIn) {
        Guard.requireArray(indications, "indications");
        Guard.requireAbove(weldLengthIn, 0, "weld length");
        List<Indication> isolated =
                indications.stream().filter(Api1104::isIsolatedSlag).toList();
        if (isolated.isEmpty()) {
            return List.of();
        }
        String clause = Api1104.ruleFor(IndicationType.SLAG).orElseThrow().clause();
        Foot length = worstFoot(isolated, weldLengthIn, Measure::countedLengthIn);
        Foot count = worstFoot(isolated, weldLengthIn, indication -> 1);

        List<ClauseVerdict> verdicts = new ArrayList<>();
        if (length.totalIn() > ISOLATED_WINDOW_IN) {
            verdicts.add(inAFoot(clause, "isolated slag", length, ISOLATED_WINDOW_IN));
        }
        if (count.totalIn() > ISOLATED_COUNT) {
            verdicts.add(Verdict.makeVerdict(
                    Disposition.REPAIR,
                    Api1104.codeName(),
                    clause,
                    Num.text(count.totalIn()) + " isolated slag inclusions in "
                            + Num.text(Api1104.windowLengthIn()) + " in" + startingAt(count) + ", cap is "
                            + ISOLATED_COUNT,
                    count.totalIn()));
        }
        return List.copyOf(verdicts);
    }

    /**
     * Hollow bead over a quarter inch long is allowed only with two clear inches to the next one, so
     * two acceptable lengths of it cannot sit end to end in the root. Read round the joint, so the last
     * one before the tie-in is measured to the first one after it.
     */
    public static List<ClauseVerdict> judgeHollowBeadSpacing(List<Indication> indications, double weldLengthIn) {
        Guard.requireAbove(weldLengthIn, 0, "weld length");
        List<Indication> spaced = Measure.alongTheWeld(indications).stream()
                .filter(indication -> indication.type() == IndicationType.HOLLOW_BEAD)
                .filter(indication -> Measure.governingSizeIn(indication) > HOLLOW_BEAD_SPACED_IN)
                .toList();
        if (spaced.size() < 2) {
            return List.of();
        }
        String clause =
                Api1104.ruleFor(IndicationType.HOLLOW_BEAD).orElseThrow().clause();
        List<ClauseVerdict> verdicts = new ArrayList<>();
        for (int i = 0; i < spaced.size(); i++) {
            Indication before = spaced.get(i);
            boolean roundTheBack = i == spaced.size() - 1;
            Indication after = spaced.get(roundTheBack ? 0 : i + 1);
            double gap = Measure.gapIn(before, after) + (roundTheBack ? weldLengthIn : 0);
            if (gap < HOLLOW_BEAD_GAP_IN) {
                double clear = Num.round(Math.max(0, gap), 4);
                verdicts.add(Verdict.makeVerdict(
                        Disposition.REPAIR,
                        Api1104.codeName(),
                        clause,
                        "hollow bead over " + Num.text(HOLLOW_BEAD_SPACED_IN) + " in long with " + Num.text(clear)
                                + " in to the next, " + Num.text(HOLLOW_BEAD_GAP_IN) + " in is needed",
                        before,
                        clear));
            }
        }
        return List.copyOf(verdicts);
    }

    /**
     * True for what the accumulation rule adds up: every imperfection except high-low penetration and
     * undercut, which the code leaves to their own caps because neither is a void in the weld metal.
     */
    public static boolean countsTowardAccumulation(Indication indication) {
        return !Api1104.isHighLow(indication) && indication.type() != IndicationType.UNDERCUT;
    }

    /**
     * The accumulation rule. Each kind of imperfection can sit inside its own caps and the joint can
     * still be full of them, so the code adds every kind together: no more than two inches in any
     * foot of weld, and no more than eight percent of the weld, whatever its length.
     */
    public static List<ClauseVerdict> judgeAccumulation(List<Indication> indications, double weldLengthIn) {
        Guard.requireArray(indications, "indications");
        Guard.requireAbove(weldLengthIn, 0, "weld length");
        List<Indication> counted = indications.stream()
                .filter(Api1104Weld::countsTowardAccumulation)
                .toList();
        if (counted.isEmpty()) {
            return List.of();
        }
        double sum = 0;
        for (Indication indication : counted) {
            sum += Measure.governingSizeIn(indication);
        }
        double total = Num.round(sum, 4);
        Foot foot = worstFoot(counted, weldLengthIn, Measure::governingSizeIn);

        List<ClauseVerdict> verdicts = new ArrayList<>();
        if (foot.totalIn() > ACCUMULATION_WINDOW_IN) {
            verdicts.add(inAFoot(ACCUMULATION_CLAUSE, "imperfections", foot, ACCUMULATION_WINDOW_IN));
        }
        double percent = Api1104.shortWeldPercent();
        if (total > Num.round(weldLengthIn * percent, 4)) {
            verdicts.add(totals(
                    ACCUMULATION_CLAUSE,
                    "imperfections",
                    total,
                    ", over " + Num.text(percent * 100) + " percent of the weld"));
        }
        return List.copyOf(verdicts);
    }

    private static String startingAt(Foot foot) {
        return foot.startIn().isPresent() ? " from " + Num.text(foot.startIn().getAsDouble()) + " in" : "";
    }

    private static ClauseVerdict inAFoot(String clause, String what, Foot foot, double capIn) {
        return totals(
                clause,
                what,
                foot.totalIn(),
                " in " + Num.text(Api1104.windowLengthIn()) + " in" + startingAt(foot) + ", cap is " + Num.text(capIn)
                        + " in");
    }

    private static ClauseVerdict totals(String clause, String what, double totalIn, String against) {
        return Verdict.makeVerdict(
                Disposition.REPAIR,
                Api1104.codeName(),
                clause,
                what + " totals " + Num.text(Num.round(totalIn, 3)) + " in" + against,
                totalIn);
    }
}
