package stroom.widget.popup.client.presenter;

import java.util.Objects;

public class Position {

    private final double left;
    private final double top;

    public Position(final double left, final double top) {
        this.left = left;
        this.top = top;
    }

    public double getLeft() {
        return left;
    }

    public double getTop() {
        return top;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Position position = (Position) o;
        return Double.compare(left, position.left) == 0 && Double.compare(top, position.top) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, top);
    }

    @Override
    public String toString() {
        return left + ":" + top;
    }
}
