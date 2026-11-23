package io.github.rkaushikdev.kerfline.repair;

/**
 * A joint as the length based repair rate sees it: the welder stencil, null when nobody recorded
 * one, the inches of weld radiographed, and the inches of it that had to be ground out and rewelded.
 */
public record WeldLengthOutcome(String welder, double radiographedIn, double repairedIn) {}
