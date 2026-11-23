package io.github.rkaushikdev.kerfline.report;

import io.github.rkaushikdev.kerfline.safety.MeterCalibration;
import io.github.rkaushikdev.kerfline.safety.SurveyRecord;
import io.github.rkaushikdev.kerfline.store.Journal;
import java.util.List;

/**
 * What a daily field report is built from: the job if named, the day, the journal, the crew by
 * name, the surveys on file, the serial of the source that left the store that day, or null when
 * none did, and the survey meter calibrations when they were brought along, or null when the meters
 * were not checked.
 */
public record DailyRequest(
        String job,
        String day,
        Journal journal,
        List<String> crew,
        List<SurveyRecord> surveys,
        String source,
        List<MeterCalibration> calibrations) {

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .job(job)
                .day(day)
                .journal(journal)
                .crew(crew)
                .surveys(surveys)
                .source(source)
                .calibrations(calibrations);
    }

    /** Builds a {@link DailyRequest}; the crew and surveys start empty and no source is out. */
    public static final class Builder {
        private String job;
        private String day;
        private Journal journal;
        private List<String> crew = List.of();
        private List<SurveyRecord> surveys = List.of();
        private String source;
        private List<MeterCalibration> calibrations;

        private Builder() {}

        public Builder job(String value) {
            this.job = value;
            return this;
        }

        public Builder day(String value) {
            this.day = value;
            return this;
        }

        public Builder journal(Journal value) {
            this.journal = value;
            return this;
        }

        public Builder crew(List<String> value) {
            this.crew = value;
            return this;
        }

        public Builder surveys(List<SurveyRecord> value) {
            this.surveys = value;
            return this;
        }

        public Builder source(String value) {
            this.source = value;
            return this;
        }

        /** The calibration records for the meters the crew carried; a survey off any other meter does not count. */
        public Builder calibrations(List<MeterCalibration> value) {
            this.calibrations = value;
            return this;
        }

        public DailyRequest build() {
            return new DailyRequest(job, day, journal, crew, surveys, source, calibrations);
        }
    }
}
