package io.github.rkaushikdev.kerfline.report;

import io.github.rkaushikdev.kerfline.indication.Indication;
import java.util.List;

/**
 * What an interpretation sheet is made from. Every field is optional, because a sheet missing its
 * reader can still be printed as a draft; {@link Interpretation#completeness} says what an issued
 * sheet still needs. The outside diameter, when given, lets the sheet say where each finding sits
 * as a clock position. Built with {@link #builder()}, where the films and indications start empty;
 * setting either to null is refused when the sheet is made, as a list that is not one.
 */
public record SheetInput(
        String job,
        String jointId,
        String day,
        String reader,
        String readerLevel,
        String code,
        String technique,
        Double wallIn,
        Double odIn,
        List<String> films,
        List<Indication> indications,
        SheetVerdict verdict,
        String remarks) {

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .job(job)
                .jointId(jointId)
                .day(day)
                .reader(reader)
                .readerLevel(readerLevel)
                .code(code)
                .technique(technique)
                .wallIn(wallIn)
                .odIn(odIn)
                .films(films)
                .indications(indications)
                .verdict(verdict)
                .remarks(remarks);
    }

    /** Builds a {@link SheetInput}; anything not set is left off the sheet. */
    public static final class Builder {
        private String job;
        private String jointId;
        private String day;
        private String reader;
        private String readerLevel;
        private String code;
        private String technique;
        private Double wallIn;
        private Double odIn;
        private List<String> films = List.of();
        private List<Indication> indications = List.of();
        private SheetVerdict verdict;
        private String remarks;

        private Builder() {}

        public Builder job(String value) {
            job = value;
            return this;
        }

        public Builder jointId(String value) {
            jointId = value;
            return this;
        }

        public Builder day(String value) {
            day = value;
            return this;
        }

        public Builder reader(String value) {
            reader = value;
            return this;
        }

        public Builder readerLevel(String value) {
            readerLevel = value;
            return this;
        }

        public Builder code(String value) {
            code = value;
            return this;
        }

        public Builder technique(String value) {
            technique = value;
            return this;
        }

        public Builder wallIn(Double value) {
            wallIn = value;
            return this;
        }

        /** The outside diameter, which puts each finding at a clock position on the pipe. */
        public Builder odIn(Double value) {
            odIn = value;
            return this;
        }

        public Builder films(List<String> value) {
            films = value;
            return this;
        }

        public Builder indications(List<Indication> value) {
            indications = value;
            return this;
        }

        public Builder verdict(SheetVerdict value) {
            verdict = value;
            return this;
        }

        public Builder remarks(String value) {
            remarks = value;
            return this;
        }

        public SheetInput build() {
            return new SheetInput(
                    job,
                    jointId,
                    day,
                    reader,
                    readerLevel,
                    code,
                    technique,
                    wallIn,
                    odIn,
                    films,
                    indications,
                    verdict,
                    remarks);
        }
    }
}
