package io.github.rkaushikdev.kerfline.safety;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.List;
import java.util.Optional;

/**
 * Labelling a camera for the road.
 *
 * <p>A loaded exposure device travels as a radioactive material package, and the label comes from
 * two readings taken on the package: the highest dose rate at its surface, and the highest at one
 * metre from it. The reading at one metre, rounded up to the next tenth, is the transport index, and
 * anything at or under five hundredths counts as nought.
 *
 * <p>The two readings each point at a category and the higher of the two is the label. That is the
 * part people get wrong: a camera with a transport index of 0.4 is Yellow-II by the index, and it is
 * Yellow-III if one face reads 60 at contact, however low the index is.
 */
public final class Shipping {

    private static final double NOUGHT_UP_TO = 0.05;

    private Shipping() {}

    /** The transport index from the highest reading at one metre, rounded up to the next tenth. */
    public static double transportIndex(double atOneMetreMremPerHour) {
        Guard.requireAtLeast(atOneMetreMremPerHour, 0, "dose rate at one metre");
        if (atOneMetreMremPerHour <= NOUGHT_UP_TO) {
            return 0;
        }
        return Num.round(Math.ceil(Num.round(atOneMetreMremPerHour * 10, 6)) / 10, 1);
    }

    /** The categories in order, lowest first. */
    public static List<PackageLabel> labels() {
        return List.of(PackageLabel.values());
    }

    /**
     * The label for a package from its two readings. The surface reading and the index each reach a
     * category, and the package carries the higher. A surface over a thousand millirem an hour cannot
     * be shipped even as exclusive use.
     */
    public static ShippingLabel labelFor(double surfaceMremPerHour, double atOneMetreMremPerHour) {
        Guard.requireAtLeast(surfaceMremPerHour, 0, "dose rate at the surface");
        double index = transportIndex(atOneMetreMremPerHour);
        if (atOneMetreMremPerHour > surfaceMremPerHour) {
            throw new BadInputException(
                    "a package cannot read less at its surface than a metre away",
                    Details.of("surface", surfaceMremPerHour, "atOneMetre", atOneMetreMremPerHour));
        }
        PackageLabel byIndex = labels().stream()
                .filter(label -> index <= label.transportIndexUpTo())
                .findFirst()
                .orElseThrow();
        Optional<PackageLabel> bySurface = labels().stream()
                .filter(label -> surfaceMremPerHour <= label.surfaceUpToMremPerHour())
                .findFirst();
        PackageLabel surfaceLabel = bySurface.orElse(PackageLabel.EXCLUSIVE_USE);
        boolean surfaceDecides = surfaceLabel.ordinal() > byIndex.ordinal();
        PackageLabel label = surfaceDecides ? surfaceLabel : byIndex;
        Optional<String> refusal = bySurface.isPresent()
                ? Optional.empty()
                : Optional.of("the surface reads " + Num.text(surfaceMremPerHour)
                        + " mrem/h, over the exclusive use limit of "
                        + Num.text(PackageLabel.EXCLUSIVE_USE.surfaceUpToMremPerHour()));
        return new ShippingLabel(
                Num.round(surfaceMremPerHour, 2),
                Num.round(atOneMetreMremPerHour, 2),
                index,
                label,
                surfaceDecides ? "surface" : "transport index",
                refusal);
    }
}
