package io.github.rkaushikdev.kerfline.report;

import static io.github.rkaushikdev.kerfline.exposure.Technique.planExposure;
import static io.github.rkaushikdev.kerfline.exposure.Technique.timerSetting;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.errors.KerflineException;
import io.github.rkaushikdev.kerfline.exposure.ExposurePlan;
import io.github.rkaushikdev.kerfline.exposure.TechniqueSetup;
import io.github.rkaushikdev.kerfline.geom.Coverage;
import io.github.rkaushikdev.kerfline.geom.Iso17636;
import io.github.rkaushikdev.kerfline.geom.IsoDistanceCheck;
import io.github.rkaushikdev.kerfline.geom.PlannedShot;
import io.github.rkaushikdev.kerfline.geom.Unsharpness;
import io.github.rkaushikdev.kerfline.geom.UnsharpnessCheck;
import io.github.rkaushikdev.kerfline.geom.UnsharpnessSetup;
import io.github.rkaushikdev.kerfline.geom.Viewing;
import io.github.rkaushikdev.kerfline.geom.ViewingPlan;
import io.github.rkaushikdev.kerfline.iqi.IqiBasis;
import io.github.rkaushikdev.kerfline.iqi.IqiSelection;
import io.github.rkaushikdev.kerfline.iqi.Selection;
import io.github.rkaushikdev.kerfline.joint.Joint;
import io.github.rkaushikdev.kerfline.joint.Material;
import io.github.rkaushikdev.kerfline.safety.Boundary;
import io.github.rkaushikdev.kerfline.safety.Camera;
import io.github.rkaushikdev.kerfline.util.Column;
import io.github.rkaushikdev.kerfline.util.Num;
import io.github.rkaushikdev.kerfline.util.Text;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * The technique sheet.
 *
 * <p>One page that a crew takes to the job and works from. It is assembled by running the same
 * calculations the rest of the package uses rather than by copying numbers, so a sheet cannot
 * disagree with what the calculation would say today. When a chart cannot cover a wall or the
 * geometry will not close, the sheet says so in the place the number would have gone instead of
 * coming out blank, because a blank on a field sheet gets filled in by guesswork.
 *
 * <p>The one thing worth being careful about is that two different thicknesses come out of the
 * same joint. The exposure chart is a steel chart, so it is read at the thickness of steel that
 * absorbs the same amount as this section does. The indicator is chosen on the section itself,
 * because the indicator is made of the same metal and is standing in for it. On steel the two are
 * equal and nothing shows; on aluminium they differ by a factor of five.
 */
public final class Technique {

    private static final int WIDTH = Layout.pageWidth();

    private static final List<Column> SHOT_COLUMNS = List.of(
            Column.of("shot").label("no").right(),
            Column.of("clock").label("centre"),
            Column.of("coversDegrees").label("covers deg").right(),
            Column.of("overlapDegrees").label("overlap deg").right());

    private Technique() {}

    /**
     * Work the sheet out. A calculation that refuses is recorded as a problem against the part it
     * belongs to, and the rest of the sheet still resolves.
     *
     * @throws BadInputException when there is no input or no joint to work from
     */
    public static TechniqueSheet buildTechnique(TechniqueInput input) {
        if (input == null) {
            throw new BadInputException("a technique sheet is built from an object", Details.of("got", null));
        }
        Joint joint = input.joint();
        if (joint == null) {
            throw new BadInputException("a technique sheet needs a joint", Details.of("got", null));
        }

        ViewingPlan viewing = Viewing.viewingPlan(joint.odIn(), joint.wallIn(), input.sourceCanEnter());
        double penetrated = viewing.penetratedThicknessIn();
        List<String> problems = new ArrayList<>();

        // The chart is a steel chart, so it is read at the equivalent steel.
        Optional<Double> chartThicknessIn =
                attempt(problems, "material", () -> Material.equivalentSteelIn(input.materialId(), penetrated));

        Optional<ExposurePlan> exposure = attempt(
                problems,
                "exposure",
                () -> planExposure(TechniqueSetup.builder()
                        .isotopeId(input.isotopeId())
                        .thicknessIn(chartThicknessIn.orElse(penetrated))
                        .distanceIn(input.sourceToObjectIn() + input.objectToFilmIn())
                        .filmId(input.filmId())
                        .screens(input.screens())
                        .targetDensity(input.targetDensity())
                        .assayCi(input.assayCi())
                        .daysSinceAssay(input.daysSinceAssay())
                        .build()));

        Optional<IqiSelection> indicator = attempt(
                problems,
                "indicator",
                () -> Selection.selectForJoint(
                        IqiBasis.builder().wallIn(penetrated).side("source").build()));

        Optional<UnsharpnessCheck> geometry = attempt(
                problems,
                "unsharpness",
                () -> Unsharpness.checkUnsharpness(UnsharpnessSetup.builder()
                        .sourceSizeIn(input.sourceSizeIn())
                        .objectToFilmIn(input.objectToFilmIn())
                        .sourceToObjectIn(input.sourceToObjectIn())
                        .materialThicknessIn(penetrated)
                        .build()));

        Optional<IsoDistanceCheck> isoDistance = input.isoClass() == null
                ? Optional.empty()
                : attempt(
                        problems,
                        "ISO 17636",
                        () -> Iso17636.check(
                                input.sourceToObjectIn(),
                                input.sourceSizeIn(),
                                input.objectToFilmIn(),
                                joint.wallIn(),
                                input.isoClass()));

        Optional<List<PlannedShot>> coverage = attempt(
                problems,
                "coverage",
                () -> Coverage.shotPlan(joint.odIn(), input.sourceToObjectIn(), input.maxThicknessRatio()));

        Optional<Double> barricadeFt = attempt(
                problems,
                "barricade",
                () -> Boundary.barricadeDistanceFt(new Camera(input.isotopeId(), input.assayCi())));

        return new TechniqueSheet(
                Optional.ofNullable(input.job()),
                joint,
                input.materialId(),
                toOptionalDouble(chartThicknessIn),
                viewing,
                exposure,
                indicator,
                geometry,
                isoDistance,
                coverage,
                toOptionalDouble(barricadeFt),
                problems);
    }

    /**
     * Run one part of the sheet. A refusal from the calculation becomes a problem named after the
     * part, and the part is left empty; anything else is a fault and goes on up.
     */
    private static <T> Optional<T> attempt(List<String> problems, String what, Supplier<T> run) {
        try {
            return Optional.of(run.get());
        } catch (KerflineException | IllegalArgumentException refused) {
            problems.add(what + ": " + refused.getMessage());
            return Optional.empty();
        }
    }

    private static OptionalDouble toOptionalDouble(Optional<Double> value) {
        return value.map(OptionalDouble::of).orElse(OptionalDouble.empty());
    }

    /**
     * The material as a name, or as whatever was asked for when that is not a material this shop
     * knows. A sheet that names an unknown metal has already recorded that as a problem, and
     * refusing to print it as well would mean the only way to see the mistake is to be unable to
     * look at the sheet.
     */
    private static String materialLabel(String materialId) {
        try {
            return Material.material(materialId).label();
        } catch (KerflineException unknown) {
            return Text.str(materialId) + " (not on the material list)";
        }
    }

    private static String missing(String what) {
        return "not established (" + what + ")";
    }

    /** Print the sheet, with a not-ready banner and the problems at the foot when it did not resolve. */
    public static String renderTechnique(TechniqueSheet sheet) {
        Joint joint = sheet.joint();
        ViewingPlan viewing = sheet.viewing();

        String head = Layout.heading("RADIOGRAPHIC TECHNIQUE", WIDTH)
                + "\n"
                + Text.keyValueBlock(List.of(
                        Text.pair("Job", sheet.job().orElse("")),
                        Text.pair("Joint", joint.id()),
                        Text.pair(
                                "Pipe",
                                Num.fixed(joint.odIn(), 3) + " in OD x " + Num.fixed(joint.wallIn(), 3) + " wall"),
                        Text.pair("Viewing", viewing.label()),
                        Text.pair("Penetrated", Num.fixed(viewing.penetratedThicknessIn(), 3) + " in"),
                        Text.pair("Material", materialLabel(sheet.materialId())),
                        Text.pair(
                                "Chart read at",
                                sheet.chartThicknessIn().isEmpty()
                                        ? ""
                                        : Num.fixed(sheet.chartThicknessIn().getAsDouble(), 3) + " in steel")));

        String source = Layout.block(
                "SOURCE AND EXPOSURE",
                sheet.exposure().map(Technique::exposureLines).orElse(missing("the chart does not cover this wall")));

        String quality = Layout.block(
                "IMAGE QUALITY",
                sheet.indicator().isPresent() && sheet.geometry().isPresent()
                        ? qualityLines(sheet.indicator().get(), sheet.geometry().get(), sheet.isoDistance())
                        : missing("the indicator or the geometry did not resolve"));

        String shots = Layout.block(
                "SHOTS",
                sheet.coverage()
                        .map(plan -> Text.renderTable(shotRows(plan), SHOT_COLUMNS))
                        .orElse(missing("the coverage did not resolve")));

        String safety = Layout.block(
                "SAFETY",
                Text.keyValueBlock(List.of(
                        Text.pair(
                                "Barricade",
                                sheet.barricadeFt().isEmpty()
                                        ? missing("no source")
                                        : Num.text(sheet.barricadeFt().getAsDouble()) + " ft"),
                        Text.pair("Minimum exposures", String.valueOf(viewing.minimumExposures())))));

        List<String> blocks = new ArrayList<>(List.of(head, source, quality, shots, safety));
        if (!sheet.usable()) {
            blocks.add(0, Layout.banner("NOT READY - see the notes at the foot", WIDTH));
            blocks.add(Layout.block(
                    "NOTES",
                    String.join(
                            "\n",
                            sheet.problems().stream().map(line -> "  - " + line).toList())));
        }
        return Layout.page(blocks);
    }

    private static String exposureLines(ExposurePlan exposure) {
        return Text.keyValueBlock(List.of(
                Text.pair("Isotope", exposure.isotope()),
                Text.pair("Activity", Num.text(exposure.activityCi()) + " Ci"),
                Text.pair("Film", exposure.film() + " with " + exposure.screens()),
                Text.pair("Density", Num.text(exposure.targetDensity())),
                Text.pair("Distance", Num.fixed(exposure.distanceIn(), 1) + " in"),
                Text.pair("Exposure", Num.text(exposure.curieMinutes()) + " Ci min"),
                Text.pair(
                        "Time",
                        Num.text(exposure.minutes()) + " min (set " + timerSetting(exposure.minutes()) + " s)")));
    }

    private static String qualityLines(
            IqiSelection indicator, UnsharpnessCheck geometry, Optional<IsoDistanceCheck> isoDistance) {
        List<Map.Entry<String, Object>> lines = new ArrayList<>(List.of(
                Text.pair("Indicator", indicator.holeIdentity() + " hole type, " + indicator.essentialHole()),
                Text.pair("Wire", indicator.wireNumber() + " of set " + indicator.wireSet()),
                Text.pair(
                        "Unsharpness",
                        Num.text(geometry.unsharpnessIn()) + " in against " + Num.text(geometry.limitIn()) + " in"),
                Text.pair(
                        "Geometry",
                        geometry.pass() ? "acceptable" : "short by " + Num.text(geometry.shortfallIn()) + " in")));
        isoDistance.ifPresent(check -> lines.add(Text.pair(
                "ISO 17636",
                check.testClass().label() + " needs " + Num.text(check.minimumIn()) + " in, "
                        + (check.pass() ? "met" : "short by " + Num.text(check.shortfallIn()) + " in"))));
        return Text.keyValueBlock(lines);
    }

    private static List<Map<String, Object>> shotRows(List<PlannedShot> plan) {
        return plan.stream()
                .map(shot -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("shot", shot.shot());
                    row.put("clock", shot.clock());
                    row.put("coversDegrees", shot.coversDegrees());
                    row.put("overlapDegrees", shot.overlapDegrees());
                    return row;
                })
                .toList();
    }

    /** A one line summary for a job folder index. */
    public static TechniqueSummary summariseTechnique(TechniqueSheet sheet) {
        return new TechniqueSummary(
                sheet.joint().id(),
                sheet.materialId(),
                sheet.viewing().technique(),
                sheet.exposure().map(ExposurePlan::isotope),
                sheet.exposure()
                        .map(plan -> OptionalDouble.of(Num.round(plan.minutes(), 2)))
                        .orElse(OptionalDouble.empty()),
                sheet.coverage().map(plan -> OptionalInt.of(plan.size())).orElse(OptionalInt.empty()),
                sheet.usable());
    }
}
