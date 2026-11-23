package io.github.rkaushikdev.kerfline.indication;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.geom.Distance;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.ToDoubleFunction;

/**
 * Sizing an indication.
 *
 * <p>Everything measured on a film is measured on a projection, so a length read off the light box
 * is longer than the thing in the weld by the magnification of the setup. On a contact shot that
 * factor is 1 and it does not matter. On a double wall shot of small bore it is well over 1.2 and a
 * quarter inch indication reads as three tenths, which is the difference between a weld that stays
 * in and a weld that comes out.
 *
 * <p>So a measurement carries the factor it was taken at, and the true size is derived rather than
 * stored. Storing the true size would lose the ability to go back to the film and check.
 */
public final class Measure {

    private Measure() {}

    /**
     * An indication from what the reader wrote: the type looked up, the measurement and any width
     * backed out through the magnification, both kept to four places.
     */
    public static Indication makeIndication(IndicationInput input) {
        if (input == null) {
            throw new BadInputException("an indication must be described by an object", Details.of("got", null));
        }
        IndicationType type = Types.indicationType(input.type());
        double magnification = input.magnification() == null ? 1 : input.magnification();
        Guard.requireAtLeast(magnification, 1, "magnification");

        double measuredIn = input.measuredIn();
        Guard.requireAtLeast(measuredIn, 0, "measured size");

        Double widthIn = input.widthIn();
        if (widthIn != null) {
            Guard.requireAtLeast(widthIn, 0, "measured width");
        }

        return new Indication(
                type,
                type.label(),
                type.planar(),
                type.measure(),
                Num.round(measuredIn, 4),
                Num.round(magnification, 4),
                Num.round(Distance.actualLength(measuredIn, magnification), 4),
                widthIn == null
                        ? OptionalDouble.empty()
                        : OptionalDouble.of(Num.round(Distance.actualLength(widthIn, magnification), 4)),
                input.at() == null ? OptionalDouble.empty() : OptionalDouble.of(input.at()),
                Optional.ofNullable(input.film()),
                Optional.ofNullable(input.note()),
                OptionalDouble.empty(),
                List.of());
    }

    /**
     * The dimension the code limit for this indication is read against: the larger of length and
     * width for something judged on its diameter, the length otherwise.
     */
    public static double governingSizeIn(Indication indication) {
        if (indication.type().measure() == MeasuredBy.DIAMETER
                && indication.widthIn().isPresent()) {
            return Math.max(indication.sizeIn(), indication.widthIn().getAsDouble());
        }
        return indication.sizeIn();
    }

    /**
     * Total length of a set of indications. Used by every code paragraph that caps cumulative
     * length in a run of weld, and it deliberately counts only the ones measured by length: adding
     * a pore diameter to a slag line length gives a number with no meaning.
     */
    public static double cumulativeLengthIn(List<Indication> indications) {
        Guard.requireArray(indications, "indications");
        List<Double> lengths = indications.stream()
                .filter(indication -> indication.type().measure() == MeasuredBy.LENGTH)
                .map(Indication::sizeIn)
                .toList();
        return Num.round(Num.sum(lengths), 4);
    }

    /** The longest single indication in a set by its governing size, or zero when there are none. */
    public static double longestIn(List<Indication> indications) {
        Guard.requireArray(indications, "indications");
        if (indications.isEmpty()) {
            return 0;
        }
        double longest = Double.NEGATIVE_INFINITY;
        for (Indication indication : indications) {
            longest = Math.max(longest, governingSizeIn(indication));
        }
        return Num.round(longest, 4);
    }

    /** Every indication whose governing size is over a limit; a size on the limit is inside it. */
    public static List<Indication> overLimit(List<Indication> indications, double limitIn) {
        Guard.requireArray(indications, "indications");
        Guard.requireAtLeast(limitIn, 0, "limit");
        return indications.stream()
                .filter(indication -> governingSizeIn(indication) > limitIn)
                .toList();
    }

    /**
     * Cumulative length inside a sliding window of weld, which is the shape most cumulative limits
     * actually take: not "in the whole joint" but "in any six inches of it". Returns the worst
     * window found, starting at a located indication, and where it started. Only the indications
     * measured by length count towards the total, for the reason {@link #cumulativeLengthIn} gives.
     * Indications with no position take no part.
     */
    public static WeldWindow worstWindow(List<Indication> indications, double windowIn) {
        return worstWindow(indications, windowIn, Measure::lengthOf);
    }

    /** The worst window measured by {@code sizeOf} rather than by length, on a weld with two ends. */
    public static WeldWindow worstWindow(
            List<Indication> indications, double windowIn, ToDoubleFunction<Indication> sizeOf) {
        return heaviest(indications, windowIn, OptionalDouble.empty(), sizeOf);
    }

    /**
     * The worst window on a girth weld, which has no ends. A window that starts a few inches short of
     * the tie-in carries on past it and picks up what lies just after zero, so two short runs either
     * side of the start mark are read as the one run they are in the pipe. A window as long as the
     * weld or longer holds all of it.
     */
    public static WeldWindow worstWindowRound(List<Indication> indications, double windowIn, double circumferenceIn) {
        return worstWindowRound(indications, windowIn, circumferenceIn, Measure::lengthOf);
    }

    /** The worst window round a girth weld, measured by {@code sizeOf}; counting is a size of one each. */
    public static WeldWindow worstWindowRound(
            List<Indication> indications,
            double windowIn,
            double circumferenceIn,
            ToDoubleFunction<Indication> sizeOf) {
        Guard.requireAbove(circumferenceIn, 0, "weld length round the joint");
        return heaviest(indications, windowIn, OptionalDouble.of(circumferenceIn), sizeOf);
    }

    private static double lengthOf(Indication indication) {
        return indication.type().measure() == MeasuredBy.LENGTH ? indication.sizeIn() : 0;
    }

    private static WeldWindow heaviest(
            List<Indication> indications,
            double windowIn,
            OptionalDouble circumferenceIn,
            ToDoubleFunction<Indication> sizeOf) {
        Guard.requireArray(indications, "indications");
        Guard.requireAbove(windowIn, 0, "window length");
        List<Indication> placed =
                indications.stream().filter(i -> i.at().isPresent()).toList();
        if (placed.isEmpty()) {
            return new WeldWindow(0, 0, List.of());
        }
        WeldWindow best = null;
        for (Indication anchor : placed) {
            double startIn = anchor.at().getAsDouble();
            List<Indication> inside = placed.stream()
                    .filter(i -> aheadOf(startIn, i.at().getAsDouble(), circumferenceIn) < windowIn)
                    .toList();
            double sum = 0;
            for (Indication member : inside) {
                sum += sizeOf.applyAsDouble(member);
            }
            double totalIn = Num.round(sum, 4);
            if (best == null || totalIn > best.totalIn()) {
                best = new WeldWindow(totalIn, Num.round(startIn, 4), inside);
            }
        }
        return best;
    }

    /**
     * How far along the weld a position lies past the start of a window. Behind the start is never
     * inside on a weld with ends; on a girth weld it is inside once the window has gone round.
     */
    private static double aheadOf(double startIn, double positionIn, OptionalDouble circumferenceIn) {
        double ahead = positionIn - startIn;
        if (circumferenceIn.isEmpty()) {
            return ahead >= 0 ? ahead : Double.POSITIVE_INFINITY;
        }
        double round = circumferenceIn.getAsDouble();
        return ((ahead % round) + round) % round;
    }
}
