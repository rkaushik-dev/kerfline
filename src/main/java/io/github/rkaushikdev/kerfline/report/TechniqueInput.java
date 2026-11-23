package io.github.rkaushikdev.kerfline.report;

import io.github.rkaushikdev.kerfline.geom.IsoClass;
import io.github.rkaushikdev.kerfline.joint.Joint;

/**
 * What a technique sheet is worked out from: the job and joint, the source and its assay, the
 * distances the crew means to set up at, the film, screens and density the job calls for, the
 * thickness ratio the coverage is planned to, whether the source can go inside the pipe, what the
 * pipe is made of, and the ISO 17636 testing class the client holds the work to, if any.
 *
 * <p>Built with {@link #builder()}, which starts from the shop defaults: a source shot on its assay
 * day, the film flat against the pipe, a 0.12 in focal spot, D7 film under standard lead screens
 * at density 2, a thickness ratio of 1.2, the source outside, and carbon steel. A number left
 * unset is NaN and a name left unset is null, so the calculation that needed it says so on the
 * sheet.
 */
public record TechniqueInput(
        String job,
        Joint joint,
        String isotopeId,
        double assayCi,
        double daysSinceAssay,
        double sourceToObjectIn,
        double objectToFilmIn,
        double sourceSizeIn,
        String filmId,
        String screens,
        double targetDensity,
        double maxThicknessRatio,
        boolean sourceCanEnter,
        String materialId,
        IsoClass isoClass) {

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .job(job)
                .joint(joint)
                .isotopeId(isotopeId)
                .assayCi(assayCi)
                .daysSinceAssay(daysSinceAssay)
                .sourceToObjectIn(sourceToObjectIn)
                .objectToFilmIn(objectToFilmIn)
                .sourceSizeIn(sourceSizeIn)
                .filmId(filmId)
                .screens(screens)
                .targetDensity(targetDensity)
                .maxThicknessRatio(maxThicknessRatio)
                .sourceCanEnter(sourceCanEnter)
                .materialId(materialId)
                .isoClass(isoClass);
    }

    /** Builds a {@link TechniqueInput} from the shop defaults. */
    public static final class Builder {
        private String job;
        private Joint joint;
        private String isotopeId;
        private double assayCi = Double.NaN;
        private double daysSinceAssay = 0;
        private double sourceToObjectIn = Double.NaN;
        private double objectToFilmIn = 0;
        private double sourceSizeIn = 0.12;
        private String filmId = "D7";
        private String screens = "LEAD_STANDARD";
        private double targetDensity = 2;
        private double maxThicknessRatio = 1.2;
        private boolean sourceCanEnter = false;
        private String materialId = "CARBON_STEEL";
        private IsoClass isoClass;

        private Builder() {}

        public Builder job(String value) {
            job = value;
            return this;
        }

        public Builder joint(Joint value) {
            joint = value;
            return this;
        }

        public Builder isotopeId(String value) {
            isotopeId = value;
            return this;
        }

        public Builder assayCi(double value) {
            assayCi = value;
            return this;
        }

        public Builder daysSinceAssay(double value) {
            daysSinceAssay = value;
            return this;
        }

        public Builder sourceToObjectIn(double value) {
            sourceToObjectIn = value;
            return this;
        }

        public Builder objectToFilmIn(double value) {
            objectToFilmIn = value;
            return this;
        }

        public Builder sourceSizeIn(double value) {
            sourceSizeIn = value;
            return this;
        }

        public Builder filmId(String value) {
            filmId = value;
            return this;
        }

        public Builder screens(String value) {
            screens = value;
            return this;
        }

        public Builder targetDensity(double value) {
            targetDensity = value;
            return this;
        }

        public Builder maxThicknessRatio(double value) {
            maxThicknessRatio = value;
            return this;
        }

        public Builder sourceCanEnter(boolean value) {
            sourceCanEnter = value;
            return this;
        }

        public Builder materialId(String value) {
            materialId = value;
            return this;
        }

        /** The ISO testing class to hold the setup to, or null for none. */
        public Builder isoClass(IsoClass value) {
            isoClass = value;
            return this;
        }

        public TechniqueInput build() {
            return new TechniqueInput(
                    job,
                    joint,
                    isotopeId,
                    assayCi,
                    daysSinceAssay,
                    sourceToObjectIn,
                    objectToFilmIn,
                    sourceSizeIn,
                    filmId,
                    screens,
                    targetDensity,
                    maxThicknessRatio,
                    sourceCanEnter,
                    materialId,
                    isoClass);
        }
    }
}
