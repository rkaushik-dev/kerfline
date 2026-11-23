package io.github.rkaushikdev.kerfline.accept;

import io.github.rkaushikdev.kerfline.indication.Indication;
import io.github.rkaushikdev.kerfline.indication.IndicationType;
import io.github.rkaushikdev.kerfline.indication.Measure;
import io.github.rkaushikdev.kerfline.indication.WeldWindow;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * ASME B31.3 process piping, Table 341.3.2.
 *
 * <p>The service the line is in changes the criteria, so nothing here can be evaluated without
 * knowing it. Severe cyclic service tightens the undercut limit and Category D loosens incomplete
 * penetration from "never" to an inch and a half in any six inches of weld, which is the largest
 * single difference between any two columns of that table.
 *
 * <p>Almost every limit is a fraction of Tw, the thickness of the thinner of the two components
 * joined. That includes the window the cumulative limit is measured over, which is twelve Tw rather
 * than a fixed twelve inches. So on a quarter inch wall the code is looking at three inches of weld
 * at a time, and on an inch wall it is looking at a foot. Getting Tw from the thicker component
 * makes every limit on the joint too generous at once.
 */
public final class AsmeB313 {

    private static final String CODE = "B31.3";
    private static final String CLAUSE = "341.3.2";
    private static final String DEFAULT_SERVICE = "normal";

    private static final List<String> SERVICES = List.of("normal", "severe_cyclic", "category_d");

    private static final Map<String, AsmeB313Rules> RULES = Map.of(
            "normal", new AsmeB313Rules(0.0625, 0.25, OptionalDouble.empty(), 0.25),
            "severe_cyclic", new AsmeB313Rules(0.03125, 0.25, OptionalDouble.empty(), 0.2),
            "category_d", new AsmeB313Rules(0.0625, 0.25, OptionalDouble.of(1.5), 0.25));

    /** The run of weld Category D reads its incomplete penetration allowance over. */
    private static final double PENETRATION_WINDOW_IN = 6;

    private static final double INCLUSION_FRACTION = 1.0 / 3;
    private static final double INCLUSION_WIDTH_IN = 0.098;
    private static final double WINDOW_MULTIPLE = 12;

    private AsmeB313() {}

    public static String codeName() {
        return CODE;
    }

    /** The three fluid service columns of the table. */
    public static List<String> services() {
        return SERVICES;
    }

    public static AsmeB313Rules rulesFor(String service) {
        Guard.requireOneOf(service, SERVICES, "fluid service");
        return RULES.get(service);
    }

    /** The window the cumulative limits are read over, which scales with Tw. */
    public static double windowLengthIn(double thinnerWallIn) {
        Guard.requireAbove(thinnerWallIn, 0, "thinner component thickness");
        return Num.round(WINDOW_MULTIPLE * thinnerWallIn, 4);
    }

    /** The caps on a single inclusion, both ways it is capped, and the cumulative cap with its window. */
    public static InclusionLimits inclusionLimits(double thinnerWallIn) {
        Guard.requireAbove(thinnerWallIn, 0, "thinner component thickness");
        double byThickness = Num.round(thinnerWallIn * INCLUSION_FRACTION, 4);
        return new InclusionLimits(
                byThickness,
                Num.round(Math.min(INCLUSION_WIDTH_IN, byThickness), 4),
                Num.round(thinnerWallIn, 4),
                windowLengthIn(thinnerWallIn));
    }

    public static double undercutLimitIn(String service, double thinnerWallIn) {
        AsmeB313Rules rules = rulesFor(service);
        Guard.requireAbove(thinnerWallIn, 0, "thinner component thickness");
        return Num.round(Math.min(rules.undercutIn(), thinnerWallIn * rules.undercutFraction()), 4);
    }

    private static CapCheck check(Indication indication, String what, double sizeIn, double capIn, String units) {
        return CapCheck.of(CODE, CLAUSE, what)
                .size(sizeIn)
                .cap(capIn)
                .units(units)
                .indication(indication);
    }

    /** One indication under the column for the service the line runs in. */
    public static ClauseVerdict judgeOne(Indication indication, String service, double thinnerWallIn) {
        AsmeB313Rules rules = rulesFor(service);
        Guard.requireAbove(thinnerWallIn, 0, "thinner component thickness");
        double size = Measure.governingSizeIn(indication);

        return switch (indication.type()) {
            case CRACK, IF -> Common.rejectPlanar(CODE, CLAUSE, indication);
            case IP ->
                rules.penetrationIn().isPresent()
                        ? Common.capVerdict(check(
                                indication,
                                "incomplete penetration",
                                size,
                                rules.penetrationIn().getAsDouble(),
                                "in long"))
                        : Verdict.makeVerdict(
                                Disposition.REJECT,
                                CODE,
                                CLAUSE,
                                indication.label() + " is not permitted in " + service + " service",
                                indication,
                                size);
            case SLAG, TUNGSTEN -> judgeInclusion(indication, size, inclusionLimits(thinnerWallIn));
            case UNDERCUT ->
                Common.capVerdict(check(indication, "undercut", size, undercutLimitIn(service, thinnerWallIn), "in"));
            case POROSITY, CLUSTER ->
                Common.capVerdict(check(
                        indication, "pore", size, Num.round(thinnerWallIn * rules.poreFraction(), 4), "in across"));
            default -> Verdict.accept(CODE, CLAUSE);
        };
    }

    private static ClauseVerdict judgeInclusion(Indication indication, double size, InclusionLimits limits) {
        if (indication.widthIn().isPresent()) {
            ClauseVerdict byWidth = Common.capVerdict(
                    check(indication, "inclusion", indication.widthIn().getAsDouble(), limits.widthIn(), "in wide"));
            if (!Verdict.isAcceptable(byWidth)) {
                return byWidth;
            }
        }
        return Common.capVerdict(check(indication, "inclusion", size, limits.lengthIn(), "in long"));
    }

    /**
     * The Category D allowance on incomplete penetration, read as a total in any six inches of weld.
     * A column that permits none has nothing to total, because every such indication has already
     * been rejected on its own.
     */
    public static List<ClauseVerdict> judgePenetration(List<Indication> indications, String service) {
        AsmeB313Rules rules = rulesFor(service);
        Guard.requireArray(indications, "indications");
        List<Indication> penetration = indications.stream()
                .filter(indication -> indication.type() == IndicationType.IP)
                .toList();
        if (rules.penetrationIn().isEmpty() || penetration.isEmpty()) {
            return List.of();
        }
        double window = Measure.worstWindow(penetration, PENETRATION_WINDOW_IN).totalIn();
        double total = window > 0 ? window : Measure.cumulativeLengthIn(penetration);
        double cap = rules.penetrationIn().getAsDouble();
        if (total <= cap) {
            return List.of();
        }
        return List.of(Verdict.makeVerdict(
                Disposition.REPAIR,
                CODE,
                CLAUSE,
                "incomplete penetration totals " + Num.text(Num.round(total, 4)) + " in in "
                        + Num.text(PENETRATION_WINDOW_IN) + " in of weld, cap is " + Num.text(cap) + " in",
                total));
    }

    private static boolean isInclusion(Indication indication) {
        return indication.type() == IndicationType.SLAG || indication.type() == IndicationType.TUNGSTEN;
    }

    /** The cumulative inclusion cap, read over a window that scales with Tw. */
    public static List<ClauseVerdict> judgeCumulative(List<Indication> indications, double thinnerWallIn) {
        InclusionLimits limits = inclusionLimits(thinnerWallIn);
        Guard.requireArray(indications, "indications");
        List<Indication> inclusions =
                indications.stream().filter(AsmeB313::isInclusion).toList();
        if (inclusions.isEmpty()) {
            return List.of();
        }

        WeldWindow window = Measure.worstWindow(inclusions, limits.windowIn());
        double total = window.totalIn() > 0 ? window.totalIn() : Measure.cumulativeLengthIn(inclusions);
        if (total <= limits.cumulativeIn()) {
            return List.of();
        }
        return List.of(Verdict.makeVerdict(
                Disposition.REPAIR,
                CODE,
                CLAUSE,
                "inclusions total " + Num.text(Num.round(total, 4)) + " in in " + Num.text(limits.windowIn())
                        + " in of weld, cap is " + Num.text(limits.cumulativeIn()) + " in",
                total));
    }

    /** Judge a whole joint in normal service. */
    public static JointVerdict judgeJoint(List<Indication> indications, double thinnerWallIn) {
        return judgeJoint(indications, thinnerWallIn, DEFAULT_SERVICE);
    }

    /**
     * Judge a whole joint: each indication under the service column, then the cumulative inclusions,
     * then whatever incomplete penetration the column allows.
     */
    public static JointVerdict judgeJoint(List<Indication> indications, double thinnerWallIn, String service) {
        Guard.requireArray(indications, "indications");
        Guard.requireOneOf(service, SERVICES, "fluid service");
        Guard.requireAbove(thinnerWallIn, 0, "thinner component thickness");
        List<ClauseVerdict> verdicts = new ArrayList<>();
        for (Indication indication : indications) {
            verdicts.add(judgeOne(indication, service, thinnerWallIn));
        }
        verdicts.addAll(judgeCumulative(indications, thinnerWallIn));
        verdicts.addAll(judgePenetration(indications, service));
        return Common.settle(verdicts, CODE, CLAUSE);
    }
}
