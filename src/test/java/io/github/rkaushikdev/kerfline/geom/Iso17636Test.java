package io.github.rkaushikdev.kerfline.geom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class Iso17636Test {

    @Nested
    class TheClasses {

        @Test
        void doublesTheFactorForClassB() {
            assertThat(IsoClass.A.factor()).isEqualTo(7.5);
            assertThat(IsoClass.B.factor()).isEqualTo(15);
            assertThat(IsoClass.B.label()).isEqualTo("class B");
        }
    }

    @Nested
    class EffectiveObjectToFilm {

        @Test
        void standsTheThicknessInForAFilmCloserThanOnePointTwoOfIt() {
            assertThat(Iso17636.effectiveObjectToFilmMm(5, 10)).isEqualTo(10);
            assertThat(Iso17636.effectiveObjectToFilmMm(11.9, 10)).isEqualTo(10);
        }

        @Test
        void keepsTheDistanceOnceTheFilmIsFurtherOut() {
            assertThat(Iso17636.effectiveObjectToFilmMm(12, 10)).isEqualTo(12);
            assertThat(Iso17636.effectiveObjectToFilmMm(15, 10)).isEqualTo(15);
        }

        @Test
        void refusesAWallOfNothing() {
            assertThatThrownBy(() -> Iso17636.effectiveObjectToFilmMm(5, 0)).hasMessageContaining("nominal thickness");
        }
    }

    @Nested
    class MinimumSourceToObject {

        @Test
        void readsTheTwoThirdsPowerInMillimetres() {
            // 27 mm to the two thirds is 9, so class A wants 7.5 x 3 x 9.
            assertThat(Iso17636.minimumSourceToObjectMm(3, 27, 10, IsoClass.A)).isEqualTo(202.5);
            assertThat(Iso17636.minimumSourceToObjectMm(3, 27, 10, IsoClass.B)).isEqualTo(405);
        }

        @Test
        void worksInchFiguresThroughMillimetres() {
            // A 0.12 in source on a contact shot of 0.28 in wall.
            assertThat(Iso17636.minimumSourceToObjectIn(0.12, 0.28, 0.28, IsoClass.A))
                    .isEqualTo(3.33);
            assertThat(Iso17636.minimumSourceToObjectIn(0.12, 0.28, 0.28, IsoClass.B))
                    .isEqualTo(6.66);
            assertThat(Iso17636.minimumSourceToObjectIn(0.12, 6.625, 0.28, IsoClass.B))
                    .isEqualTo(54.86);
        }

        @Test
        void growsSlowerThanTheStandoff() {
            double near = Iso17636.minimumSourceToObjectIn(0.12, 2, 0.28, IsoClass.A);
            double far = Iso17636.minimumSourceToObjectIn(0.12, 4, 0.28, IsoClass.A);
            assertThat(far / near).isBetween(1.55, 1.6);
        }

        @Test
        void judgesASetupAgainstTheClass() {
            assertThat(Iso17636.meetsClass(7, 0.12, 0.28, 0.28, IsoClass.B)).isTrue();
            assertThat(Iso17636.meetsClass(6, 0.12, 0.28, 0.28, IsoClass.B)).isFalse();
            assertThat(Iso17636.meetsClass(6, 0.12, 0.28, 0.28, IsoClass.A)).isTrue();
        }

        @Test
        void refusesNoClassAndNoSource() {
            assertThatThrownBy(() -> Iso17636.minimumSourceToObjectMm(3, 27, 10, null))
                    .hasMessageContaining("A or B");
            assertThatThrownBy(() -> Iso17636.minimumSourceToObjectMm(0, 27, 10, IsoClass.A))
                    .hasMessageContaining("source size");
            assertThatThrownBy(() -> Iso17636.meetsClass(0, 0.12, 0.28, 0.28, IsoClass.A))
                    .hasMessageContaining("source to object");
        }
    }

    @Nested
    class CheckAndTestClass {

        @Test
        void carriesTheMinimumAndTheShortfall() {
            IsoDistanceCheck check = Iso17636.check(6, 0.12, 0.28, 0.28, IsoClass.B);
            assertThat(check.testClass()).isEqualTo(IsoClass.B);
            assertThat(check.minimumIn()).isEqualTo(6.66);
            assertThat(check.sourceToObjectIn()).isEqualTo(6);
            assertThat(check.pass()).isFalse();
            assertThat(check.shortfallIn()).isEqualTo(0.66);
            assertThat(Iso17636.check(7, 0.12, 0.28, 0.28, IsoClass.B).shortfallIn())
                    .isZero();
        }

        @Test
        void readsTheClassHoweverItWasWritten() {
            assertThat(Iso17636.testClass("B")).isEqualTo(IsoClass.B);
            assertThat(Iso17636.testClass("class a")).isEqualTo(IsoClass.A);
            assertThat(Iso17636.testClass(" b ")).isEqualTo(IsoClass.B);
        }

        @Test
        void refusesAClassTheStandardDoesNotHave() {
            assertThatThrownBy(() -> Iso17636.testClass("C")).hasMessage("an ISO testing class is A or B, not C");
            assertThatThrownBy(() -> Iso17636.testClass(null)).hasMessageContaining("A or B");
        }
    }
}
