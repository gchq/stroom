package stroom.data.grid.client;

public class ColSettings {

    private final boolean resizable;
    private final boolean movable;

    public ColSettings(final boolean resizable, final boolean movable) {
        this.resizable = resizable;
        this.movable = movable;
    }

    public boolean isResizable() {
        return resizable;
    }

    public boolean isMovable() {
        return movable;
    }
}
