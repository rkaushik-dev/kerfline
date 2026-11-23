package io.github.rkaushikdev.kerfline.accept;

/**
 * API 1104 judges on the wall and the length of weld all the way round the joint, and on whether
 * the company has authorised short cracks to be repaired rather than cut out.
 */
public record Api1104Context(double wallIn, double weldLengthIn, boolean crackRepairs) implements CodeContext {}
