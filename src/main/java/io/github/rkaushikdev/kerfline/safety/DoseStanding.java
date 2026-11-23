package io.github.rkaushikdev.kerfline.safety;

/**
 * Where a person's dose stands: the rolling twelve months against the limit and the shop levels, or
 * a declared pregnancy against the gestation limit.
 */
public enum DoseStanding {
    WITHIN_LIMITS("within limits"),
    LEVEL_ONE_INVESTIGATION("level one investigation"),
    LEVEL_TWO_INVESTIGATION("level two investigation"),
    OVER_THE_ANNUAL_LIMIT("over the annual limit"),
    /** A declared pregnant worker past the allowance for the rest of her pregnancy. */
    OVER_THE_GESTATION_LIMIT("over the gestation limit"),
    /** A badge is still out, so the dose is not known; never reported as within limits. */
    INCOMPLETE_RECORD("incomplete record");

    private final String label;

    DoseStanding(String label) {
        this.label = label;
    }

    /** The words the dose record prints. */
    public String label() {
        return label;
    }
}
