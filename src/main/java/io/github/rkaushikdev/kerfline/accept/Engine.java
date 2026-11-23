package io.github.rkaushikdev.kerfline.accept;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.errors.KerflineException;
import io.github.rkaushikdev.kerfline.errors.UnsupportedException;
import io.github.rkaushikdev.kerfline.indication.Indication;
import io.github.rkaushikdev.kerfline.joint.Joint;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Dispatching a joint to the code that governs it.
 *
 * <p>The codes do not measure the same joint the same way, and this is the class that has to know
 * it. API 1104 wants the wall and the length of weld all the way round, because it caps defects as
 * a percentage of that length. Section VIII wants the weld thickness with the reinforcement taken
 * off, because its length limit is banded on it. B31.3 wants the thinner of the two components
 * joined and the fluid service the line will run in. AWS D1.1 wants the weld thickness and the
 * loading.
 *
 * <p>Handing any of those the wrong number produces a verdict that looks perfectly well formed and
 * is about a different weld, so the extraction from a joint record happens here, once, rather than
 * at every call site.
 */
public final class Engine {

    private static final String API1104 = "API1104";
    private static final String ASME8 = "ASME8";
    private static final String B313 = "B31.3";
    private static final String AWSD11 = "AWSD1.1";

    private static final Map<String, CodeDescription> CODES = registry();

    private Engine() {}

    private static Map<String, CodeDescription> registry() {
        Map<String, CodeDescription> codes = new LinkedHashMap<>();
        codes.put(
                API1104,
                new CodeDescription(API1104, "API 1104, pipeline girth welds", List.of("wallIn", "weldLengthIn")));
        codes.put(ASME8, new CodeDescription(ASME8, "ASME Section VIII Division 1, UW-51", List.of("thicknessIn")));
        codes.put(B313, new CodeDescription(B313, "ASME B31.3, process piping", List.of("thinnerWallIn", "service")));
        codes.put(AWSD11, new CodeDescription(AWSD11, "AWS D1.1, structural welds", List.of("thicknessIn", "loading")));
        return Collections.unmodifiableMap(codes);
    }

    /** The codes the engine carries, in the order judgeAll runs them. */
    public static List<String> codes() {
        return List.copyOf(CODES.keySet());
    }

    /**
     * What a code is and what it needs from a joint.
     *
     * @throws UnsupportedException for a code the engine does not carry
     */
    public static CodeDescription describeCode(String name) {
        CodeDescription found = CODES.get(name);
        if (found == null) {
            throw new UnsupportedException("no acceptance code " + name, Details.of("asked", name, "known", codes()));
        }
        return found;
    }

    public static CodeContext contextFor(String name, Joint joint) {
        return contextFor(name, joint, JudgeOptions.NONE);
    }

    /**
     * Work out what a code needs from a joint. The joint carries one wall thickness and each code
     * turns it into its own measure, which is the whole reason this method exists.
     */
    public static CodeContext contextFor(String name, Joint joint, JudgeOptions options) {
        describeCode(name);
        if (joint == null) {
            throw new BadInputException("a joint record is needed to judge against a code", Details.of("got", null));
        }
        double wallIn = joint.wallIn();
        if (!(wallIn > 0)) {
            throw new BadInputException("the joint carries no usable wall thickness", Details.of("wallIn", wallIn));
        }

        return switch (name) {
            case API1104 -> {
                double weldLengthIn =
                        options.weldLengthIn() != null ? options.weldLengthIn() : Num.round(Math.PI * joint.odIn(), 4);
                if (!(weldLengthIn > 0)) {
                    throw new BadInputException(
                            "API 1104 needs the length of weld round the joint",
                            Details.of("weldLengthIn", weldLengthIn));
                }
                yield new Api1104Context(wallIn, weldLengthIn, Boolean.TRUE.equals(options.crackRepairs()));
            }
            // Structural work is judged on the weld itself and on whether the member flexes in
            // service, which is not something the joint record knows.
            case AWSD11 ->
                new AwsD11Context(Num.round(wallIn, 4), options.loading() != null ? options.loading() : "static");
            // The band is read on the weld thickness with the crown taken off.
            case ASME8 ->
                new Asme8Context(
                        Num.round(wallIn, 4),
                        options.reinforcementIn() != null ? options.reinforcementIn() : 0,
                        options.weldLengthIn() != null
                                ? OptionalDouble.of(options.weldLengthIn())
                                : OptionalDouble.empty());
            default -> {
                Double mating = options.matingWallIn();
                boolean hasMating = mating != null && mating != 0 && !Double.isNaN(mating);
                double thinnerWallIn = hasMating ? Math.min(wallIn, mating) : wallIn;
                yield new AsmeB313Context(
                        Num.round(thinnerWallIn, 4), options.service() != null ? options.service() : "normal");
            }
        };
    }

    public static CodeJudgement judge(String code, List<Indication> indications, Joint joint) {
        return judge(code, indications, joint, JudgeOptions.NONE);
    }

    /** Judge a joint against one code. */
    public static CodeJudgement judge(String code, List<Indication> indications, Joint joint, JudgeOptions options) {
        Guard.requireArray(indications, "indications");
        if (!CODES.containsKey(code)) {
            throw new UnsupportedException("no acceptance code " + code, Details.of("asked", code, "known", codes()));
        }
        CodeContext context = contextFor(code, joint, options);
        JointVerdict result = judgeWith(indications, context);
        return new CodeJudgement(
                result.code().orElse(code),
                result.disposition(),
                result.clause().orElseThrow(),
                result.governing().orElseThrow(),
                result.findings(),
                result.reasons(),
                context,
                Optional.ofNullable(joint.id()));
    }

    private static JointVerdict judgeWith(List<Indication> indications, CodeContext context) {
        return switch (context) {
            case Api1104Context api ->
                Api1104.judgeJoint(indications, api.wallIn(), api.weldLengthIn(), api.crackRepairs());
            case Asme8Context vessel -> Asme8.judgeJoint(indications, vessel.thicknessIn(), vessel.weldLengthIn());
            case AsmeB313Context plant -> AsmeB313.judgeJoint(indications, plant.thinnerWallIn(), plant.service());
            case AwsD11Context structural ->
                AwsD11.judgeJoint(indications, structural.thicknessIn(), structural.loading());
        };
    }

    public static List<CodeRun> judgeAll(List<Indication> indications, Joint joint) {
        return judgeAll(indications, joint, JudgeOptions.NONE);
    }

    /**
     * Judge against every code that could govern, which is what a client asks for when a line is
     * being requalified and nobody is sure yet which code applies. The answers differ and that is
     * the point of running it. A code that refuses the joint is reported as a {@link CodeFailure}
     * rather than throwing the other answers away.
     */
    public static List<CodeRun> judgeAll(List<Indication> indications, Joint joint, JudgeOptions options) {
        List<CodeRun> runs = new ArrayList<>();
        for (String code : codes()) {
            try {
                runs.add(judge(code, indications, joint, options));
            } catch (KerflineException refused) {
                runs.add(new CodeFailure(code, refused.code(), refused.getMessage()));
            }
        }
        return List.copyOf(runs);
    }

    /**
     * The code the shop applies by default for a service. Pipeline work is judged on API 1104 and
     * everything in a plant on B31.3 unless a client names Section VIII, which they do for vessel
     * nozzle welds, or the work is structural.
     */
    public static String defaultCodeFor(String service) {
        if ("pipeline".equals(service)) {
            return API1104;
        }
        if ("vessel".equals(service)) {
            return ASME8;
        }
        if ("structural".equals(service)) {
            return AWSD11;
        }
        return B313;
    }
}
