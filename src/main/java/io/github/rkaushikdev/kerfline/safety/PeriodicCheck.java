package io.github.rkaushikdev.kerfline.safety;

/**
 * A check that holds for a fixed time once it has passed and has to be made again before that time
 * runs out: the six monthly leak test and the quarterly physical inventory. What they share is the
 * way they lapse. Only one taken on or before the day counts, the latest of those decides, a failed
 * one never comes good by waiting, and one that has run out is the same as never having had one.
 */
public sealed interface PeriodicCheck permits LeakTest, PhysicalInventory {

    /** The day the check was made. */
    String day();

    /** Whether it passed. */
    boolean pass();

    /** The last day it holds for. */
    String dueBy();
}
