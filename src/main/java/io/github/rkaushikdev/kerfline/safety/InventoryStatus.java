package io.github.rkaushikdev.kerfline.safety;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Whether the source inventory is current on a day: the answer, why not when it is not, the latest
 * inventory on file before that day, and the days left on it when it is current.
 */
public record InventoryStatus(
        boolean current, Optional<String> reason, Optional<PhysicalInventory> latest, OptionalInt daysLeft) {

    public InventoryStatus {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(latest, "latest");
        Objects.requireNonNull(daysLeft, "daysLeft");
    }
}
