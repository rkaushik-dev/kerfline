package io.github.rkaushikdev.kerfline.accept;

import io.github.rkaushikdev.kerfline.indication.Indication;
import io.github.rkaushikdev.kerfline.indication.IndicationType;
import io.github.rkaushikdev.kerfline.indication.Measure;
import io.github.rkaushikdev.kerfline.indication.WeldWindow;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * API 1104 acceptance, for girth welds in pipeline service.
 *
 * <p>The shape of this code is worth understanding before reading the numbers. Almost every defect
 * is capped three ways at once: the length of any single one, the total in any twelve inches of
 * weld, and a percentage of the whole weld length. A joint can pass the first two and fail the
 * third, and on a small diameter joint the third is the one that bites, because twelve inches is
 * most of the way round a two inch pipe.
 *
 * <p>The percentage cap is what makes this code different from the pressure vessel ones, and it is
 * why the weld length is a required argument here rather than an optional extra.
 */
public final class Api1104 {

    private static final String CODE = "API1104";
    private static final double WINDOW_IN = 12;
    private static final double PERCENT_CAP = 0.08;

    /** In the order the code lists them, which is the order aggregate findings are reported in. */
    private static final Map<IndicationType, Api1104Rule> RULES = rules();

    private static final double SLAG_WIDTH_IN = 0.0625;
    private static final double PORE_IN = 0.125;
    private static final double PORE_WALL_FRACTION = 0.25;
    private static final double CLUSTER_IN = 0.5;
    private static final double CRATER_CRACK_IN = 5.0 / 32;

    private Api1104() {}

    private static Map<IndicationType, Api1104Rule> rules() {
        OptionalDouble percent = OptionalDouble.of(PERCENT_CAP);
        OptionalDouble none = OptionalDouble.empty();
        Map<IndicationType, Api1104Rule> rules = new LinkedHashMap<>();
        rules.put(IndicationType.IP, new Api1104Rule("9.3.2", 1, 1, percent, "incomplete penetration"));
        rules.put(IndicationType.IF, new Api1104Rule("9.3.4", 1, 1, percent, "incomplete fusion"));
        rules.put(IndicationType.SLAG, new Api1104Rule("9.3.6", 2, 2, none, "slag inclusion"));
        rules.put(IndicationType.HOLLOW_BEAD, new Api1104Rule("9.3.7", 0.5, 2, none, "hollow bead"));
        rules.put(IndicationType.BURN_THROUGH, new Api1104Rule("9.3.5", 0.25, 0.5, none, "burn through"));
        rules.put(IndicationType.UNDERCUT, new Api1104Rule("9.3.8", 2, 2, percent, "undercut"));
        return Collections.unmodifiableMap(rules);
    }

    public static String codeName() {
        return CODE;
    }

    public static double windowLengthIn() {
        return WINDOW_IN;
    }

    /** The length rule for a type, or empty for a type this code has no length rule for. */
    public static Optional<Api1104Rule> ruleFor(IndicationType type) {
        return Optional.ofNullable(RULES.get(type));
    }

    /**
     * A crack comes out. The only survivor is a shallow crater or star crack under five thirty
     * seconds of an inch, and even that is only tolerated because grinding it out costs more than it
     * is worth.
     */
    public static ClauseVerdict judgeCrack(Indication indication) {
        double size = Measure.governingSizeIn(indication);
        boolean isCrater = indication
                .note()
                .filter(note -> note.equals("crater") || note.equals("star"))
                .isPresent();
        if (isCrater && size < CRATER_CRACK_IN) {
            return Verdict.accept(CODE, "9.3.9");
        }
        return Verdict.makeVerdict(
                Disposition.REJECT,
                CODE,
                "9.3.9",
                "crack " + Num.text(Num.round(size, 3)) + " in long",
                indication,
                size);
    }

    /** A single pore is capped both absolutely and against the wall it is in. */
    public static ClauseVerdict judgePorosity(Indication indication, double wallIn) {
        Guard.requireAbove(wallIn, 0, "wall thickness");
        double size = Measure.governingSizeIn(indication);
        double wallCap = Num.round(wallIn * PORE_WALL_FRACTION, 4);
        double cap = Math.min(PORE_IN, wallCap);
        if (size <= cap) {
            return Verdict.accept(CODE, "9.3.3");
        }
        return Verdict.makeVerdict(
                Disposition.REPAIR,
                CODE,
                "9.3.3",
                "pore " + Num.text(Num.round(size, 3)) + " in across, cap is " + Num.text(cap) + " in",
                indication,
                size);
    }

    public static ClauseVerdict judgeCluster(Indication indication) {
        double size = Measure.governingSizeIn(indication);
        if (size <= CLUSTER_IN) {
            return Verdict.accept(CODE, "9.3.3");
        }
        return Verdict.makeVerdict(
                Disposition.REPAIR,
                CODE,
                "9.3.3",
                "cluster " + Num.text(Num.round(size, 3)) + " in across, cap is " + Num.text(CLUSTER_IN) + " in",
                indication,
                size);
    }

    /** Slag is capped on length and, separately and first, on width. */
    public static ClauseVerdict judgeSlag(Indication indication) {
        Api1104Rule rule = RULES.get(IndicationType.SLAG);
        double size = Measure.governingSizeIn(indication);
        if (indication.widthIn().isPresent() && indication.widthIn().getAsDouble() > SLAG_WIDTH_IN) {
            double width = indication.widthIn().getAsDouble();
            return Verdict.makeVerdict(
                    Disposition.REPAIR,
                    CODE,
                    rule.clause(),
                    "slag " + Num.text(Num.round(width, 4)) + " in wide, cap is " + Num.text(SLAG_WIDTH_IN) + " in",
                    indication,
                    width);
        }
        if (size <= rule.singleIn()) {
            return Verdict.accept(CODE, rule.clause());
        }
        return Verdict.makeVerdict(
                Disposition.REPAIR,
                CODE,
                rule.clause(),
                "slag " + Num.text(Num.round(size, 3)) + " in long, cap is " + Num.text(rule.singleIn()) + " in",
                indication,
                size);
    }

    /** Everything covered by a plain single length cap; a type with no rule is accepted. */
    public static ClauseVerdict judgeByLength(Indication indication) {
        Api1104Rule rule = RULES.get(indication.type());
        if (rule == null) {
            return Verdict.accept(CODE, "9.3");
        }
        double size = Measure.governingSizeIn(indication);
        if (size <= rule.singleIn()) {
            return Verdict.accept(CODE, rule.clause());
        }
        return Verdict.makeVerdict(
                Disposition.REPAIR,
                CODE,
                rule.clause(),
                rule.label() + " " + Num.text(Num.round(size, 3)) + " in long, cap is " + Num.text(rule.singleIn())
                        + " in",
                indication,
                size);
    }

    /** One indication, sent to the rule its type is judged under. */
    public static ClauseVerdict judgeOne(Indication indication, double wallIn) {
        return switch (indication.type()) {
            case CRACK -> judgeCrack(indication);
            case POROSITY -> judgePorosity(indication, wallIn);
            case CLUSTER -> judgeCluster(indication);
            case SLAG -> judgeSlag(indication);
            default -> judgeByLength(indication);
        };
    }

    /**
     * The two caps that are not about any single indication: the total in any twelve inches, and the
     * total as a fraction of the whole weld. A girth weld goes all the way round, so the twelve inches
     * are read round the joint and carry on past the tie-in rather than stopping at it.
     */
    public static List<ClauseVerdict> judgeAggregate(
            List<Indication> indications, IndicationType type, double weldLengthIn) {
        Api1104Rule rule = RULES.get(type);
        Guard.requireAbove(weldLengthIn, 0, "weld length");
        Guard.requireArray(indications, "indications");
        List<Indication> ofType = indications.stream()
                .filter(indication -> indication.type() == type)
                .toList();
        if (rule == null || ofType.isEmpty()) {
            return List.of();
        }

        List<ClauseVerdict> verdicts = new ArrayList<>();
        WeldWindow window = Measure.worstWindowRound(ofType, WINDOW_IN, weldLengthIn);
        double total = Measure.cumulativeLengthIn(ofType);
        double inWindow = window.totalIn() > 0 ? window.totalIn() : total;

        if (inWindow > rule.windowIn()) {
            verdicts.add(Verdict.makeVerdict(
                    Disposition.REPAIR,
                    CODE,
                    rule.clause(),
                    rule.label() + " totals " + Num.text(Num.round(inWindow, 3)) + " in in " + Num.text(WINDOW_IN)
                            + " in, cap is " + Num.text(rule.windowIn()) + " in",
                    inWindow));
        }

        if (rule.percent().isPresent()) {
            double percent = rule.percent().getAsDouble();
            double cap = Num.round(weldLengthIn * percent, 4);
            if (total > cap) {
                verdicts.add(Verdict.makeVerdict(
                        Disposition.REPAIR,
                        CODE,
                        rule.clause(),
                        rule.label() + " totals " + Num.text(Num.round(total, 3)) + " in, over "
                                + Num.text(percent * 100) + " percent of the weld",
                        total));
            }
        }

        return List.copyOf(verdicts);
    }

    /** Judge a whole joint against the code: each indication, then the aggregates by type. */
    public static JointVerdict judgeJoint(List<Indication> indications, double wallIn, double weldLengthIn) {
        Guard.requireArray(indications, "indications");
        Guard.requireAbove(wallIn, 0, "wall thickness");
        Guard.requireAbove(weldLengthIn, 0, "weld length");

        List<ClauseVerdict> verdicts = new ArrayList<>();
        for (Indication indication : indications) {
            verdicts.add(judgeOne(indication, wallIn));
        }
        for (IndicationType type : RULES.keySet()) {
            verdicts.addAll(judgeAggregate(indications, type, weldLengthIn));
        }
        return Common.settle(verdicts, CODE, "9.3");
    }
}
