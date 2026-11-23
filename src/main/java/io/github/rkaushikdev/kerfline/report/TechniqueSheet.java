package io.github.rkaushikdev.kerfline.report;

import io.github.rkaushikdev.kerfline.exposure.ExposurePlan;
import io.github.rkaushikdev.kerfline.geom.IsoDistanceCheck;
import io.github.rkaushikdev.kerfline.geom.PlannedShot;
import io.github.rkaushikdev.kerfline.geom.UnsharpnessCheck;
import io.github.rkaushikdev.kerfline.geom.ViewingPlan;
import io.github.rkaushikdev.kerfline.iqi.IqiSelection;
import io.github.rkaushikdev.kerfline.joint.Joint;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * A worked technique sheet: the viewing plan for the joint and, for each calculation that resolved,
 * its result. A part that did not resolve is empty here and named in {@code problems}, so the sheet
 * can say what is missing in the place the number would have gone. {@code chartThicknessIn} is the
 * thickness of steel the exposure chart was read at; the indicator is chosen on the section itself.
 * {@code isoDistance} is the setup held against the ISO testing class, empty when none was asked for.
 */
public record TechniqueSheet(
        Optional<String> job,
        Joint joint,
        String materialId,
        OptionalDouble chartThicknessIn,
        ViewingPlan viewing,
        Optional<ExposurePlan> exposure,
        Optional<IqiSelection> indicator,
        Optional<UnsharpnessCheck> geometry,
        Optional<IsoDistanceCheck> isoDistance,
        Optional<List<PlannedShot>> coverage,
        OptionalDouble barricadeFt,
        List<String> problems) {

    public TechniqueSheet {
        coverage = coverage.map(List::copyOf);
        problems = List.copyOf(problems);
    }

    /** True when every part of the sheet resolved, which is when a crew can work from it. */
    public boolean usable() {
        return problems.isEmpty();
    }
}
