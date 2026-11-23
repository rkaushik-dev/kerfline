package io.github.rkaushikdev.kerfline.safety;

import io.github.rkaushikdev.kerfline.util.Dates;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A check that holds for a fixed time once it has passed and has to be made again before that time
 * runs out: the six monthly leak test, the quarterly physical inventory and the survey meter
 * calibration. What they share is the way they lapse. Only one taken on or before the day counts,
 * the latest of those decides and of two on the same day the one listed last, a failed one never
 * comes good by waiting, and one that has run out is the same as never having had one.
 */
public sealed interface PeriodicCheck permits LeakTest, PhysicalInventory, MeterCalibration {

    /** The day the check was made. */
    String day();

    /** Whether it passed. */
    boolean pass();

    /** The last day it holds for. */
    String dueBy();

    /** The check that decides a day: the latest taken on or before it. */
    static <T extends PeriodicCheck> Optional<T> latestOnOrBefore(List<T> checks, String day) {
        T latest = null;
        for (T check : checks) {
            if (Dates.daysBetween(check.day(), day) >= 0
                    && (latest == null || check.day().compareTo(latest.day()) >= 0)) {
                latest = check;
            }
        }
        return Optional.ofNullable(latest);
    }

    /** Why the check that decides a day does not hold on it, or nothing when it does. */
    static Optional<String> lapsed(Optional<? extends PeriodicCheck> latest, String day, String what) {
        if (latest.isEmpty()) {
            return Optional.of("no " + what + " on file");
        }
        if (!latest.get().pass()) {
            return Optional.of("the last " + what + " failed");
        }
        if (day.compareTo(latest.get().dueBy()) > 0) {
            return Optional.of("the " + what + " has run out");
        }
        return Optional.empty();
    }

    /** The days left on a check that holds, or nothing for one that has lapsed. */
    static OptionalInt daysLeft(Optional<? extends PeriodicCheck> latest, Optional<String> lapsed, String day) {
        return lapsed.isPresent()
                ? OptionalInt.empty()
                : OptionalInt.of(Dates.daysBetween(day, latest.orElseThrow().dueBy()));
    }
}
