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
                       final double fillWeight,
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

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private boolean resizable;
        private boolean movable;
        private boolean fill;
        private double fillWeight = 0;
        private Integer minWidth = 0;

        public Builder() {

        }

        public Builder(final ColSettings colSettings) {
            this.resizable = colSettings.resizable;
            this.movable = colSettings.movable;
            this.fill = colSettings.fill;
            this.fillWeight = colSettings.fillWeight;
            this.minWidth = colSettings.minWidth;
        }

        public Builder resizable(final boolean resizable) {
            this.resizable = resizable;
            return this;
        }

        public Builder movable(final boolean movable) {
            this.movable = movable;
            return this;
        }

        public Builder fill(final boolean fill) {
            this.fill = fill;
            return this;
        }

        public Builder fillWeight(final double fillWeight) {
            this.fillWeight = fillWeight;
            return this;
        }

        public Builder minWidth(final Integer minWidth) {
            this.minWidth = minWidth;
            return this;
        }

        public ColSettings build() {
            return new ColSettings(resizable, movable, fill, fillWeight, minWidth);
        }
    }
}
