package io.github.rkaushikdev.kerfline.safety;

import java.util.Objects;
import java.util.Optional;

/**
 * A leak test result: the source, the day the wipe was taken, the removable activity in
 * microcuries, the laboratory when named, whether it passed, when the next one is due, and what to
 * do about a failure.
 */
public record LeakTest(
        String serial,
        String day,
        double microCuries,
        Optional<String> laboratory,
        boolean pass,
        String dueBy,
        Optional<String> action)
        implements PeriodicCheck {

    public LeakTest {
        Objects.requireNonNull(laboratory, "laboratory");
        Objects.requireNonNull(action, "action");
    }
}
