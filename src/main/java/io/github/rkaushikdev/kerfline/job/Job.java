package io.github.rkaushikdev.kerfline.job;

import io.github.rkaushikdev.kerfline.accept.AsmeB313;
import io.github.rkaushikdev.kerfline.accept.CodeJudgement;
import io.github.rkaushikdev.kerfline.accept.Engine;
import io.github.rkaushikdev.kerfline.accept.JudgeOptions;
import io.github.rkaushikdev.kerfline.density.Limits;
import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.indication.Indication;
import io.github.rkaushikdev.kerfline.joint.Joint;
import io.github.rkaushikdev.kerfline.report.Deviation;
import io.github.rkaushikdev.kerfline.sampling.Coverage;
import io.github.rkaushikdev.kerfline.util.Dates;
import io.github.rkaushikdev.kerfline.util.Guard;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The contract, as a record everything else is judged against.
 *
 * <p>A job is not only a list of joints. It is a set of decisions somebody made before any of them
 * were welded: which code governs, which fluid service the line runs in, how much of it gets
 * radiographed, how much crown is allowed, what density the client will accept. Every one of
 * those changes an answer somewhere downstream.
 *
 * <p>Holding them here rather than passing them around loose is what makes it possible to ask the
 * useful question, which is not "does this weld pass" but "does this weld pass under what we
 * agreed to". Those come apart more often than anybody would like, and when they do the deviation
 * is the finding.
 */
public final class Job {

    /** The one code whose answer depends on the fluid service. */
    private static final String SERVICE_CODE = "B31.3";

    private Job() {}

    /**
     * Check a job's decisions and fix them as its contract.
     *
     * @throws BadInputException when the number, client or start date is missing or bad, or when
     *     the code, coverage level or (under B31.3) fluid service is not one this package knows
     */
    public static JobContract makeJob(JobInput input) {
        if (input == null) {
            throw new BadInputException("a job is described by an object", Details.of("got", null));
        }
        String number = Guard.requireLabel(input.number(), "a job is identified by its number");
        String client = Guard.requireLabel(input.client(), "a job names the client it is for");
        Guard.requireOneOf(input.code(), Engine.codes(), "acceptance code");
        Guard.requireOneOf(input.coverage(), Coverage.levels(), "radiography level");
        if (SERVICE_CODE.equals(input.code())) {
            Guard.requireOneOf(input.service(), AsmeB313.services(), "fluid service");
        }
        Dates.requireDay(input.startedOn(), "start date");
        Guard.requireAtLeast(input.reinforcementMaxIn(), 0, "maximum reinforcement");
        Guard.requireAtLeast(input.densityMinimum(), 0, "minimum density");

        return new JobContract(
                number,
                client,
                Optional.ofNullable(input.site()),
                input.code(),
                input.service(),
                input.coverage(),
                input.startedOn(),
                input.reinforcementMaxIn(),
                input.densityMinimum(),
                input.calledForSensitivity(),
                Coverage.levelFraction(input.coverage()),
                input.crackRepairs(),
                Optional.ofNullable(input.isoClass()));
    }

    /**
     * The lowest density the job will take: what the client agreed, raised to the ISO class floor
     * when the work is held to a testing class, because agreeing to less than the standard the job
     * is written to does not lower the standard.
     */
    public static double densityFloor(JobContract job) {
        return job.isoClass()
                .map(testClass -> Math.max(job.densityMinimum(), Limits.isoMinimumDensity(testClass)))
                .orElse(job.densityMinimum());
    }

    /** The parameters every judgement on this job should be made with. */
    public static ContractTerms contractFor(JobContract job) {
        return new ContractTerms(
                job.code(), job.service(), job.reinforcementMaxIn(), job.densityMinimum(), job.calledForSensitivity());
    }

    public static CodeJudgement judgeUnderContract(JobContract job, Joint joint, List<Indication> indications) {
        return judgeUnderContract(job, joint, indications, JudgeOptions.NONE);
    }

    /**
     * Judge a joint under the contract rather than under whatever was passed: the job's code, its
     * fluid service and its crack repair authority, each unless the extra options name their own.
     */
    public static CodeJudgement judgeUnderContract(
            JobContract job, Joint joint, List<Indication> indications, JudgeOptions extra) {
        Guard.requireArray(indications, "indications");
        JudgeOptions options = extra;
        if (options.service() == null) {
            options = options.service(job.service());
        }
        if (options.crackRepairs() == null) {
            options = options.crackRepairs(job.crackRepairs());
        }
        return Engine.judge(job.code(), indications, joint, options);
    }

    /**
     * Where a shot departed from what the contract called for. Each deviation names the field,
     * what was agreed and what happened, because a deviation report that says only "does not
     * conform" starts an argument rather than ending one. The fluid service is only compared on a
     * job whose code reads it, and density against the floor the ISO class sets when there is one.
     */
    public static DeviationCheck deviations(JobContract job, ActualConditions actual) {
        if (actual == null) {
            throw new BadInputException("a deviation check needs what actually happened", Details.of("got", null));
        }
        List<Deviation> found = new ArrayList<>();

        actual.code()
                .filter(code -> !code.equals(job.code()))
                .ifPresent(code -> found.add(new Deviation("code", job.code(), code)));
        actual.service()
                .filter(service -> SERVICE_CODE.equals(job.code()) && !Objects.equals(service, job.service()))
                .ifPresent(service -> found.add(new Deviation("service", job.service(), service)));
        double densityFloor = densityFloor(job);
        actual.densityMinimum().ifPresent(density -> {
            if (density < densityFloor) {
                found.add(new Deviation("density", densityFloor, density));
            }
        });
        actual.reinforcementIn().ifPresent(crown -> {
            if (crown > job.reinforcementMaxIn()) {
                found.add(new Deviation("reinforcement", job.reinforcementMaxIn(), crown));
            }
        });
        actual.coverageFraction().ifPresent(fraction -> {
            if (fraction < job.coverageFraction()) {
                found.add(new Deviation("coverage", job.coverageFraction(), fraction));
            }
        });

        return new DeviationCheck(job.number(), found, found.isEmpty());
    }

    /** A one line description for the head of every report on this job. */
    public static String describeJob(JobContract job) {
        String where = job.site()
                .filter(site -> !site.isEmpty())
                .map(site -> " at " + site)
                .orElse("");
        return job.number() + " for " + job.client() + where + ", " + job.code() + " at " + job.coverage()
                + " coverage";
    }
}
