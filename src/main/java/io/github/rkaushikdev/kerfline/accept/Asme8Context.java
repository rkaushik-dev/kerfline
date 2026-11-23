package io.github.rkaushikdev.kerfline.accept;

import java.util.OptionalDouble;

/**
 * Section VIII judges on the weld thickness with the crown taken off; the reinforcement that was
 * left out of it is kept for the record. The length of the seam matters only on thin work, where
 * Appendix 4 counts rounded indications per six inches and allows fewer on a shorter weld; it is
 * empty when nobody said.
 */
public record Asme8Context(double thicknessIn, double reinforcementIgnoredIn, OptionalDouble weldLengthIn)
        implements CodeContext {}
