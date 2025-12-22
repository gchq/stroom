/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.widget.util.client;

import com.google.gwt.dom.client.Element;

import java.util.Objects;

public class Rect {

    private final double top;
    private final double bottom;
    private final double left;
    private final double right;

    public Rect(final Element element) {
        this.top = element.getAbsoluteTop();
        this.bottom = element.getAbsoluteBottom();
        this.left = element.getAbsoluteLeft();
        this.right = element.getAbsoluteRight();
    }

    public Rect(final double top,
                final double bottom,
                final double left,
                final double right) {
        this.top = top;
        this.bottom = Math.max(top, bottom);
        this.left = left;
        this.right = Math.max(left, right);
    }

    public double getTop() {
        return top;
    }

    public double getBottom() {
        return bottom;
    }

    public double getLeft() {
        return left;
    }

    public double getRight() {
        return right;
    }

    public double getWidth() {
        return right - left;
    }

    public double getHeight() {
        return bottom - top;
    }

    public static Rect min(final Rect one, final Rect two) {
        final double top = Math.max(one.top, two.top);
        final double bottom = Math.max(top, Math.min(one.bottom, two.bottom));
        final double left = Math.max(one.left, two.left);
        final double right = Math.max(left, Math.min(one.right, two.right));
        return new Rect(top, bottom, left, right);
    }

    public static Rect max(final Rect one, final Rect two) {
        final double top = Math.min(one.top, two.top);
        final double bottom = Math.max(one.bottom, two.bottom);
        final double left = Math.min(one.left, two.left);
        final double right = Math.max(one.right, two.right);
        return new Rect(top, bottom, left, right);
    }

    public Rect grow(final double amount) {
        return new Rect(top - amount, bottom + amount, left - amount, right + amount);
    }

    public Rect growX(final double amount) {
        return new Rect(top, bottom, left - amount, right + amount);
    }

    public Rect growY(final double amount) {
        return new Rect(top - amount, bottom + amount, left, right);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Rect rect = (Rect) o;
        return Double.compare(rect.top, top) == 0 && Double.compare(rect.bottom,
                bottom) == 0 && Double.compare(rect.left, left) == 0 && Double.compare(rect.right,
                right) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(top, bottom, left, right);
    }

    @Override
    public String toString() {
        return "[" + top + ", " + left + ", " + bottom + ", " + right + "]";
    }


    // --------------------------------------------------------------------------------


    public static class Builder {

        private double top;
        private double bottom;
        private double left;
        private double right;

        public Builder top(final double top) {
            this.top = top;
            return this;
        }

        public Builder bottom(final double bottom) {
            this.bottom = bottom;
            return this;
        }

        public Builder left(final double left) {
            this.left = left;
            return this;
        }

        public Builder right(final double right) {
            this.right = right;
            return this;
        }

        public Rect build() {
            return new Rect(top, bottom, left, right);
        }
    }
}
