package io.github.rkaushikdev.kerfline.cli;

import static io.github.rkaushikdev.kerfline.util.Text.pair;

import io.github.rkaushikdev.kerfline.accept.CodeFailure;
import io.github.rkaushikdev.kerfline.accept.CodeJudgement;
import io.github.rkaushikdev.kerfline.accept.CodeRun;
import io.github.rkaushikdev.kerfline.accept.Disposition;
import io.github.rkaushikdev.kerfline.accept.Engine;
import io.github.rkaushikdev.kerfline.accept.JudgeOptions;
import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.errors.NotFoundException;
import io.github.rkaushikdev.kerfline.indication.Indication;
import io.github.rkaushikdev.kerfline.indication.IndicationInput;
import io.github.rkaushikdev.kerfline.indication.Measure;
import io.github.rkaushikdev.kerfline.isotope.Catalog;
import io.github.rkaushikdev.kerfline.isotope.Decay;
import io.github.rkaushikdev.kerfline.isotope.Isotope;
import io.github.rkaushikdev.kerfline.joint.Joint;
import io.github.rkaushikdev.kerfline.joint.JointInput;
import io.github.rkaushikdev.kerfline.joint.Spec;
import io.github.rkaushikdev.kerfline.report.Technique;
import io.github.rkaushikdev.kerfline.report.TechniqueInput;
import io.github.rkaushikdev.kerfline.report.TechniqueSheet;
import io.github.rkaushikdev.kerfline.safety.Boundary;
import io.github.rkaushikdev.kerfline.safety.Camera;
import io.github.rkaushikdev.kerfline.safety.Shipping;
import io.github.rkaushikdev.kerfline.safety.ShippingLabel;
import io.github.rkaushikdev.kerfline.util.Column;
import io.github.rkaushikdev.kerfline.util.Num;
import io.github.rkaushikdev.kerfline.util.Text;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The commands the tool offers.
 *
 * <p>Each one is a pure function from parsed options to text. Nothing here prints, reads a file or
 * looks at the clock; the entry point does all three and hands the results in. That is what makes
 * the commands testable without a filesystem, and it is why a command can be run twice and give
 * the same answer both times.
 *
 * <p>An indication is written on the command line as {@code type:size:position:note}, with the last
 * two optional, which is short enough to type under a truck and unambiguous enough to parse.
 */
public final class Commands {

    private static final List<OptionDef> TECHNIQUE_OPTIONS = List.of(
            option(OptionSpec.named("job").describe("job number")),
            option(OptionSpec.named("joint").fallback("joint").describe("joint number")),
            option(OptionSpec.named("size").describe("nominal pipe size")),
            option(OptionSpec.named("schedule").fallback("40").describe("pipe schedule")),
            option(OptionSpec.named("od").kind(OptionKind.NUMBER).describe("measured outside diameter")),
            option(OptionSpec.named("wall").kind(OptionKind.NUMBER).describe("measured wall")),
            option(OptionSpec.named("isotope").fallback("IR192").describe("source")),
            option(OptionSpec.named("activity")
                    .kind(OptionKind.NUMBER)
                    .required(true)
                    .describe("curies at assay")),
            option(OptionSpec.named("age").kind(OptionKind.NUMBER).fallback(0.0).describe("days since assay")),
            option(OptionSpec.named("distance")
                    .kind(OptionKind.NUMBER)
                    .required(true)
                    .describe("source to object")),
            option(OptionSpec.named("standoff")
                    .kind(OptionKind.NUMBER)
                    .fallback(0.0)
                    .describe("object to film")),
            option(OptionSpec.named("film").fallback("D7")),
            option(OptionSpec.named("screens").fallback("LEAD_STANDARD")),
            option(OptionSpec.named("density").kind(OptionKind.NUMBER).fallback(2.0)),
            option(OptionSpec.named("material").fallback("CARBON_STEEL").describe("what the pipe is")));

    private static final List<OptionDef> JUDGE_OPTIONS = List.of(
            option(OptionSpec.named("joint").fallback("joint")),
            option(OptionSpec.named("code")
                    .fallback("API1104")
                    .describe("one of " + String.join(", ", Engine.codes()))),
            option(OptionSpec.named("size")),
            option(OptionSpec.named("schedule").fallback("40")),
            option(OptionSpec.named("od").kind(OptionKind.NUMBER)),
            option(OptionSpec.named("wall").kind(OptionKind.NUMBER)),
            option(OptionSpec.named("service").fallback("normal")),
            option(OptionSpec.named("loading").fallback("static")),
            option(OptionSpec.named("weld-length")
                    .kind(OptionKind.NUMBER)
                    .describe("weld length when it is not the whole circumference")),
            option(OptionSpec.named("reinforcement")
                    .kind(OptionKind.NUMBER)
                    .describe("cap height Section VIII reads past")),
            option(OptionSpec.named("mating-wall")
                    .kind(OptionKind.NUMBER)
                    .describe("wall of the component on the other side")),
            option(OptionSpec.named("crack-repairs")
                    .kind(OptionKind.BOOLEAN)
                    .describe("the company lets a short crack be repaired")),
            option(OptionSpec.named("all").kind(OptionKind.BOOLEAN).describe("run every code")));

    private static final List<OptionDef> SOURCE_OPTIONS = List.of(
            option(OptionSpec.named("isotope").describe("one source rather than all of them")),
            option(OptionSpec.named("activity")
                    .kind(OptionKind.NUMBER)
                    .fallback(40.0)
                    .describe("curies")),
            option(OptionSpec.named("days")
                    .kind(OptionKind.NUMBER)
                    .fallback(90.0)
                    .describe("days to project")));

    private static final List<OptionDef> SHIP_OPTIONS = List.of(
            option(OptionSpec.named("surface")
                    .kind(OptionKind.NUMBER)
                    .required(true)
                    .describe("highest mrem/h at the package surface")),
            option(OptionSpec.named("metre")
                    .kind(OptionKind.NUMBER)
                    .required(true)
                    .describe("highest mrem/h one metre from the package")));

    private static final String INDICATION_SHAPE = "type:size:position:note";

    private static final Map<String, CliCommand> COMMANDS = commands();

    private Commands() {}

    private static OptionDef option(OptionSpec spec) {
        return Args.defineOption(spec);
    }

    private static Map<String, CliCommand> commands() {
        Map<String, CliCommand> commands = new LinkedHashMap<>();
        for (CliCommand command : List.of(
                new CliCommand(
                        "technique",
                        "work out a technique sheet for one joint",
                        TECHNIQUE_OPTIONS,
                        Commands::technique),
                new CliCommand("judge", "judge a set of indications against a code", JUDGE_OPTIONS, Commands::judge),
                new CliCommand(
                        "sources",
                        "show the source catalog, decay and boundary distances",
                        SOURCE_OPTIONS,
                        Commands::sources),
                new CliCommand(
                        "ship", "label a camera for the road from its survey readings", SHIP_OPTIONS, Commands::ship),
                Tables.tablesCommand())) {
            commands.put(command.name(), command);
        }
        return Collections.unmodifiableMap(commands);
    }

    private static Joint jointFrom(ParsedArgs options) {
        String id = options.text("joint").orElse("joint");
        Optional<String> size = options.text("size");
        OptionalDouble od = options.number("od");
        OptionalDouble wall = options.number("wall");
        if (size.isPresent()) {
            return Spec.makeJoint(JointInput.builder()
                    .id(id)
                    .nominalSize(size.get())
                    .schedule(options.text("schedule").orElse(null))
                    .build());
        }
        if (od.isPresent() && wall.isPresent()) {
            return Spec.makeJoint(JointInput.builder()
                    .id(id)
                    .odIn(od.getAsDouble())
                    .wallIn(wall.getAsDouble())
                    .build());
        }
        Map<String, Object> got = new LinkedHashMap<>();
        got.put("size", size);
        got.put("od", od.isPresent() ? Optional.of(od.getAsDouble()) : Optional.empty());
        got.put("wall", wall.isPresent() ? Optional.of(wall.getAsDouble()) : Optional.empty());
        throw new BadInputException(
                "give either --size with --schedule or --od with --wall",
                Details.of("got", Collections.unmodifiableMap(got)));
    }

    /**
     * Read {@code "SLAG:0.5:3"} into an indication: a type, the size measured on the film, and
     * optionally the position along the weld and the reader's note. The note is how the acceptance
     * rules hear about isolated slag, high-low and crater cracks, so {@code "SLAG:0.3:4:isolated"}
     * and, with no position, {@code "IP:1.5::high-low"} both read.
     *
     * @throws BadInputException when the text is not in that shape, a part is not a number, or the
     *     type is not one anybody reports
     */
    public static Indication parseIndication(String text) {
        if (text == null) {
            throw new BadInputException("an indication is written as " + INDICATION_SHAPE, Details.of("got", null));
        }
        List<String> parts = Text.split(text, ":");
        if (parts.size() < 2 || parts.size() > 4) {
            throw new BadInputException("cannot read " + text + " as " + INDICATION_SHAPE, Details.of("text", text));
        }
        IndicationInput input = IndicationInput.of(parts.get(0), number(text, parts.get(1), "a size"));
        if (parts.size() > 2 && !parts.get(2).isBlank()) {
            input = input.at(number(text, parts.get(2), "a position"));
        }
        if (parts.size() > 3 && !parts.get(3).isBlank()) {
            input = input.note(Text.trim(parts.get(3)));
        }
        return Measure.makeIndication(input);
    }

    private static double number(String text, String part, String what) {
        double value = Num.parse(part);
        if (!Double.isFinite(value)) {
            throw new BadInputException(
                    "cannot read " + text + ", " + part + " is not " + what, Details.of("text", text));
        }
        return value;
    }

    /** What the judge command hands the engine beyond the joint, from whatever options were given. */
    static JudgeOptions judgeOptions(ParsedArgs options) {
        JudgeOptions context = JudgeOptions.NONE
                .service(options.text("service").orElse(null))
                .loading(options.text("loading").orElse(null))
                .crackRepairs(options.flag("crack-repairs"));
        if (options.number("weld-length").isPresent()) {
            context = context.weldLengthIn(options.number("weld-length").getAsDouble());
        }
        if (options.number("reinforcement").isPresent()) {
            context = context.reinforcementIn(options.number("reinforcement").getAsDouble());
        }
        if (options.number("mating-wall").isPresent()) {
            context = context.matingWallIn(options.number("mating-wall").getAsDouble());
        }
        return context;
    }

    private static CommandResult technique(ParsedArgs options) {
        Joint joint = jointFrom(options);
        TechniqueSheet sheet = Technique.buildTechnique(TechniqueInput.builder()
                .job(options.text("job").orElse(null))
                .joint(joint)
                .isotopeId(options.text("isotope").orElse(null))
                .assayCi(options.number("activity").orElse(Double.NaN))
                .daysSinceAssay(options.number("age").orElse(Double.NaN))
                .sourceToObjectIn(options.number("distance").orElse(Double.NaN))
                .objectToFilmIn(options.number("standoff").orElse(Double.NaN))
                .filmId(options.text("film").orElse(null))
                .screens(options.text("screens").orElse(null))
                .targetDensity(options.number("density").orElse(Double.NaN))
                .materialId(options.text("material").orElse(null))
                .build());
        return new CommandResult(Technique.renderTechnique(sheet), sheet.usable() ? 0 : 1);
    }

    private static CommandResult judge(ParsedArgs options) {
        Joint joint = jointFrom(options);
        List<Indication> indications =
                options.positionals().stream().map(Commands::parseIndication).toList();
        JudgeOptions context = judgeOptions(options);

        if (options.flag("all")) {
            List<CodeRun> results = Engine.judgeAll(indications, joint, context);
            List<Map<String, Object>> rows = new ArrayList<>();
            for (CodeRun result : results) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("code", result.code());
                row.put("disposition", result.outcome().map(Disposition::label).orElse("could not run"));
                row.put("reason", firstReason(result));
                rows.add(row);
            }
            String table = Text.renderTable(
                    rows,
                    List.of(
                            Column.of("code").label("code"),
                            Column.of("disposition").label("result"),
                            Column.of("reason").label("first reason").max(44)));
            boolean worst = results.stream()
                    .anyMatch(result ->
                            result.outcome().filter(Disposition.ACCEPT::equals).isEmpty());
            return new CommandResult(table, worst ? 1 : 0);
        }

        CodeJudgement result = Engine.judge(options.text("code").orElse(null), indications, joint, context);
        List<String> lines = new ArrayList<>();
        lines.add(Text.keyValueBlock(List.of(
                pair("Joint", joint.id()),
                pair("Code", result.code()),
                pair("Clause", result.clause()),
                pair("Result", result.disposition().label().toUpperCase(Locale.ROOT)))));
        if (!result.reasons().isEmpty()) {
            lines.add("");
            lines.add("Findings");
            lines.add(Text.rule(40));
            result.reasons().forEach(reason -> lines.add("  - " + reason));
        }
        return new CommandResult(String.join("\n", lines), result.disposition() == Disposition.ACCEPT ? 0 : 1);
    }

    /** The label and transport index for a package; exits 1 when it cannot ship at all. */
    private static CommandResult ship(ParsedArgs options) {
        ShippingLabel label = Shipping.labelFor(
                options.number("surface").orElse(Double.NaN),
                options.number("metre").orElse(Double.NaN));
        List<String> lines = new ArrayList<>();
        lines.add(Text.keyValueBlock(List.of(
                pair("Label", label.label().label()),
                pair("Transport index", Num.fixed(label.transportIndex(), 1)),
                pair("Decided by", label.decidedBy()))));
        label.refusal().ifPresent(reason -> lines.add("Do not ship: " + reason));
        return new CommandResult(String.join("\n", lines), label.shippable() ? 0 : 1);
    }

    /** The first reason a code gave, or its refusal when it could not run, or nothing. */
    private static String firstReason(CodeRun result) {
        List<String> reasons =
                switch (result) {
                    case CodeJudgement judgement -> judgement.reasons();
                    case CodeFailure failure -> failure.message() == null ? List.of() : List.of(failure.message());
                };
        return reasons.stream().filter(reason -> !reason.isEmpty()).findFirst().orElse("");
    }

    private static CommandResult sources(ParsedArgs options) {
        double activity = options.number("activity").orElse(Double.NaN);
        double days = options.number("days").orElse(Double.NaN);
        List<Isotope> wanted = options.text("isotope")
                .filter(name -> !name.isEmpty())
                .map(name -> List.of(Catalog.isotope(name)))
                .orElseGet(Catalog::isotopes);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Isotope source : wanted) {
            double after = Decay.decayTable(source.id(), activity, List.of(days))
                    .get(0)
                    .curies();
            double restrictedFt = Boundary.boundaryDistancesFt(new Camera(source.id(), activity))
                    .restrictedFt();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("isotope", source.label());
            row.put("halfLife", Num.fixed(source.halfLifeDays(), 1));
            row.put("after", after);
            row.put(
                    "steel",
                    Num.text(source.steelRangeIn().min()) + " to "
                            + Num.text(source.steelRangeIn().max()));
            row.put("boundary", Math.round(restrictedFt));
            rows.add(row);
        }
        String table = Text.renderTable(
                rows,
                List.of(
                        Column.of("isotope").label("source"),
                        Column.of("halfLife").label("half life d").right(),
                        Column.of("after")
                                .label("Ci at " + Num.text(days) + " d")
                                .right(),
                        Column.of("steel").label("steel in"),
                        Column.of("boundary").label("rope ft").right()));
        return new CommandResult(table, 0);
    }

    /** Every command, in the order the usage screen lists them. */
    public static List<String> commandNames() {
        return List.copyOf(COMMANDS.keySet());
    }

    /**
     * One command by name.
     *
     * @throws NotFoundException naming the commands there are, when there is no such command
     */
    public static CliCommand command(String name) {
        CliCommand found = name == null ? null : COMMANDS.get(name);
        if (found == null) {
            throw new NotFoundException("no command " + name, Details.of("asked", name, "known", commandNames()));
        }
        return found;
    }

    /** Run one command from raw arguments. */
    public static CommandResult runCommand(String name, List<String> argv) {
        CliCommand spec = command(name);
        return spec.run(Args.parseArgs(argv, spec.options()));
    }

    /** One command's usage screen: what it does, then its options. */
    public static String commandUsage(String name) {
        CliCommand spec = command(name);
        return spec.describe() + "\n\n" + Args.usage(name, spec.options());
    }

    /** The screen listing every command. */
    public static String overallUsage() {
        List<String> lines = new ArrayList<>(List.of("usage: kerf <command> [options]", "", "commands:"));
        for (String name : commandNames()) {
            lines.add("  " + Text.padRight(name, 12) + COMMANDS.get(name).describe());
        }
        lines.add("");
        lines.add("run kerf <command> --help for the options one takes");
        return String.join("\n", lines);
    }
}
