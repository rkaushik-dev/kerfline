package io.github.rkaushikdev.kerfline.accept;

import java.util.OptionalDouble;

/**
 * An API 1104 length rule for one indication type: its clause, the cap on any single one, the cap
 * on the total in any twelve inches of weld, and the words the reason uses for it.
 *
 * <p>The code caps the total against the weld length in two different ways and they must not be
 * confused. {@code percent} is the eight percent cap the code applies only to a weld under twelve
 * inches long, where the twelve inch window is the whole weld and needs a tighter ceiling. {@code
 * wholeWeldFraction} applies to a weld of any length, which is how undercut is capped at a sixth of
 * the weld. Either is absent where the code sets none.
 */
public record Api1104Rule(
        String clause,
        double singleIn,
        double windowIn,
        OptionalDouble percent,
        OptionalDouble wholeWeldFraction,
        String label) {}
