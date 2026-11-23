package io.github.rkaushikdev.kerfline.geom;

/** The rule that decided how far out the source has to go. */
public enum Constraint {
    /** The geometric unsharpness limit. */
    UNSHARPNESS("unsharpness"),
    /** The number of shots allowed round the joint. */
    COVERAGE("coverage"),
    /** The ISO 17636-1 minimum source to object distance for the testing class asked for. */
    ISO_17636("ISO 17636");

    private final String label;

    Constraint(String label) {
        this.label = label;
    }

    /** The words a technique sheet prints: {@code unsharpness}, {@code coverage} or {@code ISO 17636}. */
    public String label() {
        return label;
    }
}
