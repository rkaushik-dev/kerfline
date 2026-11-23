package io.github.rkaushikdev.kerfline.geom;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SetupTest {

    private static final double OD = 6.625;
    private static final double WALL = 0.28;
    private static final double SOURCE = 0.12;

    private static WorkingDistanceQuestion.Builder question() {
        return WorkingDistanceQuestion.builder().odIn(OD).wallIn(WALL).sourceSizeIn(SOURCE);
    }

    private static SetupInput.Builder setup() {
        return SetupInput.builder().odIn(OD).wallIn(WALL).sourceSizeIn(SOURCE);
    }

    @Nested
    class StandoffIn {

        @Test
        void isTheWholeDiameterOnADoubleWallShot() {
            assertThat(Setup.standoffIn(OD, WALL, ViewingTechnique.DWSI)).isCloseTo(6.625, digits(3));
        }

        @Test
        void isTheWallWhenTheSourceIsInside() {
            assertThat(Setup.standoffIn(OD, WALL, ViewingTechnique.SWSI_PANORAMIC))
                    .isCloseTo(0.28, digits(3));
        }

        @Test
        void refusesADiameterOrWallAtZero() {
            assertThatThrownBy(() -> Setup.standoffIn(0, 0.28, ViewingTechnique.DWSI))
                    .hasMessageContaining("outside diameter");
        }
    }

    @Nested
    class DistanceForUnsharpness {

        @Test
        void readsTheLimitOffThePenetratedThicknessNotTheWall() {
            UnsharpnessDistance check = Setup.distanceForUnsharpness(OD, WALL, SOURCE);
            assertThat(check.technique()).isEqualTo(ViewingTechnique.DWSI);
            assertThat(check.limitIn()).isEqualTo(0.02);
            assertThat(check.objectToFilmIn()).isCloseTo(6.625, digits(3));
        }

        @Test
        void wantsTheSourceCrankedWellOutOnADoubleWallShot() {
            assertThat(Setup.distanceForUnsharpness(OD, WALL, SOURCE).minimumSourceToObjectIn())
                    .isGreaterThan(35);
        }

        @Test
        void wantsFarLessOnceTheSourceGoesInside() {
            UnsharpnessDistance inside = Setup.distanceForUnsharpness(OD, WALL, SOURCE, true);
            assertThat(inside.minimumSourceToObjectIn())
                    .isLessThan(Setup.distanceForUnsharpness(OD, WALL, SOURCE).minimumSourceToObjectIn());
        }

        @Test
        void wantsLessWithASmallerSource() {
            UnsharpnessDistance small = Setup.distanceForUnsharpness(OD, WALL, 0.06);
            assertThat(small.minimumSourceToObjectIn())
                    .isCloseTo(Setup.distanceForUnsharpness(OD, WALL, SOURCE).minimumSourceToObjectIn() / 2, digits(1));
        }
    }

    @Nested
    class ShortestWorkingDistance {

        @Test
        void takesWhicheverConstraintReachesFurtherOut() {
            WorkingDistance answer =
                    Setup.shortestWorkingDistance(question().maxShots(6).build());
            assertThat(answer.sourceToObjectIn().getAsDouble())
                    .isGreaterThanOrEqualTo(answer.minimumSourceToObjectIn());
            assertThat(answer.sourceToObjectIn().getAsDouble())
                    .isGreaterThanOrEqualTo(answer.requiredForShotsIn().getAsDouble());
        }

        @Test
        void namesWhichOneGoverned() {
            WorkingDistance answer =
                    Setup.shortestWorkingDistance(question().maxShots(6).build());
            assertThat(answer.governedBy()).isIn(Constraint.UNSHARPNESS, Constraint.COVERAGE);
        }

        @Test
        void isGovernedByCoverageWhenFewShotsAreAllowed() {
            WorkingDistance few =
                    Setup.shortestWorkingDistance(question().maxShots(6).build());
            WorkingDistance many =
                    Setup.shortestWorkingDistance(question().maxShots(20).build());
            assertThat(few.sourceToObjectIn().getAsDouble())
                    .isGreaterThanOrEqualTo(many.sourceToObjectIn().getAsDouble());
        }

        @Test
        void saysPlainlyWhenTheShotCountCannotBeMetAtAnyDistance() {
            WorkingDistance answer =
                    Setup.shortestWorkingDistance(question().maxShots(2).build());
            assertThat(answer.shotsReachable()).isFalse();
            assertThat(answer.sourceToObjectIn()).isEmpty();
            assertThat(answer.reason()).hasValueSatisfying(reason -> assertThat(reason)
                    .contains("cannot cover this joint at any distance"));
        }

        @Test
        void doesNotTradeUnsharpnessAwayForAShotCount() {
            WorkingDistance answer =
                    Setup.shortestWorkingDistance(question().maxShots(20).build());
            assertThat(answer.sourceToObjectIn().getAsDouble())
                    .isGreaterThanOrEqualTo(answer.minimumSourceToObjectIn());
        }

        @Test
        void letsAnIsoClassPushTheSourceFurtherOut() {
            WorkingDistance plain =
                    Setup.shortestWorkingDistance(question().maxShots(20).build());
            WorkingDistance classB = Setup.shortestWorkingDistance(
                    question().maxShots(20).isoClass(IsoClass.B).build());
            assertThat(plain.isoMinimumIn()).isEmpty();
            assertThat(plain.governedBy()).isNotEqualTo(Constraint.ISO_17636);
            assertThat(classB.governedBy()).isEqualTo(Constraint.ISO_17636);
            assertThat(classB.isoMinimumIn()).hasValue(54.86);
            assertThat(classB.sourceToObjectIn()).hasValue(54.86);
            assertThat(classB.governedBy().label()).isEqualTo("ISO 17636");
        }

        @Test
        void leavesTheAnswerAloneWhenTheClassIsTheGentlerRule() {
            WorkingDistance plain =
                    Setup.shortestWorkingDistance(question().maxShots(6).build());
            WorkingDistance classA = Setup.shortestWorkingDistance(
                    question().maxShots(6).isoClass(IsoClass.A).build());
            assertThat(classA.isoMinimumIn().getAsDouble())
                    .isLessThan(classA.sourceToObjectIn().getAsDouble());
            assertThat(classA.sourceToObjectIn()).isEqualTo(plain.sourceToObjectIn());
            assertThat(classA.governedBy()).isEqualTo(plain.governedBy());
        }

        @Test
        void keepsTheClassWhenAQuestionIsRebuilt() {
            WorkingDistanceQuestion asked = question().isoClass(IsoClass.B).build();
            assertThat(asked.toBuilder().build().isoClass()).hasValue(IsoClass.B);
            assertThat(question().build().isoClass()).isEmpty();
        }

        @Test
        void refusesAShotCountThatIsNotAWholeNumberAboveZero() {
            assertThatThrownBy(() ->
                            Setup.shortestWorkingDistance(question().maxShots(0).build()))
                    .hasMessageContaining("shot count");
            assertThatThrownBy(() -> Setup.shortestWorkingDistance(
                            question().maxShots(-1).build()))
                    .hasMessageContaining("greater than 0");
        }
    }

    @Nested
    class ResolveSetup {

        @Test
        void fillsTheStandoffInFromTheViewingTechnique() {
            ResolvedSetup resolved =
                    Setup.resolveSetup(setup().sourceToObjectIn(48).build());
            assertThat(resolved.distances().objectToFilmIn()).isCloseTo(6.625, digits(3));
            assertThat(resolved.distances().sourceToFilmIn()).isCloseTo(54.625, digits(3));
        }

        @Test
        void takesAnExplicitStandoffOverTheDerivedOne() {
            ResolvedSetup resolved = Setup.resolveSetup(
                    setup().sourceToObjectIn(48).objectToFilmIn(2).build());
            assertThat(resolved.distances().objectToFilmIn()).isEqualTo(2);
        }

        @Test
        void checksTheUnsharpnessAgainstWhatItResolvedTo() {
            ResolvedSetup resolved =
                    Setup.resolveSetup(setup().sourceToObjectIn(48).build());
            assertThat(resolved.sharpness().pass()).isTrue();
            assertThat(resolved.workable()).isTrue();
        }

        @Test
        void failsASetupCrankedInTooClose() {
            ResolvedSetup resolved =
                    Setup.resolveSetup(setup().sourceToObjectIn(12).build());
            assertThat(resolved.sharpness().pass()).isFalse();
            assertThat(resolved.workable()).isFalse();
            assertThat(resolved.sharpness().shortfallIn()).isGreaterThan(0);
        }

        @Test
        void plansTheShotsAtTheDistanceItResolvedTo() {
            ResolvedSetup resolved =
                    Setup.resolveSetup(setup().sourceToObjectIn(48).build());
            assertThat(resolved.plan()).hasSize(resolved.shots());
            assertThat(resolved.plan().get(0).clock()).isEqualTo("12:00");
        }

        @Test
        void resolvesFromSourceToFilmInsteadOfSourceToObject() {
            ResolvedSetup resolved =
                    Setup.resolveSetup(setup().sourceToFilmIn(54.625).build());
            assertThat(resolved.distances().sourceToObjectIn()).isCloseTo(48, digits(2));
        }

        @Test
        void refusesWhenItHasTooFewDistancesToWorkWith() {
            assertThatThrownBy(() -> Setup.resolveSetup(setup().build())).hasMessageContaining("two of the three");
        }

        @Test
        void refusesSomethingThatIsNotASetup() {
            assertThatThrownBy(() -> Setup.resolveSetup(null)).hasMessageContaining("resolved from an object");
        }

        @Test
        void agreesWithTheShortestWorkingDistanceItRecommends() {
            WorkingDistance answer =
                    Setup.shortestWorkingDistance(question().maxShots(8).build());
            ResolvedSetup resolved = Setup.resolveSetup(
                    setup().sourceToObjectIn(answer.sourceToObjectIn().getAsDouble())
                            .build());
            assertThat(resolved.workable()).isTrue();
            assertThat(resolved.shots()).isLessThanOrEqualTo(8);
        }
    }
}
