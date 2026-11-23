package io.github.rkaushikdev.kerfline.safety;

/**
 * A declared pregnant worker's standing: the dose from conception to the declaration, the dose
 * since, what the rest of the pregnancy is allowed, how much of that is left, and the standing.
 */
public record GestationStanding(
        DosePeriod beforeDeclaration,
        DosePeriod sinceDeclaration,
        double allowedSinceMrem,
        double remainingMrem,
        DoseStanding standing) {}
