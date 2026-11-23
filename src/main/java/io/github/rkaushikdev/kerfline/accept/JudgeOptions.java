package io.github.rkaushikdev.kerfline.accept;

/**
 * What a caller can tell the engine beyond the joint record: an overriding length of weld for API
 * 1104, the loading for AWS D1.1, the reinforcement Section VIII reads past, the wall of the mating
 * component and the fluid service for B31.3, and whether the company has authorised crack repairs
 * on API 1104 work. A null component was not given and the code's default applies. Start from
 * {@link #NONE} and adjust with the copying setters.
 */
public record JudgeOptions(
        Double weldLengthIn,
        String loading,
        Double reinforcementIn,
        Double matingWallIn,
        String service,
        Boolean crackRepairs) {

    /** Nothing beyond the joint record. */
    public static final JudgeOptions NONE = new JudgeOptions(null, null, null, null, null, null);

    public JudgeOptions weldLengthIn(double inches) {
        return new JudgeOptions(inches, loading, reinforcementIn, matingWallIn, service, crackRepairs);
    }

    public JudgeOptions loading(String kind) {
        return new JudgeOptions(weldLengthIn, kind, reinforcementIn, matingWallIn, service, crackRepairs);
    }

    public JudgeOptions reinforcementIn(double inches) {
        return new JudgeOptions(weldLengthIn, loading, inches, matingWallIn, service, crackRepairs);
    }

    public JudgeOptions matingWallIn(double inches) {
        return new JudgeOptions(weldLengthIn, loading, reinforcementIn, inches, service, crackRepairs);
    }

    public JudgeOptions service(String fluidService) {
        return new JudgeOptions(weldLengthIn, loading, reinforcementIn, matingWallIn, fluidService, crackRepairs);
    }

    /** Whether the company lets a short crack be repaired rather than the joint cut out. */
    public JudgeOptions crackRepairs(boolean authorised) {
        return new JudgeOptions(weldLengthIn, loading, reinforcementIn, matingWallIn, service, authorised);
    }
}
