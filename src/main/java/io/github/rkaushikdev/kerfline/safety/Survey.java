package io.github.rkaushikdev.kerfline.safety;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.util.Dates;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Survey records.
 *
 * <p>Three surveys are made around every exposure and they answer different questions. The
 * boundary survey says the rope is in the right place. The perimeter survey during the shot says
 * nobody has walked inside it. The retraction survey says the source went home.
 *
 * <p>That last one is the one that kills people. A source that hangs up in the guide tube reads
 * normal at the boundary, because the crank is out and the shot has ended and everything looks
 * finished, and the only thing that says otherwise is the meter held against the camera as the
 * crew walks back in. So a retraction survey with no reading is not a missing record here, it is a
 * refusal.
 *
 * <p>The meter has to be in calibration for any of it to count. Calibration is six monthly and a
 * meter reading more than twenty percent off the true rate at any check point fails.
 */
public final class Survey {

    private static final List<String> KINDS = List.of("boundary", "perimeter", "retraction");
    private static final double DEFAULT_BACKGROUND_MR = 0.02;
    private static final double DEFAULT_THRESHOLD_MR = 2;
    private static final double BACKGROUND_MULTIPLE = 5;
    private static final int CALIBRATION_MONTHS = 6;
    private static final double CALIBRATION_TOLERANCE = 0.2;

    private Survey() {}

    /** The three surveys every shot needs, in the order they are taken. */
    public static List<String> surveyKinds() {
        return KINDS;
    }

    public static Reading makeReading(double atFt, double rateMrPerHour) {
        return makeReading(atFt, rateMrPerHour, null);
    }

    /**
     * A reading rounded to what a meter can be read to: the distance to a tenth of a foot and the
     * rate to three places. A reading of nothing at all is allowed. The note may be null.
     */
    public static Reading makeReading(double atFt, double rateMrPerHour, String note) {
        Guard.requireAbove(atFt, 0, "distance");
        Guard.requireAtLeast(rateMrPerHour, 0, "dose rate");
        return new Reading(Num.round(atFt, 1), Num.round(rateMrPerHour, 3), Optional.ofNullable(note));
    }

    public static SurveyRecord makeSurvey(String kind, String day, String instrument, List<Reading> readings) {
        return makeSurvey(kind, day, instrument, readings, null);
    }

    /**
     * A survey for the file. Every reading passes through {@link #makeReading}, and a survey with no
     * readings is refused rather than recorded. The surveyor may be null.
     */
    public static SurveyRecord makeSurvey(
            String kind, String day, String instrument, List<Reading> readings, String surveyor) {
        if (kind == null || !KINDS.contains(kind)) {
            throw new BadInputException(
                    "a survey is one of " + String.join(", ", KINDS), Details.of("got", kind, "known", KINDS));
        }
        Dates.requireDay(day, "survey date");
        String meter = Guard.requireLabel(instrument, "a survey names the meter it was taken with");
        Guard.requireArray(readings, "readings");
        if (readings.isEmpty()) {
            throw new BadInputException("a survey with no readings is not a survey", Details.of("kind", kind));
        }
        List<Reading> checked = readings.stream()
                .map(reading -> makeReading(
                        reading.atFt(), reading.rateMrPerHour(), reading.note().orElse(null)))
                .toList();
        return new SurveyRecord(kind, day, meter, Optional.ofNullable(surveyor), checked);
    }

    public static RetractionVerdict judgeRetraction(SurveyRecord survey) {
        return judgeRetraction(survey, DEFAULT_BACKGROUND_MR, DEFAULT_THRESHOLD_MR);
    }

    /**
     * A retraction survey passes when the highest reading at the camera is at or under the
     * threshold. Anything above it means the source is not fully shielded and nobody approaches.
     */
    public static RetractionVerdict judgeRetraction(
            SurveyRecord survey, double backgroundMrPerHour, double thresholdMrPerHour) {
        if (!"retraction".equals(survey.kind())) {
            throw new BadInputException("that is not a retraction survey", Details.of("kind", survey.kind()));
        }
        double highest =
                Num.maxOf(survey.readings().stream().map(Reading::rateMrPerHour).toList());
        boolean home = highest <= thresholdMrPerHour;
        return new RetractionVerdict(
                highest,
                thresholdMrPerHour,
                backgroundMrPerHour,
                home,
                highest <= backgroundMrPerHour * BACKGROUND_MULTIPLE,
                home ? "approach permitted" : "do not approach, the source is not shielded");
    }

    /**
     * A boundary survey passes when at least one reading was taken at or beyond the rope and
     * nothing out there is over the restricted area limit. Hot readings inside the rope are
     * expected and ignored.
     */
    public static BoundaryVerdict judgeBoundary(SurveyRecord survey, double boundaryFt) {
        if (!"boundary".equals(survey.kind())) {
            throw new BadInputException("that is not a boundary survey", Details.of("kind", survey.kind()));
        }
        Guard.requireAbove(boundaryFt, 0, "boundary distance");
        double limit = Boundary.restrictedAreaRate();
        List<Reading> outside = survey.readings().stream()
                .filter(reading -> reading.atFt() >= boundaryFt)
                .toList();
        List<Reading> breaches = outside.stream()
                .filter(reading -> reading.rateMrPerHour() > limit)
                .toList();
        Optional<String> reason;
        if (outside.isEmpty()) {
            reason = Optional.of("no reading was taken at or beyond the rope");
        } else if (breaches.isEmpty()) {
            reason = Optional.empty();
        } else {
            reason = Optional.of("the rope is inside the two milliroentgen line");
        }
        return new BoundaryVerdict(
                boundaryFt, limit, outside.size(), breaches, !outside.isEmpty() && breaches.isEmpty(), reason);
    }

    /**
     * Where the rope should move to, in feet, from the readings taken. Uses the inverse square
     * through the worst reading rather than assuming the calculated distance, because the whole
     * point of surveying is that the calculation missed some scatter. Nought when every reading is
     * clean.
     *
     * @throws IllegalArgumentException when the survey carries no readings at all
     */
    public static double boundaryFromReadings(SurveyRecord survey) {
        List<Reading> readings = survey.readings();
        if (readings.isEmpty()) {
            throw new IllegalArgumentException("a survey with no readings gives no boundary");
        }
        double limit = Boundary.restrictedAreaRate();
        Reading worst = readings.get(0);
        for (Reading reading : readings.subList(1, readings.size())) {
            if (strength(reading) > strength(worst)) {
                worst = reading;
            }
        }
        if (worst.rateMrPerHour() <= 0) {
            return 0;
        }
        return Num.round(worst.atFt() * Math.sqrt(worst.rateMrPerHour() / limit), 1);
    }

    /** What a reading implies about the source, rate times distance squared. */
    private static double strength(Reading reading) {
        return reading.rateMrPerHour() * StrictMath.pow(reading.atFt(), 2);
    }

    /** Any reading that puts somebody in a high radiation area. */
    public static List<Reading> highRadiationReadings(SurveyRecord survey) {
        double limit = Boundary.highRadiationRate();
        return survey.readings().stream()
                .filter(reading -> reading.rateMrPerHour() > limit)
                .toList();
    }

    /** The surveys a shot needs that are not on file, in the order they are taken. */
    public static List<String> missingSurveys(List<SurveyRecord> surveys) {
        Guard.requireArray(surveys, "surveys");
        Set<String> present = surveys.stream().map(SurveyRecord::kind).collect(Collectors.toSet());
        return KINDS.stream().filter(kind -> !present.contains(kind)).toList();
    }

    /**
     * The surveys a shot needs that are not on file or were taken with a meter out of calibration on
     * the day. A reading off a meter nobody can vouch for is not a survey, however good it looks.
     */
    public static List<String> missingSurveys(List<SurveyRecord> surveys, List<MeterCalibration> calibrations) {
        Guard.requireArray(surveys, "surveys");
        return missingSurveys(surveys.stream()
                .filter(survey -> meterCurrent(calibrations, survey.instrument(), survey.day())
                        .current())
                .toList());
    }

    public static int calibrationIntervalMonths() {
        return CALIBRATION_MONTHS;
    }

    /**
     * A survey meter calibration. It passes when every check point read within twenty percent of the
     * true rate, and the next one falls due six months on. The laboratory may be null.
     */
    public static MeterCalibration makeCalibration(
            String instrument, String day, double worstError, String laboratory) {
        String meter = Guard.requireLabel(instrument, "a calibration names the meter it was made on");
        Dates.requireDay(day, "calibration date");
        Guard.requireAtLeast(worstError, 0, "calibration error");
        return new MeterCalibration(
                meter,
                day,
                Num.round(worstError, 3),
                Optional.ofNullable(laboratory),
                worstError <= CALIBRATION_TOLERANCE,
                Dates.addMonths(day, CALIBRATION_MONTHS));
    }

    /** Whether a meter is in calibration on a day, on the rules every {@link PeriodicCheck} lapses by. */
    public static CalibrationStatus meterCurrent(List<MeterCalibration> calibrations, String instrument, String day) {
        Guard.requireArray(calibrations, "calibrations");
        Dates.requireDay(day, "date");
        Optional<MeterCalibration> latest = PeriodicCheck.latestOnOrBefore(
                calibrations.stream()
                        .filter(calibration -> calibration.instrument().equals(instrument))
                        .toList(),
                day);
        Optional<String> lapsed = PeriodicCheck.lapsed(latest, day, "calibration");
        return new CalibrationStatus(lapsed.isEmpty(), lapsed, latest, PeriodicCheck.daysLeft(latest, lapsed, day));
    }
}
