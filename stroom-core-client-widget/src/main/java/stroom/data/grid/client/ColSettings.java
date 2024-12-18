package stroom.data.grid.client;

public class ColSettings {

    private final boolean resizable;
    private final boolean movable;
    private final boolean fill;
    private final double fillWeight;
    private final Integer minWidth;

    public ColSettings(final boolean resizable, final boolean movable) {
        this(resizable, movable, false, 0, 0);
    }

    public ColSettings(final boolean resizable,
                       final boolean movable,
                       final boolean fill,
                       final int fillWeight,
                       final Integer minWidth) {
        if (fillWeight < 0) {
            throw new RuntimeException("Invalid fillWeight: " + fillWeight);
        }

        this.resizable = resizable;
        this.movable = movable;
        this.fill = fill;
        this.fillWeight = fillWeight;
        this.minWidth = minWidth;
    }

    public boolean isResizable() {
        return resizable;
    }

    public boolean isMovable() {
        return movable;
    }

    public boolean isFill() {
        return fill;
    }

    public double getFillWeight() {
        return fillWeight;
    }

    public Integer getMinWidth() {
        return minWidth;
    }
}
