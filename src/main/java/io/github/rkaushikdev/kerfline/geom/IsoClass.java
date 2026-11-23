package io.github.rkaushikdev.kerfline.geom;

/**
 * The two ISO 17636-1 testing classes. Class A is the basic technique and class B the improved one
 * a client asks for on critical work; the difference that reaches the geometry is the factor on the
 * minimum source to object distance, which class B doubles.
 */
public enum IsoClass {
    A(7.5),
    B(15);

    private final double factor;

    IsoClass(double factor) {
        this.factor = factor;
    }

    /** The factor the standard multiplies the source size and the two thirds power of b by. */
    public double factor() {
        return factor;
    }

    /** The class as a technique sheet writes it, {@code class A} or {@code class B}. */
    public String label() {
        return "class " + name();
    }
}
