package io.github.rkaushikdev.kerfline.accept;

import io.github.rkaushikdev.kerfline.indication.Indication;
import io.github.rkaushikdev.kerfline.indication.Measure;
import io.github.rkaushikdev.kerfline.indication.WeldWindow;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * The parts every acceptance code turned out to share.
 *
 * <p>Four codes were written before this class existed and all four had grown the same three
 * shapes: reject anything planar, compare a size against a cap, and roll a list of verdicts up into
 * one answer with an acceptance standing in when the list is empty. The wording of the reasons had
 * already started to drift between them, which is the point at which shared code stops being a
 * tidiness question.
 *
 * <p>What is deliberately NOT here is any of the numbers. Every cap, fraction, window and exception
 * stays in the code it belongs to, because that is the part that differs and the part somebody has
 * to be able to check against a printed standard.
 */
public final class Common {

    private Common() {}

    /** Planar means a crack or an unfused face, and no code tolerates one. */
    public static ClauseVerdict rejectPlanar(String code, String clause, Indication indication) {
        return Verdict.makeVerdict(
                Disposition.REJECT,
                code,
                clause,
                indication.label() + " is not permitted at any size",
                indication,
                Measure.governingSizeIn(indication));
    }

    /**
     * Compare a size against a cap. Inclusive at the cap, because every code in this package writes
     * its limits as "shall not exceed".
     */
    public static ClauseVerdict capVerdict(CapCheck check) {
        if (check.sizeIn() <= check.capIn()) {
            return Verdict.accept(check.code(), check.clause());
        }
        return Verdict.makeVerdict(
                Disposition.REPAIR,
                check.code(),
                check.clause(),
                check.what() + " " + Num.text(Num.round(check.sizeIn(), 4)) + " " + check.units() + ", cap is "
                        + Num.text(Num.round(check.capIn(), 4)) + " " + check.units(),
                check.indication(),
                Num.round(check.sizeIn(), 4));
    }

    public static WeldWindow windowTotal(List<Indication> indications, double windowIn) {
        return windowTotal(indications, windowIn, Measure::governingSizeIn);
    }

    /**
     * The heaviest window of a given length, measured by {@code sizeOf}. Returns the total and where
     * it started, so a report can say which foot of weld was the problem rather than only that the
     * joint failed. Indications with no position take no part.
     */
    public static WeldWindow windowTotal(
            List<Indication> indications, double windowIn, ToDoubleFunction<Indication> sizeOf) {
        return Measure.worstWindow(indications, windowIn, sizeOf);
    }

    /**
     * Roll a set of verdicts into the joint answer. An empty set means nothing was found rather than
     * nothing was checked, so it settles as an acceptance against the clause that did the checking.
     */
    public static JointVerdict settle(List<ClauseVerdict> verdicts, String code, String clause) {
        Guard.requireArray(verdicts, "verdicts");
        List<ClauseVerdict> all = verdicts.isEmpty() ? List.of(Verdict.accept(code, clause)) : verdicts;
        return Verdict.combine(all, code);
    }

    /** The position along the weld of an indication already known to carry one. */
    static double at(Indication indication) {
        return indication.at().getAsDouble();
    }
}
