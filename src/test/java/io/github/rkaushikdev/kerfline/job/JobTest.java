package io.github.rkaushikdev.kerfline.job;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.kerfline.accept.CodeJudgement;
import io.github.rkaushikdev.kerfline.accept.Disposition;
import io.github.rkaushikdev.kerfline.accept.JudgeOptions;
import io.github.rkaushikdev.kerfline.density.Limits;
import io.github.rkaushikdev.kerfline.geom.IsoClass;
import io.github.rkaushikdev.kerfline.indication.Indication;
import io.github.rkaushikdev.kerfline.indication.IndicationInput;
import io.github.rkaushikdev.kerfline.indication.Measure;
import io.github.rkaushikdev.kerfline.joint.Joint;
import io.github.rkaushikdev.kerfline.joint.JointInput;
import io.github.rkaushikdev.kerfline.joint.Spec;
import io.github.rkaushikdev.kerfline.report.Deviation;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class JobTest {

    private static final JobContract JOB = Job.makeJob(JobInput.builder()
            .number("C-4471")
            .client("Sabine Midstream")
            .site("Station 6")
            .code("API1104")
            .coverage("ten")
            .startedOn("2025-03-01")
            .build());

    private static final Joint JOINT = Spec.makeJoint(
            JointInput.builder().id("J-118").nominalSize(6).schedule(40).build());

    private static JobInput.Builder small() {
        return JobInput.builder().number("C-1").client("x").startedOn("2025-03-01");
    }

    private static JobContract plant() {
        return Job.makeJob(JobInput.builder()
                .number("C-9")
                .client("x")
                .code("B31.3")
                .startedOn("2025-03-01")
                .build());
    }

    private static List<Indication> porosityAtTwo() {
        return List.of(Measure.makeIndication(IndicationInput.of("IP", 0.5).at(2)));
    }

    @Nested
    class MakeJob {

        @Test
        void keepsTheDecisionsSomebodyMade() {
            assertThat(JOB.number()).isEqualTo("C-4471");
            assertThat(JOB.client()).isEqualTo("Sabine Midstream");
            assertThat(JOB.code()).isEqualTo("API1104");
            assertThat(JOB.coverage()).isEqualTo("ten");
        }

        @Test
        void worksTheCoverageFractionOutFromTheLevel() {
            assertThat(JOB.coverageFraction()).isCloseTo(0.1, digits(4));
        }

        @Test
        void takesTheShopDefaultsForTheRest() {
            assertThat(JOB.reinforcementMaxIn()).isCloseTo(0.0625, digits(4));
            assertThat(JOB.densityMinimum()).isEqualTo(2);
            assertThat(JOB.calledForSensitivity()).isEqualTo(2);
        }

        @Test
        void refusesAJobWithNoNumberOrNoClient() {
            assertThatThrownBy(() -> Job.makeJob(JobInput.builder()
                            .client("x")
                            .startedOn("2025-03-01")
                            .build()))
                    .hasMessageContaining("identified by its number");
            assertThatThrownBy(() -> Job.makeJob(JobInput.builder()
                            .number("C-1")
                            .startedOn("2025-03-01")
                            .build()))
                    .hasMessageContaining("names the client");
        }

        @Test
        void refusesACodeNobodyImplemented() {
            assertThatThrownBy(() -> Job.makeJob(small().code("BS2910").build()))
                    .hasMessageContaining("acceptance code must be one of");
        }

        @Test
        void refusesACoverageLevelNobodySpecified() {
            assertThatThrownBy(() -> Job.makeJob(small().coverage("most").build()))
                    .hasMessageContaining("radiography level");
        }

        @Test
        void onlyChecksTheFluidServiceWhenTheCodeCaresAboutIt() {
            assertThatThrownBy(() ->
                            Job.makeJob(small().code("B31.3").service("lethal").build()))
                    .hasMessageContaining("fluid service");
            assertThatCode(() -> Job.makeJob(
                            small().code("API1104").service("lethal").build()))
                    .doesNotThrowAnyException();
        }

        @Test
        void refusesAJobThatIsNotDescribedAtAll() {
            assertThatThrownBy(() -> Job.makeJob(null)).hasMessageContaining("described by an object");
        }

        @Test
        void readsAJobBackThroughACopyOfWhatItWasSetUpWith() {
            JobInput input = small().site("Station 6").coverage("ten").build();
            assertThat(input.toBuilder().build()).isEqualTo(input);
            assertThat(Job.makeJob(input.toBuilder().number("C-2").build()).number())
                    .isEqualTo("C-2");
        }

        @Test
        void refusesAStartDateThatIsNotARealDay() {
            assertThatThrownBy(() -> Job.makeJob(small().startedOn("2025-02-30").build()))
                    .hasMessageContaining("not a real calendar day");
        }
    }

    @Nested
    class ContractFor {

        @Test
        void gathersWhatEveryJudgementShouldUse() {
            assertThat(Job.contractFor(JOB)).isEqualTo(new ContractTerms("API1104", "normal", 0.0625, 2, 2));
        }
    }

    @Nested
    class JudgeUnderContract {

        @Test
        void judgesAgainstTheCodeTheContractNames() {
            CodeJudgement result = Job.judgeUnderContract(JOB, JOINT, List.of());
            assertThat(result.code()).isEqualTo("API1104");
            assertThat(result.disposition()).isEqualTo(Disposition.ACCEPT);
        }

        @Test
        void givesADifferentAnswerUnderADifferentContract() {
            List<Indication> indications = porosityAtTwo();
            assertThat(Job.judgeUnderContract(JOB, JOINT, indications).disposition())
                    .isEqualTo(Disposition.ACCEPT);
            assertThat(Job.judgeUnderContract(plant(), JOINT, indications).disposition())
                    .isEqualTo(Disposition.REJECT);
        }

        @Test
        void carriesTheFluidServiceThroughToTheCodeThatReadsIt() {
            JobContract light = Job.makeJob(JobInput.builder()
                    .number("C-9")
                    .client("x")
                    .code("B31.3")
                    .service("category_d")
                    .startedOn("2025-03-01")
                    .build());
            assertThat(Job.judgeUnderContract(light, JOINT, porosityAtTwo()).disposition())
                    .isEqualTo(Disposition.ACCEPT);
        }

        @Test
        void refusesIndicationsThatAreNotAList() {
            assertThatThrownBy(() -> Job.judgeUnderContract(JOB, JOINT, null)).hasMessageContaining("must be an array");
        }
    }

    @Nested
    class Deviations {

        @Test
        void conformsWhenEverythingMatches() {
            DeviationCheck check =
                    Job.deviations(JOB, ActualConditions.NOTHING.code("API1104").densityMinimum(2));
            assertThat(check.conforms()).isTrue();
            assertThat(check.deviations()).isEmpty();
        }

        @Test
        void catchesAJointJudgedOnTheWrongCode() {
            DeviationCheck check = Job.deviations(JOB, ActualConditions.NOTHING.code("AWSD1.1"));
            assertThat(check.conforms()).isFalse();
            assertThat(check.deviations().get(0)).isEqualTo(new Deviation("code", "API1104", "AWSD1.1"));
        }

        @Test
        void catchesAFilmReadBelowTheAgreedDensity() {
            assertThat(Job.deviations(JOB, ActualConditions.NOTHING.densityMinimum(1.8))
                            .deviations()
                            .get(0)
                            .field())
                    .isEqualTo("density");
        }

        @Test
        void doesNotComplainAboutAFilmReadAboveIt() {
            assertThat(Job.deviations(JOB, ActualConditions.NOTHING.densityMinimum(2.5))
                            .conforms())
                    .isTrue();
        }

        @Test
        void catchesACrownHigherThanTheContractAllows() {
            assertThat(Job.deviations(JOB, ActualConditions.NOTHING.reinforcementIn(0.125))
                            .deviations()
                            .get(0)
                            .field())
                    .isEqualTo("reinforcement");
        }

        @Test
        void catchesCoverageShortOfWhatWasAgreed() {
            assertThat(Job.deviations(JOB, ActualConditions.NOTHING.coverageFraction(0.05))
                            .deviations()
                            .get(0)
                            .field())
                    .isEqualTo("coverage");
        }

        @Test
        void ignoresTheFluidServiceOnACodeThatDoesNotReadIt() {
            assertThat(Job.deviations(JOB, ActualConditions.NOTHING.service("severe_cyclic"))
                            .conforms())
                    .isTrue();
        }

        @Test
        void catchesItOnACodeThatDoes() {
            assertThat(Job.deviations(plant(), ActualConditions.NOTHING.service("severe_cyclic"))
                            .deviations()
                            .get(0)
                            .field())
                    .isEqualTo("service");
        }

        @Test
        void reportsEveryDeviationAtOnce() {
            DeviationCheck check = Job.deviations(
                    JOB,
                    ActualConditions.NOTHING
                            .code("AWSD1.1")
                            .densityMinimum(1.5)
                            .reinforcementIn(0.2)
                            .coverageFraction(0.02));
            assertThat(check.deviations()).hasSize(4);
        }

        @Test
        void refusesACheckWithNothingToCompareAgainst() {
            assertThatThrownBy(() -> Job.deviations(JOB, null)).hasMessageContaining("what actually happened");
        }
    }

    @Nested
    class DescribeJob {

        @Test
        void readsAsOneLineForAReportHeader() {
            assertThat(Job.describeJob(JOB))
                    .isEqualTo("C-4471 for Sabine Midstream at Station 6, API1104 at ten coverage");
        }

        @Test
        void leavesTheSiteOutWhenThereIsNone() {
            JobContract noSite = Job.makeJob(small().build());
            assertThat(Job.describeJob(noSite)).isEqualTo("C-1 for x, API1104 at five coverage");
        }
    }

    @Nested
    class CrackRepairsAndTheIsoClass {

        private final List<Indication> shortCrack =
                List.of(Measure.makeIndication(IndicationInput.of("CRACK", 1).at(4)));

        @Test
        void startsAJobWithNoCrackRepairsAndNoClass() {
            assertThat(JOB.crackRepairs()).isFalse();
            assertThat(JOB.isoClass()).isEmpty();
            JobContract kept = Job.makeJob(small().crackRepairs(true).isoClass(IsoClass.B).build().toBuilder()
                    .build());
            assertThat(kept.crackRepairs()).isTrue();
            assertThat(kept.isoClass()).hasValue(IsoClass.B);
        }

        @Test
        void judgesACrackUnderTheAuthorityTheContractGives() {
            JobContract authorised = Job.makeJob(small().crackRepairs(true).build());
            assertThat(Job.judgeUnderContract(JOB, JOINT, shortCrack).disposition())
                    .isEqualTo(Disposition.REJECT);
            assertThat(Job.judgeUnderContract(authorised, JOINT, shortCrack).disposition())
                    .isEqualTo(Disposition.REPAIR);
        }

        @Test
        void letsTheCallerOverrideTheContractOnTheDay() {
            JobContract authorised = Job.makeJob(small().crackRepairs(true).build());
            CodeJudgement refused =
                    Job.judgeUnderContract(authorised, JOINT, shortCrack, JudgeOptions.NONE.crackRepairs(false));
            assertThat(refused.disposition()).isEqualTo(Disposition.REJECT);
        }

        @Test
        void raisesTheDensityFloorToTheIsoClass() {
            assertThat(Limits.isoMinimumDensity(IsoClass.A)).isEqualTo(2.0);
            assertThat(Limits.isoMinimumDensity(IsoClass.B)).isEqualTo(2.3);
            assertThat(Job.densityFloor(JOB)).isEqualTo(2);
            assertThat(Job.densityFloor(Job.makeJob(small().isoClass(IsoClass.B).build())))
                    .isEqualTo(2.3);
            assertThat(Job.densityFloor(Job.makeJob(
                            small().isoClass(IsoClass.A).densityMinimum(2.5).build())))
                    .isEqualTo(2.5);
            assertThatThrownBy(() -> Limits.isoMinimumDensity(null)).hasMessageContaining("A or B");
        }

        @Test
        void reportsAFilmUnderTheClassFloorAsADeviation() {
            JobContract classB = Job.makeJob(small().isoClass(IsoClass.B).build());
            DeviationCheck check = Job.deviations(classB, ActualConditions.NOTHING.densityMinimum(2.1));
            assertThat(check.deviations()).containsExactly(new Deviation("density", 2.3, 2.1));
            assertThat(Job.deviations(JOB, ActualConditions.NOTHING.densityMinimum(2.1))
                            .conforms())
                    .isTrue();
        }
    }
}
