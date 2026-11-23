package io.github.rkaushikdev.kerfline.accept;

import io.github.rkaushikdev.kerfline.indication.Indication;
import io.github.rkaushikdev.kerfline.indication.IndicationType;
import io.github.rkaushikdev.kerfline.indication.Measure;
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
 * weld, and against the weld length itself. On a weld under twelve inches long, which is anything
 * on two inch pipe, the twelve inch window is the whole weld, and the code adds an eight percent
 * ceiling for exactly that case. Undercut is capped at a sixth of the weld whatever its length.
 *
 * <p>The caps on weld length are what make this code different from the pressure vessel ones, and
 * they are why the weld length is a required argument here rather than an optional extra. This class
 * judges each indication on its own; the rules that read the weld as a whole are in {@link
 * Api1104Weld}.
 */
public final class Api1104 {

    private static final String CODE = "API1104";
    private static final double WINDOW_IN = 12;
    private static final double PERCENT_CAP = 0.08;
    private static final double UNDERCUT_FRACTION = 1.0 / 6;

    /** In the order the code lists them, which is the order aggregate findings are reported in. */
    private static final Map<IndicationType, Api1104Rule> RULES = rules();

    private static final double SLAG_WIDTH_IN = 0.0625;
    private static final String ISOLATED = "isolated";
    private static final String HIGH_LOW_NOTE = "high-low";
    private static final Api1104Rule HIGH_LOW = new Api1104Rule(
            "9.3.2", 2, 3, OptionalDouble.empty(), OptionalDouble.empty(), "incomplete penetration due to high-low");
    private static final double ISOLATED_WIDTH_IN = 0.125;
    private static final double PORE_IN = 0.125;
    private static final double PORE_WALL_FRACTION = 0.25;
    private static final double CLUSTER_IN = 0.5;
    private static final double CRATER_CRACK_IN = 5.0 / 32;
    private static final String CRACK_CLAUSE = "9.3.9";

    private Api1104() {}

    private static Map<IndicationType, Api1104Rule> rules() {
        OptionalDouble shortWeld = OptionalDouble.of(PERCENT_CAP);
        OptionalDouble none = OptionalDouble.empty();
        Map<IndicationType, Api1104Rule> rules = new LinkedHashMap<>();
        rules.put(IndicationType.IP, new Api1104Rule("9.3.2", 1, 1, shortWeld, none, "incomplete penetration"));
        rules.put(IndicationType.IF, new Api1104Rule("9.3.4", 1, 1, shortWeld, none, "incomplete fusion"));
        rules.put(IndicationType.SLAG, new Api1104Rule("9.3.6", 2, 2, none, none, "slag inclusion"));
        rules.put(
                IndicationType.HOLLOW_BEAD,
                new Api1104Rule("9.3.7", 0.5, 2, none, OptionalDouble.of(PERCENT_CAP), "hollow bead"));
        rules.put(IndicationType.BURN_THROUGH, new Api1104Rule("9.3.5", 0.25, 0.5, none, none, "burn through"));
        rules.put(
                IndicationType.UNDERCUT,
                new Api1104Rule("9.3.8", 2, 2, none, OptionalDouble.of(UNDERCUT_FRACTION), "undercut"));
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
        return judgeCrack(indication, Double.POSITIVE_INFINITY, false);
    }

    /**
     * A crack under a company that authorises crack repairs. The weld still fails, but a crack shorter
     * than eight percent of the weld may be ground out and rewelded instead of the joint being cut
     * out. Without that authority, or at eight percent and over, the joint comes out.
     */
    public static ClauseVerdict judgeCrack(Indication indication, double weldLengthIn, boolean repairAuthorised) {
        double size = Num.round(Measure.governingSizeIn(indication), 3);
        boolean isCrater = indication
                .note()
                .filter(note -> note.equals("crater") || note.equals("star"))
                .isPresent();
        if (isCrater && size < CRATER_CRACK_IN) {
            return Verdict.accept(CODE, CRACK_CLAUSE);
        }
        String reason = "crack " + Num.text(size) + " in long";
        if (repairAuthorised) {
            Guard.requireAbove(weldLengthIn, 0, "weld length");
            double limit = Num.round(weldLengthIn * PERCENT_CAP, 4);
            if (size < limit) {
                return Verdict.makeVerdict(
                        Disposition.REPAIR,
                        CODE,
                        CRACK_CLAUSE,
                        reason + ", repairable on the company's authority under " + Num.text(limit) + " in",
                        indication,
                        size);
            }
        }
        return Verdict.makeVerdict(Disposition.REJECT, CODE, CRACK_CLAUSE, reason, indication, size);
    }

    /** A single pore is capped both absolutely and against the wall it is in. */
    public static ClauseVerdict judgePorosity(Indication indication, double wallIn) {
        Guard.requireAbove(wallIn, 0, "wall thickness");
        double cap = Math.min(PORE_IN, Num.round(wallIn * PORE_WALL_FRACTION, 4));
        return Common.capVerdict(across(indication, "pore", cap));
    }

    public static ClauseVerdict judgeCluster(Indication indication) {
        return Common.capVerdict(across(indication, "cluster", CLUSTER_IN));
    }

    private static CapCheck across(Indication indication, String what, double capIn) {
        return CapCheck.of(CODE, "9.3.3", what)
                .size(Measure.governingSizeIn(indication))
                .cap(capIn)
                .units("in across")
                .indication(indication);
    }

    /** True for slag the reader marked {@code isolated}; any other slag is an elongated line. */
    public static boolean isIsolatedSlag(Indication indication) {
        return indication.type() == IndicationType.SLAG
                && indication.note().filter(ISOLATED::equals).isPresent();
    }

    /**
     * Slag is capped on width first and then on length. An elongated line is held to a sixteenth wide
     * and two inches long. An isolated inclusion is allowed twice the width and has no length cap of
     * its own, because the code holds isolated slag on how much of it a foot of weld carries instead.
     */
    public static ClauseVerdict judgeSlag(Indication indication) {
        Api1104Rule rule = RULES.get(IndicationType.SLAG);
        boolean isolated = isIsolatedSlag(indication);
        CapCheck check = CapCheck.of(CODE, rule.clause(), isolated ? "isolated slag" : "slag")
                .indication(indication);
        if (indication.widthIn().isPresent()) {
            ClauseVerdict byWidth =
                    Common.capVerdict(check.size(indication.widthIn().getAsDouble())
                            .cap(isolated ? ISOLATED_WIDTH_IN : SLAG_WIDTH_IN)
                            .units("in wide"));
            if (isolated || !Verdict.isAcceptable(byWidth)) {
                return byWidth;
            }
        }
        if (isolated) {
            return Verdict.accept(CODE, rule.clause());
        }
        return Common.capVerdict(check.size(Measure.governingSizeIn(indication))
                .cap(rule.singleIn())
                .units("in long"));
    }

    /**
     * True for incomplete penetration the reader put down to high-low: the root was there, but one
     * pipe end sat proud of the other and the weld could not reach both lands.
     */
    public static boolean isHighLow(Indication indication) {
        return indication.type() == IndicationType.IP
                && indication.note().filter(HIGH_LOW_NOTE::equals).isPresent();
    }

    /**
     * The length rule an indication is judged under, read from its type and from what the reader
     * noted: incomplete penetration put down to high-low has caps of its own, and isolated slag is
     * held by {@link Api1104Weld#judgeIsolatedSlag} rather than by a length rule.
     */
    public static Optional<Api1104Rule> ruleOf(Indication indication) {
        if (isHighLow(indication)) {
            return Optional.of(HIGH_LOW);
        }
        if (isIsolatedSlag(indication)) {
            return Optional.empty();
        }
        return ruleFor(indication.type());
    }

    /** Every length rule the aggregate caps are read under, in the order findings are reported. */
    public static List<Api1104Rule> lengthRules() {
        List<Api1104Rule> all = new ArrayList<>(RULES.values());
        all.add(1, HIGH_LOW);
        return List.copyOf(all);
    }

    /** Everything covered by a plain single length cap; an indication with no rule is accepted. */
    public static ClauseVerdict judgeByLength(Indication indication) {
        Optional<Api1104Rule> rule = ruleOf(indication);
        if (rule.isEmpty()) {
            return Verdict.accept(CODE, "9.3");
        }
        return Common.capVerdict(
                CapCheck.of(CODE, rule.get().clause(), rule.get().label())
                        .size(Measure.governingSizeIn(indication))
                        .cap(rule.get().singleIn())
                        .units("in long")
                        .indication(indication));
    }

    /**
     * Burn through is held to a quarter inch across, and never deeper into the joint than the wall
     * it went through: on thin wall a quarter inch hole is most of the pipe.
     */
    public static ClauseVerdict judgeBurnThrough(Indication indication, double wallIn) {
        Guard.requireAbove(wallIn, 0, "wall thickness");
        Api1104Rule rule = RULES.get(IndicationType.BURN_THROUGH);
        double cap = Math.min(rule.singleIn(), Num.round(wallIn, 4));
        return Common.capVerdict(CapCheck.of(CODE, rule.clause(), rule.label())
                .size(Measure.governingSizeIn(indication))
                .cap(cap)
                .units("in across")
                .indication(indication));
    }

    /** One indication, sent to the rule its type is judged under, with no crack repairs authorised. */
    public static ClauseVerdict judgeOne(Indication indication, double wallIn) {
        return judgeOne(indication, wallIn, Double.POSITIVE_INFINITY, false);
    }

    /** One indication, on a weld whose length matters to a crack the company lets be repaired. */
    public static ClauseVerdict judgeOne(
            Indication indication, double wallIn, double weldLengthIn, boolean crackRepairs) {
        return switch (indication.type()) {
            case CRACK -> judgeCrack(indication, weldLengthIn, crackRepairs);
            case POROSITY -> judgePorosity(indication, wallIn);
            case CLUSTER -> judgeCluster(indication);
            case SLAG -> judgeSlag(indication);
            case BURN_THROUGH -> judgeBurnThrough(indication, wallIn);
            default -> judgeByLength(indication);
        };
    }

    /**
     * The caps on one type that are not about any single indication: the total in any twelve inches,
     * and the total against the weld length, read by {@link Api1104Weld}.
     */
    public static List<ClauseVerdict> judgeAggregate(
            List<Indication> indications, IndicationType type, double weldLengthIn) {
        Api1104Rule rule = RULES.get(type);
        return rule == null ? List.of() : Api1104Weld.judgeAggregate(indications, rule, weldLengthIn);
    }

    /** The share of a weld under twelve inches that the code's eight percent caps allow. */
    public static double shortWeldPercent() {
        return PERCENT_CAP;
    }

    /** Judge a whole joint against the code with no crack repairs authorised. */
    public static JointVerdict judgeJoint(List<Indication> indications, double wallIn, double weldLengthIn) {
        return judgeJoint(indications, wallIn, weldLengthIn, false);
    }

    /** Judge a whole joint against the code: each indication, then the aggregates rule by rule. */
    public static JointVerdict judgeJoint(
            List<Indication> indications, double wallIn, double weldLengthIn, boolean crackRepairs) {
        Guard.requireArray(indications, "indications");
        Guard.requireAbove(wallIn, 0, "wall thickness");
        Guard.requireAbove(weldLengthIn, 0, "weld length");

        List<ClauseVerdict> verdicts = new ArrayList<>();
        for (Indication indication : indications) {
            verdicts.add(judgeOne(indication, wallIn, weldLengthIn, crackRepairs));
        }
        for (Api1104Rule rule : lengthRules()) {
            verdicts.addAll(Api1104Weld.judgeAggregate(indications, rule, weldLengthIn));
        }
        verdicts.addAll(Api1104Weld.judgeIsolatedSlag(indications, weldLengthIn));
        verdicts.addAll(Api1104Weld.judgeHollowBeadSpacing(indications, weldLengthIn));
        verdicts.addAll(Api1104Weld.judgeAccumulation(indications, weldLengthIn));
        return Common.settle(verdicts, CODE, "9.3");
    }
}
