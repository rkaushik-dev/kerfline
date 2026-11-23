package io.github.rkaushikdev.kerfline.accept;

import java.util.OptionalDouble;

/**
 * One fluid service column of B31.3 Table 341.3.2: the fixed undercut cap and the fraction of Tw it
 * yields to on thin work, the cumulative length of incomplete penetration allowed in any six inches
 * of weld (absent where the column does not permit it at all), and the pore cap as a fraction of
 * Tw.
 */
public record AsmeB313Rules(
        double undercutIn, double undercutFraction, OptionalDouble penetrationIn, double poreFraction) {

    /** True for a column that tolerates some incomplete penetration rather than none. */
    public boolean penetrationPermitted() {
        return penetrationIn.isPresent();
    }
}
