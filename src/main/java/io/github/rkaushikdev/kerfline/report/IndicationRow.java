package io.github.rkaushikdev.kerfline.report;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * One line of the findings table: its number round the weld, what the indication is, where it sits
 * when located and, when the pipe size is known, the clock position the repair crew marks, its
 * governing size written to three places, the film it was read on and the reader's note (both blank
 * when not recorded).
 */
public record IndicationRow(
        int item, String type, OptionalDouble at, Optional<String> clock, String sizeIn, String film, String note) {

    public IndicationRow {
        Objects.requireNonNull(clock, "clock");
    }
}
