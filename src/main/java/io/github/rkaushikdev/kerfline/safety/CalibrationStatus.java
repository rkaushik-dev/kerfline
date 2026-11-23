package io.github.rkaushikdev.kerfline.safety;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Whether a survey meter is in calibration on a day: the answer, why not when it is not, the latest
 * calibration on file before that day, and the days left on it when it is current.
 */
public record CalibrationStatus(
        boolean current, Optional<String> reason, Optional<MeterCalibration> latest, OptionalInt daysLeft) {

    public CalibrationStatus {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(latest, "latest");
        Objects.requireNonNull(daysLeft, "daysLeft");
    }
}
