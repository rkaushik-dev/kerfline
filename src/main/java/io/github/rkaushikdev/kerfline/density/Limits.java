package io.github.rkaushikdev.kerfline.density;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.geom.IsoClass;
import io.github.rkaushikdev.kerfline.isotope.Catalog;
import io.github.rkaushikdev.kerfline.isotope.SourceKind;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.ArrayList;
import java.util.List;

/**
 * Film density limits.
 *
 * <p>The floor depends on what made the radiograph. A gamma source lays down less contrast than an
 * X-ray head at the same density, so the gamma floor is higher to make up for it. The ceiling is
 * the same for both and it is set by the viewer rather than by the physics: past four there is not
 * enough light coming through the film to read it on the illuminators this shop owns.
 *
 * <p>Composite viewing is the exception in both directions. Two films stacked add their
 * densities, so each single film of the pair is allowed down to 1.3, and it is the stacked pair
 * that has to clear the usual floor.
 *
 * <p>A source is named either by its kind ({@code gamma} or {@code xray}) or by an isotope id,
 * which counts as the kind of that isotope.
 */
public final class Limits {

    private static final double XRAY_FLOOR = 1.8;
    private static final double GAMMA_FLOOR = 2;
    private static final double COMPOSITE_EACH = 1.3;
    private static final double CEILING = 4;
    private static final double ISO_A_FLOOR = 2.0;
    private static final double ISO_B_FLOOR = 2.3;

    private static final List<SourceKind> KINDS = List.of(SourceKind.XRAY, SourceKind.GAMMA);

    private Limits() {}

    public static List<SourceKind> sourceKinds() {
        return KINDS;
    }

    /** The kind a source counts as, so a caller can pass an isotope id straight in. */
    public static SourceKind sourceKindOf(String source) {
        for (SourceKind kind : KINDS) {
            if (kind.label().equals(source)) {
                return kind;
            }
        }
        return Catalog.isotope(source).kind() == SourceKind.GAMMA ? SourceKind.GAMMA : SourceKind.XRAY;
    }

    public static DensityLimits densityLimits(String source) {
        return densityLimits(sourceKindOf(source), false);
    }

    public static DensityLimits densityLimits(String source, boolean composite) {
        return densityLimits(sourceKindOf(source), composite);
    }

    public static DensityLimits densityLimits(SourceKind kind) {
        return densityLimits(kind, false);
    }

    /**
     * The limits for one film. Composite viewing lowers only the floor, and only for the
     * individual films of the set.
     */
    public static DensityLimits densityLimits(SourceKind kind, boolean composite) {
        double single = kind == SourceKind.GAMMA ? GAMMA_FLOOR : XRAY_FLOOR;
        return new DensityLimits(kind, composite ? COMPOSITE_EACH : single, CEILING, composite);
    }

    /**
     * The lowest density ISO 17636-1 accepts for a testing class: 2.0 for class A and 2.3 for class
     * B, the extra contrast being much of what class B buys.
     */
    public static double isoMinimumDensity(IsoClass testClass) {
        if (testClass == null) {
            throw new BadInputException("an ISO testing class is A or B", Details.of("got", null));
        }
        return testClass == IsoClass.B ? ISO_B_FLOOR : ISO_A_FLOOR;
    }

    public static double ceilingDensity() {
        return CEILING;
    }

    public static boolean withinLimits(double value, String source) {
        return withinLimits(value, source, false);
    }

    /** True when the reading is inside the band; a reading that is not a number is simply outside it. */
    public static boolean withinLimits(double value, String source, boolean composite) {
        if (!Double.isFinite(value)) {
            return false;
        }
        DensityLimits limits = densityLimits(source, composite);
        return value >= limits.minimum() && value <= limits.maximum();
    }

    public static DensityJudgement judgeDensity(double value, String source) {
        return judgeDensity(value, source, false);
    }

    /**
     * Judge one reading, saying which way it failed. A film that is too light and one that is too
     * dark need opposite corrections, so a bare false would make the caller work it out again.
     */
    public static DensityJudgement judgeDensity(double value, String source, boolean composite) {
        if (!Double.isFinite(value)) {
            throw new BadInputException("density must be a finite number", Details.of("got", value));
        }
        if (value <= 0) {
            throw new BadInputException("density must be greater than zero", Details.of("got", value));
        }
        DensityLimits limits = densityLimits(source, composite);
        DensityCall verdict = DensityCall.ACCEPT;
        if (value < limits.minimum()) {
            verdict = DensityCall.LIGHT;
        } else if (value > limits.maximum()) {
            verdict = DensityCall.DARK;
        }
        return new DensityJudgement(
                value, verdict, verdict == DensityCall.ACCEPT, limits.minimum(), limits.maximum(), limits.kind());
    }

    /**
     * Judge a composite set. Each film has to clear the composite floor on its own, and the sum of
     * the set has to clear the ordinary single film floor, which is the rule people forget when
     * they stack two thin films.
     */
    public static CompositeJudgement judgeComposite(List<Double> values, String source) {
        if (values == null || values.size() < 2) {
            throw new BadInputException("a composite set is two or more films", Details.of("got", values));
        }
        for (int index = 0; index < values.size(); index++) {
            Double value = values.get(index);
            if (value == null || !Double.isFinite(value) || value <= 0) {
                throw new BadInputException(
                        "film " + (index + 1) + " density must be greater than zero", Details.of("got", value));
            }
        }
        List<DensityJudgement> each = new ArrayList<>();
        double total = 0;
        for (double value : values) {
            each.add(judgeDensity(value, source, true));
            total += value;
        }
        double stacked = Num.round(total, 3);
        DensityLimits single = densityLimits(source);
        boolean everyFilmPasses = each.stream().allMatch(DensityJudgement::pass);
        boolean stackedPasses = stacked >= single.minimum() && stacked <= single.maximum();
        return new CompositeJudgement(each, stacked, everyFilmPasses, stackedPasses, everyFilmPasses && stackedPasses);
    }
}
