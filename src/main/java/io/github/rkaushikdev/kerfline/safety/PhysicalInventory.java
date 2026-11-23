package io.github.rkaushikdev.kerfline.safety;

import java.util.List;
import java.util.Objects;

/**
 * A physical inventory of the sealed sources: the day it was taken and who took it, the serials
 * actually sighted, the ones on the books that were not, whether everything was accounted for, and
 * when the next one is due. A source that was not sighted is a lost source until it is found.
 */
public record PhysicalInventory(
        String day, String takenBy, List<String> sighted, List<String> missing, boolean pass, String dueBy)
        implements PeriodicCheck {

    public PhysicalInventory {
        Objects.requireNonNull(takenBy, "takenBy");
        sighted = List.copyOf(sighted);
        missing = List.copyOf(missing);
    }
}
