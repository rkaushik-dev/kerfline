package io.github.rkaushikdev.kerfline.repair;

/**
 * Inches repaired over inches radiographed for one welder, or for everybody when the welder is
 * {@code all}, with both lengths the rate came from.
 */
public record LengthRate(String welder, double radiographedIn, double repairedIn, double rate) {}
