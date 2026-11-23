package io.github.rkaushikdev.kerfline.accept;

import static io.github.rkaushikdev.kerfline.accept.Indications.at;
import static io.github.rkaushikdev.kerfline.accept.Indications.ind;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.kerfline.indication.Indication;
import io.github.rkaushikdev.kerfline.indication.IndicationType;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class Api1104WeldTest {

    @Nested
    class WhereTheWorstFootStarts {

        @Test
        void namesTheStartOfTheFootThatBrokeTheCap() {
            List<Indication> set = List.of(at("IP", 0.4, 7), at("IP", 0.4, 8), at("IP", 0.4, 9));
            assertThat(Api1104.judgeAggregate(set, IndicationType.IP, 37.7))
                    .singleElement()
                    .satisfies(verdict -> assertThat(verdict.reason())
                            .hasValue("incomplete penetration totals 1.2 in in 12 in from 7 in, cap is 1 in"));
        }

        @Test
        void namesAStartBeforeTheTieInWhenTheFootRunsPastIt() {
            List<Indication> set =
                    List.of(at("IP", 0.4, 19.5), at("IP", 0.4, 20.3), at("IP", 0.4, 0.2), at("IP", 0.4, 0.9));
            Api1104Rule penetration = Api1104.lengthRules().get(0);
            assertThat(Api1104Weld.judgeAggregate(set, penetration, 20.8))
                    .anyMatch(verdict -> verdict.reason().orElseThrow().contains("from 19.5 in"));
        }

        @Test
        void givesNoStartWhenNothingWasLocated() {
            List<Indication> set = List.of(ind("IP", 0.6), ind("IP", 0.6));
            Api1104Rule penetration = Api1104.lengthRules().get(0);
            assertThat(Api1104Weld.judgeAggregate(set, penetration, 37.7))
                    .singleElement()
                    .satisfies(verdict -> assertThat(verdict.reason())
                            .hasValue("incomplete penetration totals 1.2 in in 12 in, cap is 1 in"));
        }

        @Test
        void namesTheStartOfTheCrowdedFootOfImperfections() {
            List<Indication> set = List.of(at("SLAG", 1.5, 30), at("IP", 0.9, 33));
            assertThat(Api1104Weld.judgeAccumulation(set, 37.7)).singleElement().satisfies(verdict -> assertThat(
                            verdict.reason())
                    .hasValue("imperfections totals 2.4 in in 12 in from 30 in, cap is 2 in"));
        }
    }

    @Nested
    class TheSharedNumbers {

        @Test
        void readsTheEightPercentFromTheCodeItself() {
            assertThat(Api1104.shortWeldPercent()).isEqualTo(0.08);
        }

        @Test
        void aggregatesOnlyWhatTheRuleJudges() {
            Api1104Rule highLow = Api1104.lengthRules().get(1);
            List<Indication> plain = List.of(at("IP", 2, 0), at("IP", 2, 3));
            assertThat(Api1104Weld.judgeAggregate(plain, highLow, 37.7)).isEmpty();
        }
    }
}
