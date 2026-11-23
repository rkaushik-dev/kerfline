package io.github.rkaushikdev.kerfline.safety;

import java.util.Optional;

/**
 * What a camera going out in a package is labelled: the readings it was worked from, the transport
 * index, the label, which of the two readings decided it, and why it cannot ship at all when the
 * surface reading is past even the exclusive use limit.
 */
public record ShippingLabel(
        double surfaceMremPerHour,
        double atOneMetreMremPerHour,
        double transportIndex,
        PackageLabel label,
        String decidedBy,
        Optional<String> refusal) {

    /** True when the package can go on the road under the label given. */
    public boolean shippable() {
        return refusal.isEmpty();
    }
}
