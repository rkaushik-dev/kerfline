package io.github.rkaushikdev.kerfline.report;

import io.github.rkaushikdev.kerfline.indication.Indication;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The interpretation sheet the client keeps: what was looked at, what was found, what rule it was
 * judged against and who signed it. Text is held as it was written, so a blank field still reads
 * as missing when {@link Interpretation#completeness} checks it.
 */
public record InterpretationSheet(
        Optional<String> job,
        Optional<String> jointId,
        Optional<String> day,
        Optional<String> reader,
        Optional<String> readerLevel,
        Optional<String> code,
        Optional<String> technique,
        OptionalDouble wallIn,
        OptionalDouble odIn,
        List<String> films,
        List<Indication> indications,
        Optional<SheetVerdict> verdict,
        Optional<String> remarks) {

    public InterpretationSheet {
        films = List.copyOf(films);
        indications = List.copyOf(indications);
    }
}
