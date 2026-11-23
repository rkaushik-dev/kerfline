package io.github.rkaushikdev.kerfline.geom;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The shortest workable crank for a joint and why. When the allowed shot count cannot be met at
 * any distance, {@code shotsReachable} is false, there is no distance, and {@code reason} says
 * so; otherwise the distance is the largest of what unsharpness, coverage and, when a testing class
 * was asked for, ISO 17636 each require ({@code isoMinimumIn} is empty when none was), and {@code
 * governedBy} names which one that was.
 */
public record WorkingDistance(
        ViewingTechnique technique,
        double objectToFilmIn,
        double limitIn,
        double minimumSourceToObjectIn,
        boolean shotsReachable,
        OptionalDouble requiredForShotsIn,
        OptionalDouble isoMinimumIn,
        OptionalDouble sourceToObjectIn,
        Constraint governedBy,
        Optional<String> reason) {}
