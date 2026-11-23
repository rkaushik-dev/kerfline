package io.github.rkaushikdev.kerfline.safety;

import java.util.Objects;
import java.util.Optional;

/**
 * A survey meter calibration: the instrument, the day, the worst error found against the reference
 * source as a fraction of the true rate, the laboratory when named, whether it passed, and when the
 * next one is due.
 */
public record MeterCalibration(
        String instrument, String day, double worstError, Optional<String> laboratory, boolean pass, String dueBy)
        implements PeriodicCheck {

    public MeterCalibration {
        Objects.requireNonNull(laboratory, "laboratory");
    }
}
