package io.github.rkaushikdev.kerfline.job;

import io.github.rkaushikdev.kerfline.geom.IsoClass;
import java.util.Objects;
import java.util.Optional;

/**
 * A job as the contract fixed it: its number and client, the site when there is one, the
 * acceptance code and fluid service it is judged under, the radiography level and the fraction of
 * joints that means, the day work started, the crown, density and sensitivity the client agreed
 * to, whether the client authorises crack repairs, and the ISO testing class when one was named. Every judgement on the job is made against this record rather than against whatever
 * a caller happened to pass.
 */
public record JobContract(
        String number,
        String client,
        Optional<String> site,
        String code,
        String service,
        String coverage,
        String startedOn,
        double reinforcementMaxIn,
        double densityMinimum,
        double calledForSensitivity,
        double coverageFraction,
        boolean crackRepairs,
        Optional<IsoClass> isoClass) {

    public JobContract {
        Objects.requireNonNull(site, "site");
        Objects.requireNonNull(isoClass, "isoClass");
    }
}
