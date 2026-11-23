package io.github.rkaushikdev.kerfline.geom;

/**
 * A setup held against an ISO 17636 testing class: the class, the minimum source to object distance
 * it asks for, the distance the crew means to set up at, and whether that is far enough.
 */
public record IsoDistanceCheck(IsoClass testClass, double minimumIn, double sourceToObjectIn, boolean pass) {

    /** How much further out the source has to go, or nought when it is already far enough. */
    public double shortfallIn() {
        return pass ? 0 : Math.round((minimumIn - sourceToObjectIn) * 100) / 100.0;
    }
}
