package io.github.rkaushikdev.kerfline.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.kerfline.accept.JudgeOptions;
import io.github.rkaushikdev.kerfline.indication.Indication;
import io.github.rkaushikdev.kerfline.indication.IndicationType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CommandsTest {

    private static List<String> with(List<String> base, String... more) {
        List<String> argv = new ArrayList<>(base);
        argv.addAll(List.of(more));
        return argv;
    }

    @Nested
    class ParseIndication {

        @Test
        void readsTypeSizeAndPosition() {
            Indication indication = Commands.parseIndication("SLAG:0.5:3");
            assertThat(indication.type()).isEqualTo(IndicationType.SLAG);
            assertThat(indication.sizeIn()).isEqualTo(0.5);
            assertThat(indication.at()).hasValue(3);
        }

        @Test
        void takesAnIndicationWithNoPosition() {
            assertThat(Commands.parseIndication("SLAG:0.5").at()).isEmpty();
        }

        @Test
        void readsALowerCaseType() {
            assertThat(Commands.parseIndication("porosity:0.04").type()).isEqualTo(IndicationType.POROSITY);
        }

        @Test
        void refusesAShapeItCannotRead() {
            assertThatThrownBy(() -> Commands.parseIndication("SLAG")).hasMessageContaining("cannot read SLAG");
            assertThatThrownBy(() -> Commands.parseIndication("a:b:c:d")).hasMessageContaining("cannot read");
        }

        @Test
        void refusesASizeOrAPositionThatIsNotANumber() {
            assertThatThrownBy(() -> Commands.parseIndication("SLAG:big")).hasMessageContaining("is not a size");
            assertThatThrownBy(() -> Commands.parseIndication("SLAG:0.5:top"))
                    .hasMessageContaining("is not a position");
        }

        @Test
        void readsTheReadersNoteAfterThePosition() {
            Indication isolated = Commands.parseIndication("SLAG:0.3:4:isolated");
            assertThat(isolated.note()).hasValue("isolated");
            assertThat(isolated.at()).hasValue(4);
        }

        @Test
        void takesANoteWithNoPosition() {
            Indication highLow = Commands.parseIndication("IP:1.5::high-low");
            assertThat(highLow.at()).isEmpty();
            assertThat(highLow.note()).hasValue("high-low");
            assertThat(Commands.parseIndication("IP:1.5:2:").note()).isEmpty();
        }

        @Test
        void refusesMoreThanFourParts() {
            assertThatThrownBy(() -> Commands.parseIndication("SLAG:0.3:4:isolated:x"))
                    .hasMessage("cannot read SLAG:0.3:4:isolated:x as type:size:position:note");
            assertThatThrownBy(() -> Commands.parseIndication("SLAG:big"))
                    .hasMessage("cannot read SLAG:big, big is not a size");
        }

        @Test
        void refusesATypeNobodyReports() {
            assertThatThrownBy(() -> Commands.parseIndication("LAMINATION:0.5"))
                    .hasMessageContaining("no indication type");
        }
    }

    @Nested
    class TheCommandList {

        @Test
        void offersTheFourCommands() {
            assertThat(Commands.commandNames()).containsExactly("technique", "judge", "sources", "tables");
        }

        @Test
        void describesEachOne() {
            for (String name : Commands.commandNames()) {
                assertThat(Commands.command(name).describe().length()).isGreaterThan(10);
            }
        }

        @Test
        void refusesACommandNobodyWrote() {
            assertThatThrownBy(() -> Commands.command("shoot")).hasMessageContaining("no command shoot");
        }
    }

    @Nested
    class TechniqueCommand {

        private final List<String> argv = List.of(
                "--job",
                "C-4471",
                "--joint",
                "J-118",
                "--size",
                "6",
                "--schedule",
                "40",
                "--activity",
                "40",
                "--distance",
                "48",
                "--standoff",
                "6.625");

        @Test
        void printsATechniqueSheet() {
            CommandResult result = Commands.runCommand("technique", argv);
            assertThat(result.text()).contains("RADIOGRAPHIC TECHNIQUE");
            assertThat(result.text()).contains("J-118");
            assertThat(result.code()).isZero();
        }

        @Test
        void exitsNonZeroWhenTheTechniqueWillNotResolve() {
            List<String> thick =
                    List.of("--joint", "J-9", "--od", "24", "--wall", "3", "--activity", "40", "--distance", "48");
            CommandResult result = Commands.runCommand("technique", thick);
            assertThat(result.code()).isEqualTo(1);
            assertThat(result.text()).contains("NOT READY");
        }

        @Test
        void takesMeasuredGeometryInsteadOfASize() {
            List<String> measured = List.of("--od", "6.625", "--wall", "0.28", "--activity", "40", "--distance", "48");
            assertThat(Commands.runCommand("technique", measured).code()).isZero();
        }

        @Test
        void refusesWhenNeitherGeometryIsGiven() {
            assertThatThrownBy(() -> Commands.runCommand("technique", List.of("--activity", "40", "--distance", "48")))
                    .hasMessageContaining("either --size with --schedule or --od with --wall");
        }

        @Test
        void refusesWithoutTheRequiredOptions() {
            assertThatThrownBy(() -> Commands.runCommand("technique", List.of("--size", "6")))
                    .hasMessageContaining("missing required option");
        }
    }

    @Nested
    class JudgeCommand {

        private final List<String> base = List.of("--joint", "J-118", "--size", "6", "--schedule", "40");

        @Test
        void acceptsACleanJointAndExitsZero() {
            CommandResult result = Commands.runCommand("judge", base);
            assertThat(result.text()).containsPattern("Result\\s+ACCEPT");
            assertThat(result.code()).isZero();
        }

        @Test
        void takesIndicationsAsPositionals() {
            CommandResult result = Commands.runCommand("judge", with(base, "SLAG:3:2"));
            assertThat(result.code()).isEqualTo(1);
            assertThat(result.text()).contains("Findings");
            assertThat(result.text()).contains("slag");
        }

        @Test
        void rejectsAJointWithACrackInIt() {
            CommandResult result = Commands.runCommand("judge", with(base, "CRACK:0.2:2"));
            assertThat(result.text()).containsPattern("Result\\s+REJECT");
            assertThat(result.code()).isEqualTo(1);
        }

        @Test
        void namesTheClauseItJudgedOn() {
            assertThat(Commands.runCommand("judge", with(base, "CRACK:0.2:2")).text())
                    .contains("Clause");
        }

        @Test
        void runsEveryCodeWhenAsked() {
            CommandResult result = Commands.runCommand("judge", with(base, "--all", "SLAG:0.15:2"));
            assertThat(result.text()).contains("API1104");
            assertThat(result.text()).contains("B31.3");
            assertThat(result.text()).contains("AWSD1.1");
        }

        @Test
        void showsTheCodesDisagreeing() {
            CommandResult result = Commands.runCommand("judge", with(base, "--all", "SLAG:0.15:2"));
            assertThat(result.text()).containsPattern("accept");
            assertThat(result.text()).containsPattern("repair");
        }

        @Test
        void takesTheFluidServiceThroughToB313() {
            CommandResult normal = Commands.runCommand("judge", with(base, "--code", "B31.3", "IP:0.5:2"));
            CommandResult light =
                    Commands.runCommand("judge", with(base, "--code", "B31.3", "--service", "category_d", "IP:0.5:2"));
            assertThat(normal.code()).isEqualTo(1);
            assertThat(light.code()).isZero();
        }
    }

    @Nested
    class JudgeOptionsFromTheCommandLine {

        private final List<String> base = List.of("--joint", "J-118", "--size", "6", "--schedule", "40");

        @Test
        void reachesTheIsolatedSlagRuleThroughTheNote() {
            List<String> five = List.of(
                    "SLAG:0.05:1:isolated",
                    "SLAG:0.05:2:isolated",
                    "SLAG:0.05:3:isolated",
                    "SLAG:0.05:4:isolated",
                    "SLAG:0.05:5:isolated");
            CommandResult result = Commands.runCommand("judge", with(base, five.toArray(String[]::new)));
            assertThat(result.code()).isEqualTo(1);
            assertThat(result.text()).contains("5 isolated slag inclusions in 12 in from 1 in, cap is 4");
        }

        @Test
        void repairsAShortCrackOnlyWhenTheCompanySaysSo() {
            assertThat(Commands.runCommand("judge", with(base, "CRACK:1:4")).text())
                    .contains("REJECT");
            CommandResult authorised = Commands.runCommand("judge", with(base, "--crack-repairs", "CRACK:1:4"));
            assertThat(authorised.text()).contains("REPAIR").contains("repairable on the company's authority");
        }

        @Test
        void takesAShorterWeldLengthThroughToTheCode() {
            // 0.9 in of IP is under any cap on the full joint and over 8 percent of a 7.5 in weld.
            assertThat(Commands.runCommand("judge", with(base, "IP:0.9:1")).code())
                    .isZero();
            CommandResult partial = Commands.runCommand("judge", with(base, "--weld-length", "7.5", "IP:0.9:1"));
            assertThat(partial.code()).isEqualTo(1);
            assertThat(partial.text()).contains("percent of a weld under 12 in");
        }

        @Test
        void mapsEveryOptionOntoTheEngine() {
            ParsedArgs parsed = Args.parseArgs(
                    List.of(
                            "--weld-length",
                            "30",
                            "--reinforcement",
                            "0.06",
                            "--mating-wall",
                            "0.5",
                            "--crack-repairs",
                            "--service",
                            "category_d"),
                    Commands.command("judge").options());
            JudgeOptions options = Commands.judgeOptions(parsed);
            assertThat(options.weldLengthIn()).isEqualTo(30);
            assertThat(options.reinforcementIn()).isEqualTo(0.06);
            assertThat(options.matingWallIn()).isEqualTo(0.5);
            assertThat(options.crackRepairs()).isTrue();
            assertThat(options.service()).isEqualTo("category_d");
            JudgeOptions bare = Commands.judgeOptions(
                    Args.parseArgs(List.of(), Commands.command("judge").options()));
            assertThat(bare.weldLengthIn()).isNull();
            assertThat(bare.crackRepairs()).isFalse();
        }
    }

    @Nested
    class SourcesCommand {

        @Test
        void listsEverySourceWithItsDecayAndBoundary() {
            CommandResult result = Commands.runCommand("sources", List.of());
            assertThat(result.text()).contains("Ir-192");
            assertThat(result.text()).contains("Co-60");
            assertThat(result.text()).contains("rope ft");
            assertThat(result.code()).isZero();
        }

        @Test
        void narrowsToOneSource() {
            CommandResult result = Commands.runCommand("sources", List.of("--isotope", "IR192"));
            assertThat(result.text()).contains("Ir-192");
            assertThat(result.text()).doesNotContain("Co-60");
        }

        @Test
        void projectsTheActivityForwardTheDaysAskedFor() {
            CommandResult shortly = Commands.runCommand("sources", List.of("--isotope", "IR192", "--days", "0"));
            CommandResult later = Commands.runCommand("sources", List.of("--isotope", "IR192", "--days", "365"));
            assertThat(shortly.text()).contains("Ci at 0 d");
            assertThat(later.text()).contains("Ci at 365 d");
        }

        @Test
        void refusesASourceOutsideTheCatalog() {
            assertThatThrownBy(() -> Commands.runCommand("sources", List.of("--isotope", "CS137")))
                    .hasMessageContaining("no isotope CS137");
        }
    }

    @Nested
    class TheUsageScreens {

        @Test
        void describesOneCommandWithItsOptions() {
            String text = Commands.commandUsage("technique");
            assertThat(text).contains("usage: kerf technique");
            assertThat(text).contains("--activity");
            assertThat(text).contains("(required)");
        }

        @Test
        void listsEveryCommandOnTheOverallScreen() {
            String text = Commands.overallUsage();
            for (String name : Commands.commandNames()) {
                assertThat(text).contains(name);
            }
            assertThat(text).contains("usage: kerf <command>");
        }

        @Test
        void refusesUsageForACommandNobodyWrote() {
            assertThatThrownBy(() -> Commands.commandUsage("shoot")).hasMessageContaining("no command shoot");
        }
    }

    @Nested
    class TechniqueTakesTheMaterialThePipeIsMadeOf {

        private final List<String> argv = List.of(
                "--joint",
                "J-118",
                "--size",
                "6",
                "--schedule",
                "40",
                "--activity",
                "40",
                "--distance",
                "48",
                "--standoff",
                "6.625");

        @Test
        void assumesCarbonSteelWhenNoneIsGiven() {
            assertThat(Commands.runCommand("technique", argv).text()).contains("carbon steel");
        }

        @Test
        void takesADifferentMetalAndPrintsIt() {
            CommandResult result = Commands.runCommand("technique", with(argv, "--material", "STAINLESS"));
            assertThat(result.text()).contains("austenitic stainless");
        }

        @Test
        void readsTheChartAtTheSteelTheSectionBehavesLike() {
            CommandResult result = Commands.runCommand("technique", with(argv, "--material", "STAINLESS"));
            assertThat(result.text()).contains("in steel");
        }

        @Test
        void marksTheSheetNotReadyOnAMetalNobodyStocks() {
            CommandResult result = Commands.runCommand("technique", with(argv, "--material", "LEAD"));
            assertThat(result.code()).isEqualTo(1);
            assertThat(result.text()).contains("NOT READY");
        }
    }
}
