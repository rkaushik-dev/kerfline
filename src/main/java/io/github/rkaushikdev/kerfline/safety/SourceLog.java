package io.github.rkaushikdev.kerfline.safety;

import io.github.rkaushikdev.kerfline.errors.ConflictException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.isotope.Catalog;
import io.github.rkaushikdev.kerfline.isotope.Decay;
import io.github.rkaushikdev.kerfline.isotope.Isotope;
import io.github.rkaushikdev.kerfline.util.Dates;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The source inventory, the utilisation log, the leak test and the quarterly physical inventory.
 *
 * <p>Every sealed source in the shop is on this list from the day it arrives to the day it is
 * shipped back, and the log has to be able to say where it was on any given day in between. That
 * is not paperwork for its own sake: it is the only way to answer the question that gets asked
 * after an incident, which is who had it and what were they doing.
 *
 * <p>The leak test is six monthly and the threshold is five nanocuries of removable contamination.
 * A source over that comes out of service the same day, and there is no margin and no supervisor
 * override on that number. The physical inventory is quarterly and lapses the same way.
 */
public final class SourceLog {

    private static final double LEAK_LIMIT_UCI = 0.005;
    private static final int LEAK_INTERVAL_MONTHS = 6;
    private static final int INVENTORY_INTERVAL_MONTHS = 3;

    private SourceLog() {}

    /** The leak test limit, five nanocuries, in microcuries. A reading at the limit fails. */
    public static double leakLimitMicroCuries() {
        return LEAK_LIMIT_UCI;
    }

    public static int leakIntervalMonths() {
        return LEAK_INTERVAL_MONTHS;
    }

    public static SealedSource makeSource(String serial, String isotopeId, double assayCi, String assayDay) {
        return makeSource(serial, isotopeId, assayCi, assayDay, null);
    }

    /** A source off its certificate. The isotope may be given by label; the camera may be null. */
    public static SealedSource makeSource(
            String serial, String isotopeId, double assayCi, String assayDay, String camera) {
        String id = Guard.requireLabel(serial, "a source is identified by its serial number");
        Isotope source = Catalog.isotope(isotopeId);
        Guard.requireAbove(assayCi, 0, "assay activity");
        Dates.requireDay(assayDay, "assay date");
        return new SealedSource(id, source.id(), Num.round(assayCi, 2), assayDay, Optional.ofNullable(camera));
    }

    /** What the source reads on a given day, in curies, from its certificate. Before the assay it back decays. */
    public static double activityOn(SealedSource source, String day) {
        Dates.requireDay(day, "date");
        Isotope record = Catalog.isotope(source.isotope());
        int days = Dates.daysBetween(source.assayDay(), day);
        return Num.round(Decay.activityAfter(source.assayCi(), record.halfLifeDays(), days), 2);
    }

    /** One entry in the utilisation log; the location may be null. */
    public static SourceUse makeUse(
            String serial, String day, String user, String location, double minutes, int exposures) {
        Dates.requireDay(day, "date");
        String who = Guard.requireLabel(user, "every use of a source names who had it");
        Guard.requireAtLeast(minutes, 0, "time out of shield");
        Guard.requireAtLeast(exposures, 0, "exposure count");
        return new SourceUse(serial, day, who, Optional.ofNullable(location), Num.round(minutes, 1), exposures);
    }

    /** What a source did over a period, both days included. */
    public static Utilisation utilisation(List<SourceUse> uses, String serial, String from, String to) {
        Guard.requireArray(uses, "log entries");
        Dates.requireDay(from, "period start");
        Dates.requireDay(to, "period end");
        List<SourceUse> mine = uses.stream()
                .filter(use -> Objects.equals(use.serial(), serial) && Dates.withinSpan(use.day(), from, to))
                .toList();
        return new Utilisation(
                serial,
                from,
                to,
                mine.size(),
                Num.round(Num.sum(mine.stream().map(SourceUse::minutes).toList()), 1),
                mine.stream().mapToInt(SourceUse::exposures).sum(),
                mine.stream().map(SourceUse::user).distinct().sorted().toList(),
                mine.stream().map(SourceUse::day).distinct().sorted().toList());
    }

    /** Who had a source on a day, which is the question asked after an incident. */
    public static List<Holder> whoHadIt(List<SourceUse> uses, String serial, String day) {
        Guard.requireArray(uses, "log entries");
        Dates.requireDay(day, "date");
        return uses.stream()
                .filter(use -> Objects.equals(use.serial(), serial) && Objects.equals(use.day(), day))
                .map(use -> new Holder(use.user(), use.location(), use.minutes()))
                .toList();
    }

    public static LeakTest makeLeakTest(String serial, String day, double microCuries) {
        return makeLeakTest(serial, day, microCuries, null);
    }

    /**
     * A leak test result. It passes only under the limit, and the next one falls due six months
     * on, clamped onto the end of a short month. The laboratory may be null.
     */
    public static LeakTest makeLeakTest(String serial, String day, double microCuries, String laboratory) {
        Dates.requireDay(day, "test date");
        Guard.requireAtLeast(microCuries, 0, "removable activity");
        boolean pass = microCuries < LEAK_LIMIT_UCI;
        return new LeakTest(
                serial,
                day,
                Num.round(microCuries, 5),
                Optional.ofNullable(laboratory),
                pass,
                Dates.addMonths(day, LEAK_INTERVAL_MONTHS),
                pass ? Optional.empty() : Optional.of("remove the source from service today"));
    }

    /**
     * Whether a source may be used on a day, on the rules every {@link PeriodicCheck} lapses by;
     * when two tests share a day the one listed last is taken.
     */
    public static LeakTestStatus leakTestCurrent(List<LeakTest> tests, String serial, String day) {
        Guard.requireArray(tests, "leak tests");
        Dates.requireDay(day, "date");
        Optional<LeakTest> latest = latestOnOrBefore(
                tests.stream()
                        .filter(test -> Objects.equals(test.serial(), serial))
                        .toList(),
                day);
        Optional<String> lapsed = lapsed(latest, day, "leak test");
        return new LeakTestStatus(lapsed.isEmpty(), lapsed, latest, daysLeft(latest, lapsed, day));
    }

    private static <T extends PeriodicCheck> Optional<T> latestOnOrBefore(List<T> checks, String day) {
        T latest = null;
        for (T check : checks) {
            if (Dates.daysBetween(check.day(), day) >= 0
                    && (latest == null || check.day().compareTo(latest.day()) >= 0)) {
                latest = check;
            }
        }
        return Optional.ofNullable(latest);
    }

    /** Why a check does not hold on a day, or nothing when it does. */
    private static Optional<String> lapsed(Optional<? extends PeriodicCheck> latest, String day, String what) {
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

    private static OptionalInt daysLeft(Optional<? extends PeriodicCheck> latest, Optional<String> lapsed, String day) {
        return lapsed.isPresent()
                ? OptionalInt.empty()
                : OptionalInt.of(Dates.daysBetween(day, latest.orElseThrow().dueBy()));
    }

    /**
     * A physical inventory of the sources. Every source on the books has to be sighted for it to
     * pass, and one that was not is a lost source that is reported by telephone the same day. The
     * next inventory falls due three months on. The serials sighted are matched as written.
     */
    public static PhysicalInventory makeInventory(
            String day, String takenBy, List<SealedSource> onTheBooks, List<String> sighted) {
        Dates.requireDay(day, "inventory date");
        String who = Guard.requireLabel(takenBy, "an inventory names who took it");
        Guard.requireArray(onTheBooks, "sources on the books");
        Guard.requireArray(sighted, "sources sighted");
        List<String> missing = onTheBooks.stream()
                .map(SealedSource::serial)
                .filter(serial -> !sighted.contains(serial))
                .toList();
        return new PhysicalInventory(
                day, who, sighted, missing, missing.isEmpty(), Dates.addMonths(day, INVENTORY_INTERVAL_MONTHS));
    }

    public static int inventoryIntervalMonths() {
        return INVENTORY_INTERVAL_MONTHS;
    }

    /**
     * Whether the source inventory is current on a day. An inventory that left a source unaccounted
     * for is a failed one, and it stays failed until a later inventory finds everything.
     */
    public static InventoryStatus inventoryCurrent(List<PhysicalInventory> inventories, String day) {
        Guard.requireArray(inventories, "inventories");
        Dates.requireDay(day, "date");
        Optional<PhysicalInventory> latest = latestOnOrBefore(inventories, day);
        Optional<String> lapsed = lapsed(latest, day, "physical inventory");
        return new InventoryStatus(lapsed.isEmpty(), lapsed, latest, daysLeft(latest, lapsed, day));
    }

    /**
     * Book a source out of the store with its activity for the day.
     *
     * @throws ConflictException when its leak testing is not current, which no override changes
     */
    public static Booking checkOut(SealedSource source, List<LeakTest> tests, String day) {
        LeakTestStatus leak = leakTestCurrent(tests, source.serial(), day);
        if (!leak.current()) {
            String reason = leak.reason().orElseThrow();
            throw new ConflictException(
                    "source " + source.serial() + " cannot go out: " + reason,
                    Details.of("serial", source.serial(), "reason", reason));
        }
        return new Booking(source.serial(), day, activityOn(source, day));
    }

    /** Sources whose leak test is missing, failed, run out or due inside a window, so it can be booked. */
    public static List<LeakTestDue> leakTestsDueBy(
            List<SealedSource> sources, List<LeakTest> tests, String day, double withinDays) {
        Dates.requireDay(day, "date");
        Guard.requireAtLeast(withinDays, 0, "window");
        List<LeakTestDue> due = new ArrayList<>();
        for (SealedSource source : sources) {
            LeakTestStatus leak = leakTestCurrent(tests, source.serial(), day);
            if (!leak.current() || leak.daysLeft().getAsInt() <= withinDays) {
                due.add(new LeakTestDue(
                        source.serial(),
                        leak.latest().map(LeakTest::dueBy),
                        leak.reason().orElse("due for renewal")));
            }
        }
        return List.copyOf(due);
    }
}
