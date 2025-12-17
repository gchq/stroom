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

import java.util.Objects;

public class Size {

    private final double width;
    private final double height;

    public Size(final double width,
                final double height) {
        this.width = width;
        this.height = height;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }
//
//    public void set(final double dimension, final double size) {
//        if (dimension == 0) {
//            width = size;
//        } else {
//            height = size;
//        }
//    }

    public double get(final double dimension) {
        if (dimension == 0) {
            return width;
        }
        return height;
    }

    public Builder copy() {
        return new Builder(this);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Size size = (Size) o;
        return Double.compare(size.width, width) == 0 && Double.compare(size.height, height) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(width, height);
    }

    @Override
    public String toString() {
        return "[" + width + ", " + height + "]";
    }

    public static class Builder {

        private double width;
        private double height;

        public Builder() {
        }

        public Builder(final Size size) {
            this.width = size.width;
            this.height = size.height;
        }

        public Builder width(final double width) {
            this.width = width;
            return this;
        }

        public Builder height(final double height) {
            this.height = height;
            return this;
        }

        public Builder dimension(final double dimension, final double size) {
            if (dimension == 0) {
                width = size;
            } else {
                height = size;
            }
            return this;
        }

        public Size build() {
            return new Size(width, height);
        }
    }
}
