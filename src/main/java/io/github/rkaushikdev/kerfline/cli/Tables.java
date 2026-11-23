package io.github.rkaushikdev.kerfline.cli;

import io.github.rkaushikdev.kerfline.accept.Api1104;
import io.github.rkaushikdev.kerfline.accept.Api1104Rule;
import io.github.rkaushikdev.kerfline.accept.Asme8;
import io.github.rkaushikdev.kerfline.density.DensityLimits;
import io.github.rkaushikdev.kerfline.density.Limits;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.errors.NotFoundException;
import io.github.rkaushikdev.kerfline.geom.Unsharpness;
import io.github.rkaushikdev.kerfline.iqi.Selection;
import io.github.rkaushikdev.kerfline.iqi.Wire;
import io.github.rkaushikdev.kerfline.isotope.SourceKind;
import io.github.rkaushikdev.kerfline.joint.Schedule;
import io.github.rkaushikdev.kerfline.util.Column;
import io.github.rkaushikdev.kerfline.util.Num;
import io.github.rkaushikdev.kerfline.util.Text;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The reference tables, printed.
 *
 * <p>A technician at a light box with a question about which plaque the code calls for at three
 * eighths of an inch does not want to run a calculation. They want the table. These are the same
 * tables the rest of the package reads, printed rather than consulted, so there is no second copy
 * to drift.
 *
 * <p>That is the whole point of putting them behind a command instead of in a document. A printed
 * table in a folder is a copy, and a copy of a code table is a copy that will one day disagree
 * with the one the tool applies.
 */
public final class Tables {

    private record ReferenceTable(String describe, Supplier<String> build) {}

    private static final Map<String, ReferenceTable> TABLES = tables();

    private static final CliCommand TABLES_COMMAND = new CliCommand(
            "tables",
            "print one of the reference tables the tool works from",
            List.of(Args.defineOption(OptionSpec.named("table").describe("one of " + String.join(", ", tableNames())))),
            Tables::runTables);

    private Tables() {}

    private static Map<String, ReferenceTable> tables() {
        Map<String, ReferenceTable> tables = new LinkedHashMap<>();
        tables.put(
                "unsharpness",
                new ReferenceTable("geometric unsharpness limits by material thickness", Tables::unsharpnessTable));
        tables.put("iqi", new ReferenceTable("indicator selection by thickness and side", Tables::iqiTable));
        tables.put("density", new ReferenceTable("density limits by source and viewing", Tables::densityTable));
        tables.put(
                "wire", new ReferenceTable("the wire ladder and the set each wire is drawn from", Tables::wireTable));
        tables.put(
                "schedule", new ReferenceTable("pipe outside diameters and wall thicknesses", Tables::scheduleTable));
        tables.put("api1104", new ReferenceTable("API 1104 length caps by imperfection", Tables::api1104Table));
        tables.put(
                "rounded",
                new ReferenceTable("Section VIII Appendix 4 rounded indication limits", Tables::roundedTable));
        return Collections.unmodifiableMap(tables);
    }

    /** One column of a printed table: its heading, whether it sits right, and what a row puts in it. */
    private record Field<T>(String heading, boolean right, Function<T, Object> value) {}

    private static <T> Field<T> left(String heading, Function<T, Object> value) {
        return new Field<>(heading, false, value);
    }

    private static <T> Field<T> right(String heading, Function<T, Object> value) {
        return new Field<>(heading, true, value);
    }

    /** Print rows under the fields, each field reading its own cell off the row. */
    private static <T> String print(List<T> rows, List<Field<T>> fields) {
        List<Column> columns = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
            Column column = Column.of("c" + i).label(fields.get(i).heading());
            columns.add(fields.get(i).right() ? column.right() : column);
        }
        List<Map<String, Object>> cells = new ArrayList<>();
        for (T row : rows) {
            Map<String, Object> cell = new LinkedHashMap<>();
            for (int i = 0; i < fields.size(); i++) {
                cell.put("c" + i, fields.get(i).value().apply(row));
            }
            cells.add(cell);
        }
        return Text.renderTable(cells, columns);
    }

    private static String unsharpnessTable() {
        return print(
                Unsharpness.unsharpnessBands(),
                List.of(
                        right("over in", band -> Num.fixed(band.over(), 2)),
                        right(
                                "through in",
                                band -> band.upTo() == Double.POSITIVE_INFINITY
                                        ? "and above"
                                        : Num.fixed(band.upTo(), 2)),
                        right("Ug limit in", band -> Num.fixed(band.limitIn(), 3))));
    }

    private static String iqiTable() {
        return print(
                        Selection.selectionTable(),
                        List.of(
                                right("over in", band -> Num.fixed(band.over(), 3)),
                                right("through in", band -> Num.fixed(band.upTo(), 3)),
                                right("src hole", band -> band.source().hole()),
                                right("src wire", band -> band.source().wire()),
                                right("film hole", band -> band.film().hole()),
                                right("film wire", band -> band.film().wire())))
                + "\n\nThe essential hole is " + Selection.essentialHole() + " throughout.";
    }

    private static String densityTable() {
        List<DensityLimits> rows = new ArrayList<>();
        for (SourceKind kind : Limits.sourceKinds()) {
            rows.add(Limits.densityLimits(kind, false));
            rows.add(Limits.densityLimits(kind, true));
        }
        return print(
                rows,
                List.of(
                        left("source", limits -> limits.kind().label()),
                        left("viewing", limits -> limits.composite() ? "composite, each film" : "single film"),
                        right("minimum", limits -> Num.fixed(limits.minimum(), 1)),
                        right("maximum", limits -> Num.fixed(limits.maximum(), 1))));
    }

    private static String wireTable() {
        return print(
                Wire.wireNumbers(),
                List.of(
                        right("wire", number -> number),
                        right("diameter in", number -> Num.fixed(Wire.wireDiameterIn(number), 4)),
                        left("set", Wire::preferredSetForWire)));
    }

    private record SizeAndSchedule(String size, String schedule) {}

    private static String scheduleTable() {
        List<SizeAndSchedule> rows = new ArrayList<>();
        for (String size : Schedule.nominalSizes()) {
            Schedule.schedulesFor(size).forEach(schedule -> rows.add(new SizeAndSchedule(size, schedule)));
        }
        return print(
                rows,
                List.of(
                        left("NPS", SizeAndSchedule::size),
                        right("OD in", row -> Num.fixed(Schedule.outsideDiameterIn(row.size()), 3)),
                        left("sch", SizeAndSchedule::schedule),
                        right("wall in", row -> Num.fixed(Schedule.wallThicknessIn(row.size(), row.schedule()), 3))));
    }

    private static String api1104Table() {
        return print(
                        Api1104.lengthRules(),
                        List.of(
                                left("imperfection", Api1104Rule::label),
                                left("clause", Api1104Rule::clause),
                                right("single in", rule -> Num.text(rule.singleIn())),
                                right("in 12 in", rule -> Num.text(rule.windowIn())),
                                left("against the weld", Tables::againstTheWeld)))
                + "\n\nEvery imperfection but high-low and undercut also counts toward 2 in in any 12 in"
                + "\nand 8 percent of the weld.";
    }

    private static String againstTheWeld(Api1104Rule rule) {
        if (rule.wholeWeldFraction().isPresent()) {
            return Num.text(Num.round(rule.wholeWeldFraction().getAsDouble() * 100, 1)) + " percent of any weld";
        }
        if (rule.percent().isPresent()) {
            return Num.text(rule.percent().getAsDouble() * 100) + " percent under 12 in";
        }
        return "";
    }

    private static String roundedTable() {
        return print(
                List.of(0.1, 0.125, 0.25, 0.5, 0.75, 1.0, 2.0, 3.0),
                List.of(
                        right("t in", t -> Num.fixed(t, 3)),
                        right("counts over in", t -> Num.fixed(Asme8.relevantOverIn(t), 4)),
                        right("largest in", t -> Num.fixed(Asme8.roundedLimitIn(t, false), 4)),
                        right("alone in", t -> Num.fixed(Asme8.roundedLimitIn(t, true), 4))));
    }

    /** The tables the command can print, in the order it lists them. */
    public static List<String> tableNames() {
        return List.copyOf(TABLES.keySet());
    }

    /**
     * What a table shows, for the list the command prints.
     *
     * @throws NotFoundException when there is no such table
     */
    public static String tableDescribe(String name) {
        return table(name).describe();
    }

    /**
     * One table, printed from the same data the rest of the package applies.
     *
     * @throws NotFoundException when there is no such table
     */
    public static String renderReferenceTable(String name) {
        return table(name).build().get();
    }

    private static ReferenceTable table(String name) {
        ReferenceTable found = name == null ? null : TABLES.get(name);
        if (found == null) {
            throw new NotFoundException("no table called " + name, Details.of("asked", name, "known", tableNames()));
        }
        return found;
    }

    /** The {@code tables} command: the list of tables, or one table when {@code --table} names it. */
    public static CliCommand tablesCommand() {
        return TABLES_COMMAND;
    }

    private static CommandResult runTables(ParsedArgs parsed) {
        return parsed.text("table")
                .map(name -> new CommandResult(renderReferenceTable(name), 0))
                .orElseGet(() -> {
                    List<String> lines = new ArrayList<>(List.of("tables:"));
                    for (String name : tableNames()) {
                        lines.add("  " + Text.padRight(name, 12) + tableDescribe(name));
                    }
                    return new CommandResult(String.join("\n", lines), 0);
                });
    }
}
