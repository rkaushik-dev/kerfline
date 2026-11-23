package io.github.rkaushikdev.kerfline.safety;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.util.Dates;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.Predicate;

/**
 * Dose records for the crew.
 *
 * <p>The annual limit is five thousand millirem to the whole body. The shop does not run anybody
 * up to it: there are two investigation levels well below, and crossing either one starts a
 * conversation rather than stopping work. That is the entire point of setting them where they
 * are.
 *
 * <p>A declared pregnant worker is held to a second limit, five hundred millirem to the embryo or
 * fetus over the whole pregnancy, which runs from conception rather than from the day she told
 * anybody.
 *
 * <p>Two things here are deliberately awkward. A quarterly badge that has not come back from the
 * reader is not a zero, so an unreturned badge makes the total unknown rather than making it
 * smaller. And the pocket dosimeter is not the record of dose; it is the thing that tells a
 * technician during the shift that something is wrong. When the two disagree the badge is the
 * record and the disagreement is the finding.
 */
public final class Dosimetry {

    private static final double ANNUAL_LIMIT_MREM = 5000;
    private static final double LEVEL_ONE = 0.1;
    private static final double LEVEL_TWO = 0.3;
    private static final double DEFAULT_TOLERANCE = 0.3;
    private static final double GESTATION_LIMIT_MREM = 500;
    private static final double LATE_DECLARATION_MREM = 50;

    private Dosimetry() {}

    /** The annual whole body limit, five thousand millirem. */
    public static double annualLimitMrem() {
        return ANNUAL_LIMIT_MREM;
    }

    public static InvestigationLevels investigationLevels() {
        return new InvestigationLevels(ANNUAL_LIMIT_MREM * LEVEL_ONE, ANNUAL_LIMIT_MREM * LEVEL_TWO);
    }

    /** A returned badge with its reading. */
    public static BadgeRecord makeBadgeRecord(String person, String from, String to, double mrem) {
        return makeBadgeRecord(person, from, to, mrem, true);
    }

    /**
     * A badge period. A returned badge needs a reading of at least nought; a badge that has not
     * come back must carry none, so pass null for its reading.
     */
    public static BadgeRecord makeBadgeRecord(String person, String from, String to, Double mrem, boolean returned) {
        String name = Guard.requireLabel(person, "a dose record names the person it belongs to");
        Dates.requireDay(from, "period start");
        Dates.requireDay(to, "period end");
        if (to.compareTo(from) < 0) {
            throw new BadInputException(
                    "a badge period cannot end before it starts", Details.of("from", from, "to", to));
        }
        if (returned) {
            double reading = Guard.requireNumber(mrem, "recorded dose");
            Guard.requireAtLeast(reading, 0, "recorded dose");
            return new BadgeRecord(name, from, to, OptionalDouble.of(Num.round(reading, 1)), true);
        }
        if (mrem != null) {
            throw new BadInputException(
                    "a badge that has not come back cannot carry a reading", Details.of("mrem", mrem));
        }
        return new BadgeRecord(name, from, to, OptionalDouble.empty(), false);
    }

    /**
     * The dose in a window, from every badge period that overlaps it, including one that runs right
     * across it: an annual badge worn through a quarter is in that quarter. An unreturned badge makes
     * the total unknown, which is reported rather than quietly dropped.
     */
    public static DosePeriod doseInPeriod(List<BadgeRecord> records, String from, String to) {
        Guard.requireArray(records, "dose records");
        Dates.requireDay(from, "period start");
        Dates.requireDay(to, "period end");
        return sum(
                records,
                from,
                to,
                record -> record.from().compareTo(to) <= 0 && record.to().compareTo(from) >= 0);
    }

    /** The badges a test picks out, added up, with any still out making the total incomplete. */
    private static DosePeriod sum(List<BadgeRecord> records, String from, String to, Predicate<BadgeRecord> counts) {
        List<BadgeRecord> counted = records.stream().filter(counts).toList();
        int outstanding =
                (int) counted.stream().filter(record -> !record.returned()).count();
        List<Double> known = counted.stream()
                .filter(BadgeRecord::returned)
                .map(record -> record.mrem().orElseThrow())
                .toList();
        return new DosePeriod(from, to, counted.size(), Num.round(Num.sum(known), 1), outstanding, outstanding == 0);
    }

    /** The limit on the dose to the embryo or fetus over the whole pregnancy, five hundred millirem. */
    public static double gestationLimitMrem() {
        return GESTATION_LIMIT_MREM;
    }

    /**
     * A declared pregnant worker's standing. The limit covers the whole pregnancy, so the dose
     * before the declaration counts against it. When that dose was already within fifty millirem of
     * the limit by the day she declared, the rest of the pregnancy is allowed fifty millirem more
     * whatever the total comes to. A badge counts toward the part of the pregnancy its period ends
     * in, so one running across the declaration is held against the allowance after it.
     */
    public static GestationStanding judgeGestation(
            List<BadgeRecord> records, String conceivedOn, String declaredOn, String endingOn) {
        Guard.requireArray(records, "dose records");
        Dates.requireDay(conceivedOn, "estimated conception date");
        Dates.requireDay(declaredOn, "declaration date");
        Dates.requireDay(endingOn, "date");
        if (declaredOn.compareTo(conceivedOn) < 0 || endingOn.compareTo(declaredOn) < 0) {
            throw new BadInputException(
                    "a pregnancy is declared after conception and judged after the declaration",
                    Details.of("conceivedOn", conceivedOn, "declaredOn", declaredOn, "endingOn", endingOn));
        }
        DosePeriod before = sum(
                records,
                conceivedOn,
                declaredOn,
                record -> record.to().compareTo(conceivedOn) >= 0 && record.to().compareTo(declaredOn) < 0);
        DosePeriod since = sum(
                records,
                declaredOn,
                endingOn,
                record -> record.to().compareTo(declaredOn) >= 0 && record.to().compareTo(endingOn) <= 0);
        double allowed = before.mrem() >= GESTATION_LIMIT_MREM - LATE_DECLARATION_MREM
                ? LATE_DECLARATION_MREM
                : Num.round(GESTATION_LIMIT_MREM - before.mrem(), 1);
        DoseStanding standing =
                since.mrem() > allowed ? DoseStanding.OVER_THE_GESTATION_LIMIT : DoseStanding.WITHIN_LIMITS;
        return new GestationStanding(
                before,
                since,
                allowed,
                Num.round(Math.max(0, allowed - since.mrem()), 1),
                before.complete() && since.complete() ? standing : DoseStanding.INCOMPLETE_RECORD);
    }

    /** The rolling twelve month dose ending on a day. */
    public static DosePeriod annualDose(List<BadgeRecord> records, String endingOn) {
        Dates.requireDay(endingOn, "date");
        return doseInPeriod(records, Dates.addYears(endingOn, -1), endingOn);
    }

    /**
     * Judge a person's standing. An incomplete record never reports "within limits", because the
     * missing badge is exactly where the dose would be.
     */
    public static AnnualStanding judgeAnnual(List<BadgeRecord> records, String endingOn) {
        DosePeriod period = annualDose(records, endingOn);
        InvestigationLevels levels = investigationLevels();
        return new AnnualStanding(
                period,
                ANNUAL_LIMIT_MREM,
                levels,
                annualStanding(period, levels),
                Num.round(Math.max(0, ANNUAL_LIMIT_MREM - period.mrem()), 1),
                Num.round(period.mrem() / ANNUAL_LIMIT_MREM, 4));
    }

    /** The rung of the ladder a complete year reaches; an incomplete one is never placed on it. */
    private static DoseStanding annualStanding(DosePeriod period, InvestigationLevels levels) {
        if (!period.complete()) {
            return DoseStanding.INCOMPLETE_RECORD;
        }
        if (period.mrem() > ANNUAL_LIMIT_MREM) {
            return DoseStanding.OVER_THE_ANNUAL_LIMIT;
        }
        if (period.mrem() > levels.levelTwoMrem()) {
            return DoseStanding.LEVEL_TWO_INVESTIGATION;
        }
        return period.mrem() > levels.levelOneMrem()
                ? DoseStanding.LEVEL_ONE_INVESTIGATION
                : DoseStanding.WITHIN_LIMITS;
    }

    public static PocketComparison comparePocket(double badgeMrem, double pocketMrem) {
        return comparePocket(badgeMrem, pocketMrem, DEFAULT_TOLERANCE);
    }

    /**
     * Compare a pocket dosimeter against the badge for the same period. A large disagreement is a
     * finding on the equipment or the record keeping, and the badge is what stands either way.
     * The tolerance is a fraction of the larger of the two readings.
     */
    public static PocketComparison comparePocket(double badgeMrem, double pocketMrem, double tolerance) {
        Guard.requireAtLeast(badgeMrem, 0, "badge dose");
        Guard.requireAtLeast(pocketMrem, 0, "pocket dose");
        Guard.requireAtLeast(tolerance, 0, "tolerance");
        double larger = Math.max(badgeMrem, pocketMrem);
        if (larger == 0) {
            return new PocketComparison(true, 0, 0, "both read nothing");
        }
        double difference = Math.abs(badgeMrem - pocketMrem);
        return new PocketComparison(
                difference / larger <= tolerance,
                Num.round(difference, 1),
                badgeMrem,
                "the badge is the record of dose whatever the pocket meter said");
    }
}
