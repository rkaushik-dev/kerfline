package io.github.rkaushikdev.kerfline.safety;

/**
 * The radioactive material label a package carries, from the lowest category to a load that cannot
 * travel as an ordinary package at all.
 */
public enum PackageLabel {
    WHITE_I("Radioactive White-I", 0.05, 0.5),
    YELLOW_II("Radioactive Yellow-II", 1, 50),
    YELLOW_III("Radioactive Yellow-III", 10, 200),
    /** Over the Yellow-III limits: the package goes only as an exclusive use shipment. */
    EXCLUSIVE_USE("exclusive use only", Double.POSITIVE_INFINITY, 1000);

    private final String label;
    private final double transportIndexUpTo;
    private final double surfaceUpToMremPerHour;

    PackageLabel(String label, double transportIndexUpTo, double surfaceUpToMremPerHour) {
        this.label = label;
        this.transportIndexUpTo = transportIndexUpTo;
        this.surfaceUpToMremPerHour = surfaceUpToMremPerHour;
    }

    /** The words on the label, or on the paperwork for an exclusive use load. */
    public String label() {
        return label;
    }

    /** The highest transport index this category takes. */
    public double transportIndexUpTo() {
        return transportIndexUpTo;
    }

    /** The highest dose rate at the package surface this category takes, in mrem per hour. */
    public double surfaceUpToMremPerHour() {
        return surfaceUpToMremPerHour;
    }
}
