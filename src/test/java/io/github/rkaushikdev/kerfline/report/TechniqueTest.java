package io.github.rkaushikdev.kerfline.report;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.kerfline.geom.IsoClass;
import io.github.rkaushikdev.kerfline.geom.IsoDistanceCheck;
import io.github.rkaushikdev.kerfline.geom.ViewingTechnique;
import io.github.rkaushikdev.kerfline.joint.Joint;
import io.github.rkaushikdev.kerfline.joint.JointInput;
import io.github.rkaushikdev.kerfline.joint.Spec;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TechniqueTest {

    private static final Joint JOINT = Spec.makeJoint(
            JointInput.builder().id("J-118").nominalSize(6).schedule(40).build());

    private static final Joint THICK =
            Spec.makeJoint(JointInput.builder().id("J-9").odIn(24).wallIn(3).build());

    // A double wall shot stands the film off by the whole diameter, so the
    // source has to be cranked past 40 in to hold the unsharpness limit.
    private static TechniqueInput.Builder base() {
        return TechniqueInput.builder()
                .job("C-4471")
                .joint(JOINT)
                .isotopeId("IR192")
                .assayCi(40)
                .sourceToObjectIn(48)
                .objectToFilmIn(6.625);
    }

    private static TechniqueSheet sheet(TechniqueInput.Builder input) {
        return Technique.buildTechnique(input.build());
    }

    @Nested
    class BuildTechnique {

        private final TechniqueSheet sheet = sheet(base());

        @Test
        void picksTheViewingTechniqueFromTheJoint() {
            assertThat(sheet.viewing().technique()).isEqualTo(ViewingTechnique.DWSI);
            assertThat(sheet.viewing().penetratedThicknessIn()).isCloseTo(0.56, digits(3));
        }

        @Test
        void worksTheExposureOutOnThePenetratedThickness() {
            assertThat(sheet.exposure()).isPresent();
            assertThat(sheet.exposure().get().isotope()).isEqualTo("IR192");
            assertThat(sheet.exposure().get().minutes()).isGreaterThan(0);
        }

        @Test
        void choosesAnIndicator() {
            assertThat(sheet.indicator().get().holeIdentity()).isGreaterThan(0);
            assertThat(sheet.indicator().get().essentialHole()).isEqualTo("2T");
        }

        @Test
        void checksTheGeometry() {
            assertThat(sheet.geometry().get().pass()).isTrue();
            assertThat(sheet.geometry().get().limitIn()).isEqualTo(0.02);
        }

        @Test
        void failsTheGeometryWhenTheSourceIsCrankedInTooClose() {
            TechniqueSheet close = sheet(base().sourceToObjectIn(24));
            assertThat(close.geometry().get().pass()).isFalse();
            assertThat(close.geometry().get().shortfallIn()).isGreaterThan(0);
            assertThat(close.geometry().get().minimumSourceToObjectIn()).isCloseTo(39.75, digits(1));
        }

        @Test
        void plansTheShotsRoundTheJoint() {
            assertThat(sheet.coverage().get().size()).isGreaterThan(1);
            assertThat(sheet.coverage().get().get(0).clock()).isEqualTo("12:00");
        }

        @Test
        void worksOutTheBarricade() {
            assertThat(sheet.barricadeFt().getAsDouble()).isGreaterThan(200);
        }

        @Test
        void isUsableWhenEverythingResolved() {
            assertThat(sheet.usable()).isTrue();
            assertThat(sheet.problems()).isEmpty();
        }

        @Test
        void refusesASheetWithNoJointOnIt() {
            assertThatThrownBy(() -> sheet(base().joint(null))).hasMessageContaining("needs a joint");
            assertThatThrownBy(() -> Technique.buildTechnique(null)).hasMessageContaining("built from an object");
        }
    }

    @Nested
    class BuildTechniqueWhenSomethingWillNotResolve {

        @Test
        void recordsTheProblemRatherThanThrowing() {
            TechniqueSheet sheet = sheet(base().joint(THICK));
            assertThat(sheet.usable()).isFalse();
            assertThat(sheet.problems()).isNotEmpty();
            assertThat(sheet.exposure()).isEmpty();
        }

        @Test
        void stillResolvesThePartsThatDoWork() {
            TechniqueSheet sheet = sheet(base().joint(THICK));
            assertThat(sheet.viewing().technique()).isEqualTo(ViewingTechnique.DWSI);
            assertThat(sheet.coverage()).isPresent();
        }

        @Test
        void namesWhichPartFailed() {
            TechniqueSheet sheet = sheet(base().joint(THICK));
            assertThat(String.join(" ", sheet.problems())).contains("exposure");
        }
    }

    @Nested
    class RenderTechnique {

        private final String text = Technique.renderTechnique(sheet(base()));

        @Test
        void printsTheHeadingAndTheJoint() {
            assertThat(text).contains("RADIOGRAPHIC TECHNIQUE");
            assertThat(text).contains("J-118");
            assertThat(text).contains("6.625 in OD x 0.280 wall");
        }

        @Test
        void printsTheExposureBlockWithATimerSetting() {
            assertThat(text).contains("SOURCE AND EXPOSURE");
            assertThat(text).contains("Ci min");
            assertThat(text).containsPattern("set \\d+ s");
        }

        @Test
        void printsTheIndicatorAndTheUnsharpnessTogether() {
            assertThat(text).contains("IMAGE QUALITY");
            assertThat(text).contains("hole type, 2T");
            assertThat(text).contains("Unsharpness");
        }

        @Test
        void printsTheShotTable() {
            assertThat(text).contains("SHOTS");
            assertThat(text).contains("centre");
            assertThat(text).contains("12:00");
        }

        @Test
        void printsTheBarricade() {
            assertThat(text).contains("SAFETY");
            assertThat(text).contains("Barricade");
        }

        @Test
        void doesNotMarkAGoodSheetAsNotReady() {
            assertThat(text).doesNotContain("NOT READY");
        }

        @Test
        void keepsEveryLineInsideThePaperWidth() {
            for (String line : text.split("\n")) {
                assertThat(line.length()).isLessThanOrEqualTo(78);
            }
        }
    }

    @Nested
    class RenderTechniqueWhenSomethingWillNotResolve {

        private final String text = Technique.renderTechnique(sheet(base().joint(THICK)));

        @Test
        void marksTheSheetNotReady() {
            assertThat(text).contains("NOT READY");
        }

        @Test
        void saysWhatWasNotEstablishedRatherThanLeavingABlank() {
            assertThat(text).contains("not established");
        }

        @Test
        void listsTheNotesAtTheFoot() {
            assertThat(text).contains("NOTES");
            assertThat(text).contains("exposure:");
        }

        @Test
        void saysWhichPartIsMissingWhenNothingButTheViewingResolves() {
            TechniqueSheet sheet =
                    sheet(base().isotopeId(null).sourceSizeIn(Double.NaN).maxThicknessRatio(1));
            String printed = Technique.renderTechnique(sheet);
            assertThat(sheet.problems().stream().map(problem -> problem.substring(0, problem.indexOf(':'))))
                    .containsExactly("exposure", "unsharpness", "coverage", "barricade");
            assertThat(printed).contains("not established (the indicator or the geometry did not resolve)");
            assertThat(printed).contains("not established (the coverage did not resolve)");
            assertThat(printed).contains("not established (no source)");
        }
    }

    @Nested
    class TheInputCarriesTheSetup {

        @Test
        void passesEverySettingThroughToTheCalculations() {
            TechniqueInput input = base().filmId("D5")
                    .screens("LEAD_HEAVY")
                    .targetDensity(2.5)
                    .daysSinceAssay(10)
                    .sourceSizeIn(0.08)
                    .maxThicknessRatio(1.3)
                    .sourceCanEnter(false)
                    .materialId("STAINLESS")
                    .build();
            TechniqueSheet sheet = Technique.buildTechnique(input);
            assertThat(sheet.exposure().get().film()).isEqualTo("D5");
            assertThat(sheet.exposure().get().screens()).isEqualTo("LEAD_HEAVY");
            assertThat(sheet.exposure().get().targetDensity()).isEqualTo(2.5);
            assertThat(sheet.exposure().get().activityCi()).isLessThan(40);
            assertThat(sheet.materialId()).isEqualTo("STAINLESS");
            assertThat(input.toBuilder().build()).isEqualTo(input);
        }
    }

    @Nested
    class SummariseTechnique {

        @Test
        void givesAFolderIndexRow() {
            TechniqueSummary summary = Technique.summariseTechnique(sheet(base()));
            assertThat(summary.jointId()).isEqualTo("J-118");
            assertThat(summary.technique()).isEqualTo(ViewingTechnique.DWSI);
            assertThat(summary.isotope()).contains("IR192");
            assertThat(summary.usable()).isTrue();
            assertThat(summary.shots().getAsInt()).isGreaterThan(0);
        }

        @Test
        void leavesTheUnresolvedPartsNull() {
            TechniqueSummary summary = Technique.summariseTechnique(sheet(base().joint(THICK)));
            assertThat(summary.isotope()).isEmpty();
            assertThat(summary.minutes()).isEmpty();
            assertThat(summary.usable()).isFalse();
        }
    }

    @Nested
    class TheMaterialThePipeIsMadeOf {

        @Test
        void readsTheChartAtTheEquivalentSteelThickness() {
            TechniqueSheet steel = sheet(base());
            assertThat(steel.chartThicknessIn().getAsDouble())
                    .isCloseTo(steel.viewing().penetratedThicknessIn(), digits(4));
        }

        @Test
        void readsALighterMetalAtAMuchThinnerSteelEquivalent() {
            TechniqueSheet aluminium = sheet(base().materialId("ALUMINIUM"));
            assertThat(aluminium.chartThicknessIn().getAsDouble())
                    .isLessThan(aluminium.viewing().penetratedThicknessIn() / 4);
        }

        @Test
        void shortensTheExposureOnTheLighterMetal() {
            TechniqueSheet steel = sheet(base());
            TechniqueSheet aluminium = sheet(base().materialId("ALUMINIUM"));
            assertThat(aluminium.exposure()).isEmpty();
            assertThat(steel.exposure()).isPresent();
        }

        @Test
        void lengthensItOnAHarderOne() {
            TechniqueSheet steel = sheet(base());
            TechniqueSheet inconel = sheet(base().materialId("INCONEL"));
            assertThat(inconel.exposure().get().curieMinutes())
                    .isGreaterThan(steel.exposure().get().curieMinutes());
        }

        @Test
        void stillChoosesTheIndicatorOnTheSectionAndNotOnTheSteelEquivalent() {
            TechniqueSheet steel = sheet(base());
            TechniqueSheet inconel = sheet(base().materialId("INCONEL"));
            assertThat(inconel.indicator().get().holeIdentity())
                    .isEqualTo(steel.indicator().get().holeIdentity());
        }

        @Test
        void printsTheMaterialAndTheChartThicknessOnTheSheet() {
            String text = Technique.renderTechnique(sheet(base().materialId("STAINLESS")));
            assertThat(text).contains("austenitic stainless");
            assertThat(text).contains("in steel");
        }

        @Test
        void recordsAMaterialItDoesNotKnowRatherThanThrowing() {
            TechniqueSheet sheet = sheet(base().materialId("LEAD"));
            assertThat(sheet.usable()).isFalse();
            assertThat(String.join(" ", sheet.problems())).contains("material");
        }

        @Test
        void printsAMaterialItDoesNotKnowAsItWasAskedFor() {
            String text = Technique.renderTechnique(sheet(base().materialId("LEAD")));
            assertThat(text).contains("LEAD (not on the material list)");
            assertThat(text).contains("material: no material LEAD on the list");
        }
    }

    @Nested
    class HeldToAnIsoClass {

        @Test
        void leavesTheIsoLineOffWhenNoClassWasAsked() {
            TechniqueSheet plain = sheet(base());
            assertThat(plain.isoDistance()).isEmpty();
            assertThat(Technique.renderTechnique(plain)).doesNotContain("ISO 17636");
        }

        @Test
        void saysHowFarShortOfClassBTheSetupIs() {
            TechniqueSheet classB = sheet(base().isoClass(IsoClass.B));
            IsoDistanceCheck check = classB.isoDistance().orElseThrow();
            assertThat(check.minimumIn()).isEqualTo(54.86);
            assertThat(check.pass()).isFalse();
            assertThat(check.shortfallIn()).isEqualTo(6.86);
            assertThat(Technique.renderTechnique(classB)).contains("class B needs 54.86 in, short by 6.86 in");
        }

        @Test
        void saysClassAIsMetAtTheSameDistance() {
            TechniqueSheet classA = sheet(base().isoClass(IsoClass.A));
            assertThat(classA.isoDistance().orElseThrow().pass()).isTrue();
            assertThat(classA.isoDistance().orElseThrow().shortfallIn()).isZero();
            assertThat(Technique.renderTechnique(classA)).contains("class A needs 27.43 in, met");
            assertThat(classA.usable()).isTrue();
        }

        @Test
        void recordsAProblemWhenTheSetupCannotBeHeldToTheClass() {
            TechniqueSheet unset = sheet(base().isoClass(IsoClass.B).sourceToObjectIn(Double.NaN));
            assertThat(unset.isoDistance()).isEmpty();
            assertThat(unset.problems()).anyMatch(problem -> problem.startsWith("ISO 17636: "));
        }

        @Test
        void keepsTheClassWhenTheInputIsRebuilt() {
            TechniqueInput input = base().isoClass(IsoClass.B).build();
            assertThat(input.toBuilder().build().isoClass()).isEqualTo(IsoClass.B);
        }
    }
}
