package io.github.rkaushikdev.kerfline.geom;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.Locale;

/**
 * The ISO 17636-1 rule on how close the source may come.
 *
 * <p>The ASME rule caps the unsharpness itself. ISO puts the limit on the distance instead, and it
 * does not scale the way unsharpness does: the minimum source to object distance is a factor times
 * the source size times the object to film distance raised to two thirds. So doubling the standoff
 * asks for only about one and a half times the distance, where holding a fixed unsharpness would
 * ask for twice. On a contact shot of thin wall ISO is the stricter of the two, and on a double wall
 * shot of large pipe it is usually the gentler.
 *
 * <p>The standard works in millimetres and the power makes that matter: the formula is not unit
 * free, so the inch figures here are converted through millimetres rather than plugged in.
 */
public final class Iso17636 {

    private static final double MM_PER_IN = 25.4;
    private static final double THICKNESS_MULTIPLE = 1.2;

    private Iso17636() {}

    /**
     * The object to film distance the formula is read at. Where the film sits closer than 1.2 times
     * the nominal thickness, the thickness stands in for it.
     */
    public static double effectiveObjectToFilmMm(double objectToFilmMm, double thicknessMm) {
        Guard.requireAtLeast(objectToFilmMm, 0, "object to film distance");
        Guard.requireAbove(thicknessMm, 0, "nominal thickness");
        return objectToFilmMm < THICKNESS_MULTIPLE * thicknessMm ? thicknessMm : objectToFilmMm;
    }

    /** The closest the source may be to the source side of the object, in millimetres. */
    public static double minimumSourceToObjectMm(
            double sourceSizeMm, double objectToFilmMm, double thicknessMm, IsoClass testClass) {
        Guard.requireAbove(sourceSizeMm, 0, "source size");
        if (testClass == null) {
            throw new BadInputException("an ISO testing class is A or B");
        }
        double b = effectiveObjectToFilmMm(objectToFilmMm, thicknessMm);
        return Num.round(testClass.factor() * sourceSizeMm * Math.pow(b, 2.0 / 3), 1);
    }

    /** The same minimum for dimensions measured in inches, worked through millimetres. */
    public static double minimumSourceToObjectIn(
            double sourceSizeIn, double objectToFilmIn, double thicknessIn, IsoClass testClass) {
        double mm = minimumSourceToObjectMm(
                sourceSizeIn * MM_PER_IN, objectToFilmIn * MM_PER_IN, thicknessIn * MM_PER_IN, testClass);
        return Num.round(mm / MM_PER_IN, 2);
    }

    /** Whether a source to object distance in inches meets the class. */
    public static boolean meetsClass(
            double sourceToObjectIn,
            double sourceSizeIn,
            double objectToFilmIn,
            double thicknessIn,
            IsoClass testClass) {
        return check(sourceToObjectIn, sourceSizeIn, objectToFilmIn, thicknessIn, testClass)
                .pass();
    }

    /** A planned setup against the class, with the minimum it was held to. */
    public static IsoDistanceCheck check(
            double sourceToObjectIn,
            double sourceSizeIn,
            double objectToFilmIn,
            double thicknessIn,
            IsoClass testClass) {
        Guard.requireAbove(sourceToObjectIn, 0, "source to object distance");
        double minimum = minimumSourceToObjectIn(sourceSizeIn, objectToFilmIn, thicknessIn, testClass);
        return new IsoDistanceCheck(testClass, minimum, sourceToObjectIn, sourceToObjectIn >= minimum);
    }

    /**
     * An ISO testing class as somebody wrote it, {@code B} or {@code class b}.
     *
     * @throws BadInputException when it is neither A nor B
     */
    public static IsoClass testClass(String text) {
        String key = text == null
                ? ""
                : text.trim().toUpperCase(Locale.ROOT).replace("CLASS", "").trim();
        for (IsoClass testClass : IsoClass.values()) {
            if (testClass.name().equals(key)) {
                return testClass;
            }
        }
        throw new BadInputException("an ISO testing class is A or B, not " + text);
    }
}
