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

package stroom.widget.tab.client.presenter;

import stroom.svg.shared.SvgImage;

import java.util.Optional;

public class TabDataImpl implements TabData {

    private final String text;
    private final String tooltip;
    private final boolean closeable;
    private final String type;

    public TabDataImpl(final String text) {
        this(text, false, null);
    }

    public TabDataImpl(final String text, final boolean closeable) {
        this(text, closeable, null);
    }

    public TabDataImpl(final String text, final String type) {
        this(text, false, type);
    }

    public TabDataImpl(final String text, final boolean closeable, final String type) {
        this(text, closeable, type, null);
    }

    public TabDataImpl(final String text, final boolean closeable, final String type, final String tooltip) {
        this.text = text;
        this.closeable = closeable;
        this.type = type;
        this.tooltip = tooltip;
    }

    private TabDataImpl(final Builder builder) {
        text = builder.text;
        tooltip = builder.tooltip;
        closeable = builder.closeable;
        type = builder.type;
    }

    @Override
    public SvgImage getIcon() {
        return null;
    }

    @Override
    public String getLabel() {
        return text;
    }

    @Override
    public boolean isCloseable() {
        return closeable;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public Optional<String> getTooltip() {
        return Optional.ofNullable(tooltip);
    }

    @Override
    public String toString() {
        return "TabDataImpl{" +
                "text='" + text + '\'' +
                ", closeable=" + closeable +
                ", type='" + type + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(final TabDataImpl copy) {
        final Builder builder = new Builder();
        builder.text = copy.getLabel();
        builder.tooltip = copy.getTooltip().orElse(null);
        builder.closeable = copy.isCloseable();
        builder.type = copy.getType();
        return builder;
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private String text;
        private String tooltip;
        private boolean closeable;
        private String type;

        private Builder() {
        }

        public Builder withLabel(final String text) {
            this.text = text;
            return this;
        }

        public Builder withTooltip(final String tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        public Builder withCloseable(final boolean closeable) {
            this.closeable = closeable;
            return this;
        }

        public Builder withType(final String type) {
            this.type = type;
            return this;
        }

        public TabDataImpl build() {
            return new TabDataImpl(this);
        }
    }
}
