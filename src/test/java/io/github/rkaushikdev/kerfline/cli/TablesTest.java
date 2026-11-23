package io.github.rkaushikdev.kerfline.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.kerfline.accept.Api1104;
import io.github.rkaushikdev.kerfline.accept.Asme8;
import io.github.rkaushikdev.kerfline.geom.Unsharpness;
import io.github.rkaushikdev.kerfline.joint.Schedule;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TablesTest {

    @Nested
    class TheTableList {

        @Test
        void offersTheTablesATechnicianAsksFor() {
            assertThat(Tables.tableNames())
                    .containsExactly("unsharpness", "iqi", "density", "wire", "schedule", "api1104", "rounded");
        }

        @Test
        void describesEachOne() {
            for (String name : Tables.tableNames()) {
                assertThat(Tables.tableDescribe(name).length()).isGreaterThan(10);
            }
        }

        @Test
        void refusesATableNobodyPrinted() {
            assertThatThrownBy(() -> Tables.tableDescribe("exposure")).hasMessageContaining("no table called exposure");
            assertThatThrownBy(() -> Tables.renderReferenceTable("exposure"))
                    .hasMessageContaining("no table called exposure");
        }
    }

    @Nested
    class ThePrintedTablesAgreeWithTheCodeThatAppliesThem {

        @Test
        void printsTheUnsharpnessLimitsTheCheckerUses() {
            String text = Tables.renderReferenceTable("unsharpness");
            assertThat(text).contains("Ug limit");
            assertThat(text).contains("0.020");
            assertThat(text).contains("0.070");
            assertThat(Unsharpness.unsharpnessLimit(0.5)).isEqualTo(0.02);
        }

        @Test
        void printsTheIndicatorTableTheSelectorReads() {
            String text = Tables.renderReferenceTable("iqi");
            assertThat(text).contains("src hole");
            assertThat(text).contains("film wire");
            assertThat(text).contains("The essential hole is 2T");
        }

        @Test
        void printsBothDensityFloors() {
            String text = Tables.renderReferenceTable("density");
            assertThat(text).contains("gamma");
            assertThat(text).contains("xray");
            assertThat(text).contains("2.0");
            assertThat(text).contains("1.8");
            assertThat(text).contains("composite");
        }

        @Test
        void printsEveryWireWithTheSetItComesFrom() {
            String text = Tables.renderReferenceTable("wire");
            assertThat(text.split("\n")).hasSize(23);
            assertThat(text).contains("0.0032");
            assertThat(text).contains("0.3200");
        }

        @Test
        void printsTheWallsTheScheduleTableCarries() {
            String text = Tables.renderReferenceTable("schedule");
            assertThat(text).contains("NPS");
            assertThat(text).contains("6.625");
            assertThat(text).contains(Num.toFixed(Schedule.wallThicknessIn("6", "40"), 3));
        }

        @Test
        void printsARowForEverySizeAndSchedulePairing() {
            String[] lines = Tables.renderReferenceTable("schedule").split("\n");
            assertThat(lines.length).isGreaterThan(60);
        }
    }

    @Nested
    class TheCodeTables {

        @Test
        void printsEveryApi1104LengthRuleWithItsCaps() {
            String text = Tables.renderReferenceTable("api1104");
            assertThat(text.split("\n")).hasSize(2 + Api1104.lengthRules().size() + 3);
            assertThat(text).containsPattern("incomplete penetration due to high-low +9\\.3\\.2 +2 +3");
            assertThat(text).containsPattern("incomplete fusion +9\\.3\\.4 +1 +1 +8 percent under 12 in");
            assertThat(text).containsPattern("hollow bead +9\\.3\\.7 +0\\.5 +2 +8 percent of any weld");
            assertThat(text).containsPattern("undercut +9\\.3\\.8 +2 +2 +16\\.7 percent of any weld");
            assertThat(text).contains("8 percent of the weld.");
        }

        @Test
        void printsTheAppendixFourLimitsAcrossTheThicknessBands() {
            String text = Tables.renderReferenceTable("rounded");
            assertThat(text).contains("counts over in");
            assertThat(text).containsPattern("0\\.500 +0\\.0313 +0\\.1250 +0\\.1667");
            assertThat(text).containsPattern("3\\.000 +0\\.0625 +0\\.1563 +0\\.3750");
            assertThat(text.split("\n")).hasSize(12);
            assertThat(text).containsPattern("0\\.100 +0\\.0100 +0\\.0250 +0\\.0333 +12");
            assertThat(text).containsPattern("0\\.125 +0\\.0156 +0\\.0313 +0\\.0417 +chart");
        }

        @Test
        void readsTheNumbersFromTheCodesRatherThanACopy() {
            String text = Tables.renderReferenceTable("rounded");
            assertThat(text).contains(Num.fixed(Asme8.roundedLimitIn(0.25, true), 4));
            assertThat(Tables.tableDescribe("api1104")).contains("API 1104");
        }
    }

    @Nested
    class TheTablesCommand {

        @Test
        void isRegisteredWithTheOthers() {
            assertThat(Commands.commandNames()).contains("tables");
        }

        @Test
        void listsTheTablesWhenNoneIsNamed() {
            CommandResult result = Commands.runCommand("tables", List.of());
            assertThat(result.code()).isZero();
            for (String name : Tables.tableNames()) {
                assertThat(result.text()).contains(name);
            }
        }

        @Test
        void printsOneTableWhenItIsNamed() {
            CommandResult result = Commands.runCommand("tables", List.of("--table", "unsharpness"));
            assertThat(result.text()).contains("Ug limit");
            assertThat(result.code()).isZero();
        }

        @Test
        void refusesATableNobodyPrinted() {
            assertThatThrownBy(() -> Commands.runCommand("tables", List.of("--table", "exposure")))
                    .hasMessageContaining("no table called");
        }

        @Test
        void describesItselfForTheUsageScreen() {
            assertThat(Tables.tablesCommand().describe()).contains("reference table");
            assertThat(Tables.tablesCommand().options()).hasSize(1);
        }
    }
}
