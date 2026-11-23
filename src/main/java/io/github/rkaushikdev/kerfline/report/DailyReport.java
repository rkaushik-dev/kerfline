package io.github.rkaushikdev.kerfline.report;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One crew's day: the journal entries dated that day counted by kind, the joints shot in register
 * order, the films exposed, the readings, and the survey check: the meters found out of
 * calibration when the calibrations were checked, and {@code complete}, true when no source went
 * out or all three surveys are on file for the day off meters that were in calibration.
 */
public record DailyReport(
        Optional<String> job,
        String day,
        List<String> crew,
        Optional<String> source,
        int entries,
        Map<String, Integer> kinds,
        List<String> jointsShot,
        double films,
        List<DailyVerdict> verdicts,
        List<String> surveysDone,
        List<String> surveysMissing,
        List<String> metersOutOfCalibration,
        boolean complete) {

    public DailyReport {
        crew = List.copyOf(crew);
        kinds = Collections.unmodifiableMap(new LinkedHashMap<>(kinds));
        jointsShot = List.copyOf(jointsShot);
        verdicts = List.copyOf(verdicts);
        surveysDone = List.copyOf(surveysDone);
        surveysMissing = List.copyOf(surveysMissing);
        metersOutOfCalibration = List.copyOf(metersOutOfCalibration);
    }
}
