package io.github.rkaushikdev.kerfline.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ShippingTest {

    @Nested
    class TransportIndex {

        @Test
        void countsAnythingUpToFiveHundredthsAsNought() {
            assertThat(Shipping.transportIndex(0)).isZero();
            assertThat(Shipping.transportIndex(0.04)).isZero();
            assertThat(Shipping.transportIndex(0.05)).isZero();
        }

        @Test
        void roundsTheReadingAtOneMetreUpToTheNextTenth() {
            assertThat(Shipping.transportIndex(0.06)).isEqualTo(0.1);
            assertThat(Shipping.transportIndex(0.41)).isEqualTo(0.5);
            assertThat(Shipping.transportIndex(1.0)).isEqualTo(1.0);
            assertThat(Shipping.transportIndex(3.33)).isEqualTo(3.4);
        }

        @Test
        void refusesANegativeReading() {
            assertThatThrownBy(() -> Shipping.transportIndex(-1)).hasMessageContaining("one metre");
        }
    }

    @Nested
    class LabelFor {

        @Test
        void labelsAQuietPackageWhiteI() {
            ShippingLabel label = Shipping.labelFor(0.3, 0.02);
            assertThat(label.label()).isEqualTo(PackageLabel.WHITE_I);
            assertThat(label.transportIndex()).isZero();
            assertThat(label.shippable()).isTrue();
        }

        @Test
        void takesYellowIIFromTheIndex() {
            ShippingLabel label = Shipping.labelFor(30, 0.4);
            assertThat(label.label()).isEqualTo(PackageLabel.YELLOW_II);
            assertThat(label.decidedBy()).isEqualTo("transport index");
        }

        @Test
        void letsAHotFaceRaiseTheLabelPastWhatTheIndexSays() {
            ShippingLabel label = Shipping.labelFor(60, 0.4);
            assertThat(label.label()).isEqualTo(PackageLabel.YELLOW_III);
            assertThat(label.decidedBy()).isEqualTo("surface");
            assertThat(Shipping.labelFor(0.6, 0).label()).isEqualTo(PackageLabel.YELLOW_II);
        }

        @Test
        void sendsAPackageOverTheYellowIIILimitsAsExclusiveUse() {
            assertThat(Shipping.labelFor(150, 12).label()).isEqualTo(PackageLabel.EXCLUSIVE_USE);
            ShippingLabel hot = Shipping.labelFor(500, 5);
            assertThat(hot.label()).isEqualTo(PackageLabel.EXCLUSIVE_USE);
            assertThat(hot.shippable()).isTrue();
            assertThat(hot.label().label()).isEqualTo("exclusive use only");
        }

        @Test
        void refusesAPackagePastTheExclusiveUseLimit() {
            ShippingLabel label = Shipping.labelFor(1200, 5);
            assertThat(label.shippable()).isFalse();
            assertThat(label.refusal()).hasValue("the surface reads 1200 mrem/h, over the exclusive use limit of 1000");
        }

        @Test
        void refusesASurfaceReadingBelowTheOneAtAMetre() {
            assertThatThrownBy(() -> Shipping.labelFor(1, 2)).hasMessageContaining("less at its surface");
        }

        @Test
        void listsTheCategoriesLowestFirst() {
            assertThat(Shipping.labels())
                    .containsExactly(
                            PackageLabel.WHITE_I,
                            PackageLabel.YELLOW_II,
                            PackageLabel.YELLOW_III,
                            PackageLabel.EXCLUSIVE_USE);
            assertThat(PackageLabel.YELLOW_III.transportIndexUpTo()).isEqualTo(10);
            assertThat(PackageLabel.YELLOW_II.surfaceUpToMremPerHour()).isEqualTo(50);
        }
    }
}
