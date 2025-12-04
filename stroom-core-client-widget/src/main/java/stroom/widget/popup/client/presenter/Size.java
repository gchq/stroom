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

package stroom.widget.popup.client.presenter;

public class Size {

    private Integer initial;
    private Integer min;
    private Integer max;
    private boolean resizable;

    private Size(final Integer initial,
                 final Integer min,
                 final Integer max,
                 final boolean resizable) {
        this.initial = initial;
        this.min = min;
        this.max = max;
        this.resizable = resizable;
    }

    public Integer getInitial() {
        return initial;
    }

    public void setInitial(final Integer initial) {
        this.initial = initial;
    }

    public Integer getMin() {
        return min;
    }

    public void setMin(final Integer min) {
        this.min = min;
    }

    public Integer getMax() {
        return max;
    }

    public void setMax(final Integer max) {
        this.max = max;
    }

    public boolean isResizable() {
        return resizable;
    }

    public void setResizable(final boolean resizable) {
        this.resizable = resizable;
    }

    public static Builder builder() {
        return new Builder();
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private Integer initial;
        private Integer min;
        private Integer max;
        private boolean resizable;

        public Builder initial(final Integer initial) {
            this.initial = initial;
            return this;
        }

        public Builder min(final Integer min) {
            this.min = min;
            return this;
        }

        public Builder max(final Integer max) {
            this.max = max;
            resizable = true;
            return this;
        }

        public Builder resizable(final boolean resizable) {
            this.resizable = resizable;
            return this;
        }

        public Size build() {
            return new Size(initial, min, max, resizable);
        }
    }
}
