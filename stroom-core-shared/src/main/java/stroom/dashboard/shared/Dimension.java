package stroom.dashboard.shared;

public final class Dimension {

    public static final int X = 0;
    public static final int Y = 1;

    private Dimension() {
    }

    public static int opposite(final int dim) {
        if (dim == X) {
            return Y;
        } else {
            return X;
        }
    }
}
