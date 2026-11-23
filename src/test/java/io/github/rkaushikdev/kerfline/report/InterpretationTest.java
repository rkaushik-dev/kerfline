package io.github.rkaushikdev.kerfline.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.kerfline.indication.IndicationInput;
import io.github.rkaushikdev.kerfline.indication.Measure;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InterpretationTest {

    private static SheetInput.Builder fullInput() {
        return SheetInput.builder()
                .job("C-4471")
                .jointId("J-118")
                .day("2025-03-14")
                .reader("R. Ibarra")
                .readerLevel("II")
                .code("API1104")
                .technique("DWSI")
                .wallIn(0.28)
                .films(List.of("1", "2", "3"))
                .indications(List.of(
                        Measure.makeIndication(
                                IndicationInput.of("SLAG", 0.15).at(8).film("2")),
                        Measure.makeIndication(
                                IndicationInput.of("POROSITY", 0.04).at(3).film("1"))))
                .verdict(SheetVerdict.of("accept", "9.3", List.of()));
    }

    private static InterpretationSheet full() {
        return Interpretation.makeSheet(fullInput().build());
    }

    private static InterpretationSheet sheet(SheetInput.Builder input) {
        return Interpretation.makeSheet(input.build());
    }

    @Nested
    class MakeSheet {

        @Test
        void keepsWhatItWasGiven() {
            InterpretationSheet sheet = full();
            assertThat(sheet.jointId()).contains("J-118");
            assertThat(sheet.films()).containsExactly("1", "2", "3");
            assertThat(sheet.indications()).hasSize(2);
        }

        @Test
        void leavesTheOptionalFieldsNullRatherThanUndefined() {
            InterpretationSheet sheet = sheet(SheetInput.builder());
            assertThat(sheet.job()).isEmpty();
            assertThat(sheet.verdict()).isEmpty();
            assertThat(sheet.indications()).isEmpty();
        }

        @Test
        void refusesADateThatIsNotARealDay() {
            assertThatThrownBy(() -> sheet(SheetInput.builder().day("2025-02-30")))
                    .hasMessageContaining("not a real calendar day");
        }

        @Test
        void refusesIndicationsThatAreNotAList() {
            assertThatThrownBy(() -> sheet(SheetInput.builder().indications(null)))
                    .hasMessageContaining("must be an array");
        }

        @Test
        void makesTheSameSheetFromACopyOfItsInput() {
            SheetInput input = fullInput().remarks("none").build();
            assertThat(Interpretation.makeSheet(input.toBuilder().build())).isEqualTo(Interpretation.makeSheet(input));
            assertThat(SheetVerdict.of("accept", "9.3")).isEqualTo(SheetVerdict.of("accept", "9.3", List.of()));
        }

        @Test
        void refusesSomethingThatIsNotAnObject() {
            assertThatThrownBy(() -> Interpretation.makeSheet(null)).hasMessageContaining("built from an object");
        }
    }

    @Nested
    class Completeness {

        @Test
        void callsAFullSheetIssuable() {
            SheetCompleteness state = Interpretation.completeness(full());
            assertThat(state.issuable()).isTrue();
            assertThat(state.missing()).isEmpty();
        }

        @Test
        void namesTheRequiredFields() {
            assertThat(Interpretation.requiredFields()).containsExactly("job", "jointId", "day", "reader", "code");
        }

        @Test
        void listsEverythingMissingRatherThanTheFirst() {
            SheetCompleteness state = Interpretation.completeness(sheet(SheetInput.builder()));
            assertThat(state.missing()).contains("job", "reader", "verdict", "films");
            assertThat(state.issuable()).isFalse();
        }

        @Test
        void countsASheetWithNoReaderAsNotIssuable() {
            InterpretationSheet sheet = sheet(fullInput().reader(null));
            assertThat(Interpretation.completeness(sheet).missing()).containsExactly("reader");
        }

        @Test
        void countsABlankFieldAsMissing() {
            InterpretationSheet sheet = sheet(fullInput().job("   "));
            assertThat(Interpretation.completeness(sheet).missing()).containsExactly("job");
        }

        @Test
        void doesNotRequireIndicationsSinceACleanJointHasNone() {
            InterpretationSheet sheet = sheet(fullInput().indications(List.of()));
            assertThat(Interpretation.completeness(sheet).issuable()).isTrue();
        }
    }

    @Nested
    class IndicationRows {

        @Test
        void sortsThemTheWayTheySitRoundTheWeld() {
            List<IndicationRow> rows = Interpretation.indicationRows(full());
            assertThat(rows.stream().map(row -> row.at().getAsDouble())).containsExactly(3.0, 8.0);
        }

        @Test
        void numbersThemFromOneAfterSorting() {
            assertThat(Interpretation.indicationRows(full()).stream().map(IndicationRow::item))
                    .containsExactly(1, 2);
        }

        @Test
        void printsTheSizeToThreePlacesWithTheTrailingZeros() {
            List<IndicationRow> rows = Interpretation.indicationRows(full());
            assertThat(rows.get(0).sizeIn()).isEqualTo("0.040");
        }

        @Test
        void isEmptyForACleanJoint() {
            assertThat(Interpretation.indicationRows(sheet(SheetInput.builder())))
                    .isEmpty();
        }
    }

    @Nested
    class WhereTheCrewFindsIt {

        private InterpretationSheet onSixInchPipe() {
            return sheet(fullInput()
                    .odIn(6.625)
                    .indications(List.of(
                            Measure.makeIndication(IndicationInput.of("SLAG", 0.15)
                                    .at(8)
                                    .film("2")
                                    .note("isolated")),
                            Measure.makeIndication(
                                    IndicationInput.of("POROSITY", 0.04).at(3).film("1")),
                            Measure.makeIndication(IndicationInput.of("UNDERCUT", 0.3)))));
        }

        @Test
        void putsEachLocatedFindingAtAClockPosition() {
            List<IndicationRow> rows = Interpretation.indicationRows(onSixInchPipe());
            assertThat(rows.stream().map(IndicationRow::clock))
                    .containsExactly(Optional.empty(), Optional.of("1:44"), Optional.of("4:37"));
        }

        @Test
        void carriesTheReadersNoteOntoTheRow() {
            List<IndicationRow> rows = Interpretation.indicationRows(onSixInchPipe());
            assertThat(rows.get(2).note()).isEqualTo("isolated");
            assertThat(rows.get(1).note()).isEmpty();
        }

        @Test
        void printsTheClockAndNoteColumnsOnlyWhenTheSheetHasThem() {
            String located = Interpretation.renderSheet(onSixInchPipe());
            assertThat(located).contains("clock").contains("4:37").contains("isolated");
            String plain = Interpretation.renderSheet(full());
            assertThat(plain).doesNotContain("clock").doesNotContain("note");
            for (String line : located.split("\n")) {
                assertThat(line.length()).isLessThanOrEqualTo(78);
            }
        }

        @Test
        void leavesTheClockOffASheetThatDoesNotKnowThePipe() {
            assertThat(Interpretation.indicationRows(full()).stream().map(IndicationRow::clock))
                    .containsOnly(Optional.empty());
            assertThat(full().odIn()).isEmpty();
            assertThat(onSixInchPipe().odIn()).hasValue(6.625);
        }

        @Test
        void summarisesTheLargestFindingWhereverItSits() {
            assertThat(Interpretation.summarise(onSixInchPipe()).largestIn()).isEqualTo(0.3);
        }
    }

    @Nested
    class RenderSheet {

        @Test
        void printsTheHeadingAndTheJoint() {
            String text = Interpretation.renderSheet(full());
            assertThat(text).contains("RADIOGRAPHIC INTERPRETATION");
            assertThat(text).contains("J-118");
            assertThat(text).contains("API1104");
        }

        @Test
        void printsTheFindingsTable() {
            String text = Interpretation.renderSheet(full());
            assertThat(text).contains("FINDINGS");
            assertThat(text).contains("indication");
            assertThat(text).contains("slag inclusion");
        }

        @Test
        void saysSoPlainlyWhenThereIsNothingToReport() {
            InterpretationSheet clean = sheet(fullInput().indications(List.of()));
            assertThat(Interpretation.renderSheet(clean)).contains("No reportable indications.");
        }

        @Test
        void printsTheDispositionInCapitalsWithItsClause() {
            String text = Interpretation.renderSheet(full());
            assertThat(text).contains("Disposition: ACCEPT");
            assertThat(text).contains("Clause: 9.3");
        }

        @Test
        void listsEveryReasonOnARejectedJoint() {
            InterpretationSheet sheet =
                    sheet(fullInput().verdict(SheetVerdict.of("reject", "9.3.9", List.of("crack 0.2 in long"))));
            String text = Interpretation.renderSheet(sheet);
            assertThat(text).contains("Disposition: REJECT");
            assertThat(text).contains("crack 0.2 in long");
        }

        @Test
        void marksADraftAndSaysWhatItIsWaitingOn() {
            InterpretationSheet sheet = sheet(fullInput().reader(null));
            String text = Interpretation.renderSheet(sheet);
            assertThat(text).contains("DRAFT");
            assertThat(text).contains("reader");
        }

        @Test
        void doesNotMarkACompleteSheetAsADraft() {
            assertThat(Interpretation.renderSheet(full())).doesNotContain("DRAFT");
        }

        @Test
        void printsTheReaderAndTheirLevel() {
            assertThat(Interpretation.renderSheet(full())).contains("R. Ibarra (Level II)");
        }

        @Test
        void appendsRemarksOnlyWhenThereAreSome() {
            assertThat(Interpretation.renderSheet(full())).doesNotContain("REMARKS");
            InterpretationSheet withRemarks = sheet(fullInput().remarks("Reshot at 3:00 for density."));
            assertThat(Interpretation.renderSheet(withRemarks)).contains("REMARKS");
            assertThat(Interpretation.renderSheet(withRemarks)).contains("Reshot at 3:00");
        }

        @Test
        void rendersASheetWithAlmostNothingOnItRatherThanThrowing() {
            assertThatCode(() -> Interpretation.renderSheet(sheet(SheetInput.builder())))
                    .doesNotThrowAnyException();
            assertThat(Interpretation.renderSheet(sheet(SheetInput.builder()))).contains("Not yet judged.");
        }

        @Test
        void keepsEveryLineInsideThePaperWidth() {
            for (String line : Interpretation.renderSheet(full()).split("\n")) {
                assertThat(line.length()).isLessThanOrEqualTo(78);
            }
        }
    }

    @Nested
    class Summarise {

        @Test
        void givesARegisterRow() {
            assertThat(Interpretation.summarise(full()))
                    .isEqualTo(new SheetSummary(
                            Optional.of("J-118"), Optional.of("2025-03-14"), Optional.of("accept"), 2, 0.15, true));
        }

        @Test
        void reportsNothingLargestOnACleanJoint() {
            InterpretationSheet clean = sheet(fullInput().indications(List.of()));
            assertThat(Interpretation.summarise(clean).largestIn()).isEqualTo(0);
        }

        @Test
        void leavesTheDispositionNullWhileTheJointIsUnjudged() {
            InterpretationSheet sheet = sheet(fullInput().verdict(null));
            assertThat(Interpretation.summarise(sheet).disposition()).isEmpty();
            assertThat(Interpretation.summarise(sheet).issuable()).isFalse();
        }
    }
}
