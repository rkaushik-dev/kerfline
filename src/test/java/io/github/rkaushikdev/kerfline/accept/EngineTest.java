package io.github.rkaushikdev.kerfline.accept;

import static io.github.rkaushikdev.kerfline.accept.Indications.at;
import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.kerfline.indication.Indication;
import io.github.rkaushikdev.kerfline.joint.Joint;
import io.github.rkaushikdev.kerfline.joint.JointInput;
import io.github.rkaushikdev.kerfline.joint.Spec;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EngineTest {

    private static final Joint JOINT = Spec.makeJoint(
            JointInput.builder().id("J-118").nominalSize(6).schedule(40).build());

    /** A joint record with an outside diameter and no wall, which no code can judge. */
    private static Joint withoutWall(String id) {
        return new Joint(
                id,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "5G",
                "SMAW",
                "butt",
                false,
                Optional.empty(),
                Optional.empty(),
                6.625,
                Double.NaN,
                false,
                List.of());
    }

    @Nested
    class TheRegistry {

        @Test
        void carriesTheThreeCodes() {
            assertThat(Engine.codes()).containsExactly("API1104", "ASME8", "B31.3", "AWSD1.1");
        }

        @Test
        void saysWhatEachCodeNeeds() {
            assertThat(Engine.describeCode("API1104").needs()).containsExactly("wallIn", "weldLengthIn");
            assertThat(Engine.describeCode("ASME8").needs()).containsExactly("thicknessIn");
            assertThat(Engine.describeCode("B31.3").needs()).containsExactly("thinnerWallIn", "service");
            assertThat(Engine.describeCode("AWSD1.1").needs()).containsExactly("thicknessIn", "loading");
        }

        @Test
        void refusesACodeNobodyWrote() {
            assertThatThrownBy(() -> Engine.describeCode("BS2910")).hasMessageContaining("no acceptance code BS2910");
        }
    }

    @Nested
    class ContextFor {

        @Test
        void givesApi1104TheWallAndTheWeldLength() {
            Api1104Context context = (Api1104Context) Engine.contextFor("API1104", JOINT);
            assertThat(context.wallIn()).isCloseTo(0.28, digits(4));
            assertThat(context.weldLengthIn()).isCloseTo(20.8129, digits(3));
        }

        @Test
        void tellsApi1104WhetherTheCompanyRepairsCracks() {
            assertThat(((Api1104Context) Engine.contextFor("API1104", JOINT)).crackRepairs())
                    .isFalse();
            Api1104Context authorised =
                    (Api1104Context) Engine.contextFor("API1104", JOINT, JudgeOptions.NONE.crackRepairs(true));
            assertThat(authorised.crackRepairs()).isTrue();
            assertThat(authorised.weldLengthIn()).isCloseTo(20.8129, digits(3));
        }

        @Test
        void repairsAShortCrackOnlyUnderTheAuthority() {
            List<Indication> crack = List.of(at("CRACK", 1, 4));
            assertThat(Engine.judge("API1104", crack, JOINT).disposition()).isEqualTo(Disposition.REJECT);
            assertThat(Engine.judge("API1104", crack, JOINT, JudgeOptions.NONE.crackRepairs(true))
                            .disposition())
                    .isEqualTo(Disposition.REPAIR);
            assertThat(Engine.judge("B31.3", crack, JOINT, JudgeOptions.NONE.crackRepairs(true))
                            .disposition())
                    .isEqualTo(Disposition.REJECT);
        }

        @Test
        void keepsTheAuthorityAcrossTheOtherSetters() {
            JudgeOptions options = JudgeOptions.NONE
                    .crackRepairs(true)
                    .weldLengthIn(30)
                    .loading("cyclic")
                    .reinforcementIn(0.1)
                    .matingWallIn(0.3)
                    .service("normal");
            assertThat(options.crackRepairs()).isTrue();
            assertThat(options.weldLengthIn()).isEqualTo(30);
        }

        @Test
        void givesSectionViiiTheWeldThickness() {
            assertThat(((Asme8Context) Engine.contextFor("ASME8", JOINT)).thicknessIn())
                    .isCloseTo(0.28, digits(4));
        }

        @Test
        void givesB313TheThinnerComponentAndAService() {
            AsmeB313Context context = (AsmeB313Context) Engine.contextFor("B31.3", JOINT);
            assertThat(context.thinnerWallIn()).isCloseTo(0.28, digits(4));
            assertThat(context.service()).isEqualTo("normal");
        }

        @Test
        void takesTheThinnerOfTwoMatingWalls() {
            AsmeB313Context context =
                    (AsmeB313Context) Engine.contextFor("B31.3", JOINT, JudgeOptions.NONE.matingWallIn(0.2));
            assertThat(context.thinnerWallIn()).isCloseTo(0.2, digits(4));
        }

        @Test
        void doesNotTakeTheThickerOne() {
            AsmeB313Context context =
                    (AsmeB313Context) Engine.contextFor("B31.3", JOINT, JudgeOptions.NONE.matingWallIn(0.5));
            assertThat(context.thinnerWallIn()).isCloseTo(0.28, digits(4));
        }

        @Test
        void takesAnOverridingWeldLength() {
            Api1104Context context =
                    (Api1104Context) Engine.contextFor("API1104", JOINT, JudgeOptions.NONE.weldLengthIn(30));
            assertThat(context.weldLengthIn()).isEqualTo(30);
        }

        @Test
        void refusesAJointWithNoUsableWall() {
            assertThatThrownBy(() -> Engine.contextFor("API1104", withoutWall("J-1")))
                    .hasMessageContaining("no usable wall");
            assertThatThrownBy(() -> Engine.contextFor("API1104", null))
                    .hasMessageContaining("a joint record is needed");
        }
    }

    @Nested
    class Judge {

        @Test
        void acceptsACleanJointUnderAnyCode() {
            for (String code : Engine.codes()) {
                assertThat(Engine.judge(code, List.of(), JOINT).disposition()).isEqualTo(Disposition.ACCEPT);
            }
        }

        @Test
        void carriesTheJointIdIntoTheResult() {
            assertThat(Engine.judge("API1104", List.of(), JOINT).jointId()).contains("J-118");
        }

        @Test
        void reportsTheContextItJudgedOn() {
            CodeJudgement result = Engine.judge("API1104", List.of(), JOINT);
            assertThat(((Api1104Context) result.context()).weldLengthIn()).isGreaterThan(20);
        }

        @Test
        void takesAJointOutForACrackUnderAnyCode() {
            List<Indication> indications = List.of(at("CRACK", 0.05, 1));
            for (String code : Engine.codes()) {
                assertThat(Engine.judge(code, indications, JOINT).disposition()).isEqualTo(Disposition.REJECT);
            }
        }

        @Test
        void refusesACodeItDoesNotCarry() {
            assertThatThrownBy(() -> Engine.judge("BS2910", List.of(), JOINT))
                    .hasMessageContaining("no acceptance code");
        }

        @Test
        void refusesIndicationsThatAreNotAnArray() {
            assertThatThrownBy(() -> Engine.judge("API1104", null, JOINT)).hasMessageContaining("must be an array");
        }
    }

    @Nested
    class TheCodesDisagreeWhichIsThePoint {

        @Test
        void splitsOnASlagInclusionThatOneCodeCapsTighterThanAnother() {
            // 0.15 in of slag: B31.3 caps it at a third of 0.28 which is 0.093 in,
            // API 1104 caps it at 2 in and does not care.
            List<Indication> indications = List.of(at("SLAG", 0.15, 1));
            assertThat(Engine.judge("API1104", indications, JOINT).disposition())
                    .isEqualTo(Disposition.ACCEPT);
            assertThat(Engine.judge("B31.3", indications, JOINT).disposition()).isEqualTo(Disposition.REPAIR);
        }

        @Test
        void splitsOnIncompletePenetrationBetweenPipelineAndPlantWork() {
            List<Indication> indications = List.of(at("IP", 0.5, 1));
            assertThat(Engine.judge("API1104", indications, JOINT).disposition())
                    .isEqualTo(Disposition.ACCEPT);
            assertThat(Engine.judge("B31.3", indications, JOINT).disposition()).isEqualTo(Disposition.REJECT);
        }

        @Test
        void movesTheB313AnswerWithTheFluidService() {
            List<Indication> indications = List.of(at("IP", 0.5, 1));
            CodeJudgement normal = Engine.judge("B31.3", indications, JOINT, JudgeOptions.NONE.service("normal"));
            CodeJudgement light = Engine.judge("B31.3", indications, JOINT, JudgeOptions.NONE.service("category_d"));
            assertThat(normal.disposition()).isEqualTo(Disposition.REJECT);
            assertThat(light.disposition()).isEqualTo(Disposition.ACCEPT);
        }
    }

    @Nested
    class JudgeAll {

        @Test
        void runsEveryCodeOverTheSameJoint() {
            List<CodeRun> results = Engine.judgeAll(List.of(), JOINT);
            assertThat(results).hasSize(Engine.codes().size());
            assertThat(results).allMatch(r -> r.outcome().equals(Optional.of(Disposition.ACCEPT)));
        }

        @Test
        void showsTheCodesDisagreeingSideBySide() {
            List<CodeRun> results = Engine.judgeAll(List.of(at("SLAG", 0.15, 1)), JOINT);
            Map<String, Optional<Disposition>> byCode = new LinkedHashMap<>();
            results.forEach(r -> byCode.put(r.code(), r.outcome()));
            assertThat(byCode.get("API1104")).contains(Disposition.ACCEPT);
            assertThat(byCode.get("B31.3")).contains(Disposition.REPAIR);
        }

        @Test
        void reportsACodeThatCouldNotRunRatherThanThrowingTheLotAway() {
            List<CodeRun> results = Engine.judgeAll(List.of(), withoutWall("J-1"));
            assertThat(results).allMatch(r -> r.outcome().isEmpty());
            assertThat(results.get(0))
                    .isInstanceOfSatisfying(CodeFailure.class, failure -> assertThat(failure.message())
                            .contains("no usable wall"));
        }
    }

    @Nested
    class DefaultCodeFor {

        @Test
        void sendsPipelineWorkToApi1104() {
            assertThat(Engine.defaultCodeFor("pipeline")).isEqualTo("API1104");
        }

        @Test
        void sendsVesselWorkToSectionViii() {
            assertThat(Engine.defaultCodeFor("vessel")).isEqualTo("ASME8");
        }

        @Test
        void sendsStructuralWorkToAwsD11() {
            assertThat(Engine.defaultCodeFor("structural")).isEqualTo("AWSD1.1");
        }

        @Test
        void sendsEverythingElseToB313() {
            assertThat(Engine.defaultCodeFor("process")).isEqualTo("B31.3");
            assertThat(Engine.defaultCodeFor(null)).isEqualTo("B31.3");
        }
    }

    @Nested
    class AwsD11ThroughTheEngine {

        @Test
        void judgesACleanJoint() {
            assertThat(Engine.judge("AWSD1.1", List.of(), JOINT).disposition()).isEqualTo(Disposition.ACCEPT);
        }

        @Test
        void takesTheWeldThicknessAndAStaticLoadingByDefault() {
            AwsD11Context context = (AwsD11Context) Engine.contextFor("AWSD1.1", JOINT);
            assertThat(context.thicknessIn()).isCloseTo(0.28, digits(4));
            assertThat(context.loading()).isEqualTo("static");
        }

        @Test
        void takesTheLoadingFromTheCaller() {
            AwsD11Context context =
                    (AwsD11Context) Engine.contextFor("AWSD1.1", JOINT, JudgeOptions.NONE.loading("cyclic"));
            assertThat(context.loading()).isEqualTo("cyclic");
        }

        @Test
        void givesTheSameJointDifferentAnswersUnderTheTwoLoadings() {
            List<Indication> indications = List.of(at("SLAG", 0.11, 1));
            CodeJudgement statically = Engine.judge("AWSD1.1", indications, JOINT);
            CodeJudgement cyclically = Engine.judge("AWSD1.1", indications, JOINT, JudgeOptions.NONE.loading("cyclic"));
            assertThat(statically.disposition()).isEqualTo(Disposition.ACCEPT);
            assertThat(cyclically.disposition()).isEqualTo(Disposition.REPAIR);
        }

        @Test
        void isHarsherThanApi1104OnTheSameSmallSlagLine() {
            List<Indication> indications = List.of(at("SLAG", 0.5, 1));
            assertThat(Engine.judge("API1104", indications, JOINT).disposition())
                    .isEqualTo(Disposition.ACCEPT);
            assertThat(Engine.judge("AWSD1.1", indications, JOINT).disposition())
                    .isEqualTo(Disposition.REPAIR);
        }

        @Test
        void runsInJudgeAllAlongsideTheOtherThree() {
            List<CodeRun> results = Engine.judgeAll(List.of(), JOINT);
            assertThat(results).hasSize(4);
            assertThat(results.stream().map(CodeRun::code)).contains("AWSD1.1");
        }
    }
}
