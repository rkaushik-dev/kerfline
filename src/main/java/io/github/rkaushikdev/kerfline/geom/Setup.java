package io.github.rkaushikdev.kerfline.geom;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.errors.KerflineException;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Working out where the source goes.
 *
 * <p>The crew's question is never "what is the unsharpness at 24 inches". It is "how far out do I
 * have to crank it", and the answer is governed by two things at once, three when a client holds the
 * work to an ISO 17636 testing class. Cranking out improves the
 * unsharpness and it also widens what one shot covers, so both constraints push the same way and
 * the answer is whichever of them is further out.
 *
 * <p>That both push the same way is worth stating, because it is what makes the answer a maximum
 * of two numbers rather than a search. It also means there is no trade to make: a setup that fails
 * unsharpness is not fixed by accepting more shots, and one that needs too many shots is not fixed
 * by accepting a blurrier film.
 */
public final class Setup {

    private Setup() {}

    /**
     * The standoff the viewing technique implies, which is not always the wall. A double wall shot
     * puts the film on the far side of the pipe, so the standoff is the whole diameter; a source
     * inside sits against the wall.
     */
    public static double standoffIn(double odIn, double wallIn, ViewingTechnique technique) {
        Guard.requireAbove(odIn, 0, "outside diameter");
        Guard.requireAbove(wallIn, 0, "wall thickness");
        return technique.name().startsWith("SWSI") ? Num.round(wallIn, 4) : Num.round(odIn, 4);
    }

    public static UnsharpnessDistance distanceForUnsharpness(double odIn, double wallIn, double sourceSizeIn) {
        return distanceForUnsharpness(odIn, wallIn, sourceSizeIn, false);
    }

    /** The closest the source can be and still hold the unsharpness limit. */
    public static UnsharpnessDistance distanceForUnsharpness(
            double odIn, double wallIn, double sourceSizeIn, boolean sourceCanEnter) {
        ViewingPlan viewing = Viewing.viewingPlan(odIn, wallIn, sourceCanEnter);
        double objectToFilmIn = standoffIn(odIn, wallIn, viewing.technique());
        double limitIn = Unsharpness.unsharpnessLimit(viewing.penetratedThicknessIn());
        return new UnsharpnessDistance(
                viewing.technique(),
                objectToFilmIn,
                limitIn,
                Num.round(Unsharpness.minimumSourceToObject(sourceSizeIn, objectToFilmIn, limitIn), 2));
    }

    /**
     * The shortest crank that satisfies every constraint asked about. Says which one governed,
     * because that is what tells a crew whether to fetch a smaller source or accept more shots when
     * the answer is impractical, and says plainly when the shot count cannot be met at any distance.
     * Coverage wins a tie, since a crew can see it on the pipe. When the question names an ISO
     * testing class, its minimum distance is a third constraint pushing the same way as the other two.
     */
    public static WorkingDistance shortestWorkingDistance(WorkingDistanceQuestion question) {
        int maxShots = question.maxShots();
        Guard.requireAbove(maxShots, 0, "shot count");
        UnsharpnessDistance sharpness = distanceForUnsharpness(
                question.odIn(), question.wallIn(), question.sourceSizeIn(), question.sourceCanEnter());
        OptionalDouble forIso = question.isoClass().stream()
                .mapToDouble(testClass -> Iso17636.minimumSourceToObjectIn(
                        question.sourceSizeIn(), sharpness.objectToFilmIn(), question.wallIn(), testClass))
                .findFirst();
        OptionalDouble forShots = distanceForShots(question.odIn(), maxShots, question.maxThicknessRatio());

        Constraint governedBy = Constraint.UNSHARPNESS;
        double governed = sharpness.minimumSourceToObjectIn();
        if (forIso.isPresent() && forIso.getAsDouble() > governed) {
            governedBy = Constraint.ISO_17636;
            governed = forIso.getAsDouble();
        }
        if (forShots.isEmpty() || forShots.getAsDouble() >= governed) {
            governedBy = Constraint.COVERAGE;
            governed = forShots.orElse(Double.NaN);
        }
        return new WorkingDistance(
                sharpness.technique(),
                sharpness.objectToFilmIn(),
                sharpness.limitIn(),
                sharpness.minimumSourceToObjectIn(),
                forShots.isPresent(),
                forShots,
                forIso,
                forShots.isPresent() ? OptionalDouble.of(Num.round(governed, 2)) : OptionalDouble.empty(),
                governedBy,
                forShots.isPresent()
                        ? Optional.empty()
                        : Optional.of(maxShots + " shots cannot cover this joint at any distance with a "
                                + Num.text(question.maxThicknessRatio()) + " ratio"));
    }

    private static OptionalDouble distanceForShots(double odIn, int shots, double maxThicknessRatio) {
        try {
            return OptionalDouble.of(Coverage.requiredDistanceForShots(odIn, shots, maxThicknessRatio));
        } catch (KerflineException unreachable) {
            return OptionalDouble.empty();
        }
    }

    /**
     * The whole setup, resolved. Takes whatever distances the caller has and fills in the rest,
     * the standoff from the viewing technique when none is given, then checks the two constraints
     * against what it ended up with rather than against what was asked for.
     */
    public static ResolvedSetup resolveSetup(SetupInput input) {
        if (input == null) {
            throw new BadInputException("a setup is resolved from an object", Details.of("got", null));
        }
        ViewingPlan viewing = Viewing.viewingPlan(input.odIn(), input.wallIn(), input.sourceCanEnter());
        Double objectToFilmIn = input.objectToFilmIn() != null
                ? input.objectToFilmIn()
                : Double.valueOf(standoffIn(input.odIn(), input.wallIn(), viewing.technique()));
        Distances resolved = Distance.resolveDistances(
                new GivenDistances(input.sourceToFilmIn(), input.sourceToObjectIn(), objectToFilmIn));

        UnsharpnessCheck sharpness = Unsharpness.checkUnsharpness(new UnsharpnessSetup(
                input.sourceSizeIn(),
                resolved.objectToFilmIn(),
                resolved.sourceToObjectIn(),
                viewing.penetratedThicknessIn()));

        int shots = Coverage.exposuresForCircumference(
                input.odIn(), resolved.sourceToObjectIn(), input.maxThicknessRatio());
        List<PlannedShot> plan =
                Coverage.shotPlan(input.odIn(), resolved.sourceToObjectIn(), input.maxThicknessRatio());

        return new ResolvedSetup(viewing, resolved, sharpness, shots, plan, sharpness.pass());
    }
}
