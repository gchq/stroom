package stroom.widget.util.client;

public class Rect {

    private final int top;
    private final int bottom;
    private final int left;
    private final int right;

    public Rect(final int top,
                final int bottom,
                final int left,
                final int right) {
        this.top = top;
        this.bottom = Math.max(top, bottom);
        this.left = left;
        this.right = Math.max(left, right);
    }

    public int getTop() {
        return top;
    }

    public int getBottom() {
        return bottom;
    }

    public int getLeft() {
        return left;
    }

    public int getRight() {
        return right;
    }

    public int getWidth() {
        return right - left;
    }

    public int getHeight() {
        return bottom - top;
    }

    public static Rect min(final Rect one, final Rect two) {
        final int top = Math.max(one.top, two.top);
        final int bottom = Math.max(top, Math.min(one.bottom, two.bottom));
        final int left = Math.max(one.left, two.left);
        final int right = Math.max(left, Math.min(one.right, two.right));
        return new Rect(top, bottom, left, right);
    }

    public static Rect max(final Rect one, final Rect two) {
        final int top = Math.min(one.top, two.top);
        final int bottom = Math.max(one.bottom, two.bottom);
        final int left = Math.min(one.left, two.left);
        final int right = Math.max(one.right, two.right);
        return new Rect(top, bottom, left, right);
    }

    public static class Builder {

        private int top;
        private int bottom;
        private int left;
        private int right;

        public Builder top(final int top) {
            this.top = top;
            return this;
        }

        public Builder bottom(final int bottom) {
            this.bottom = bottom;
            return this;
        }

        public Builder left(final int left) {
            this.left = left;
            return this;
        }

        public Builder right(final int right) {
            this.right = right;
            return this;
        }

        public Rect build() {
            return new Rect(top, bottom, left, right);
        }
    }
}
