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

import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.Header;
import com.google.gwt.user.cellview.client.SafeHtmlHeader;

public class ColSpec<R> {

    private final Column<R, ?> column;
    private final Header<?> header;
    private final ColSettings colSettings;
    private final int width;

    public ColSpec(final Column<R, ?> column,
                   final Header<?> header,
                   final ColSettings colSettings,
                   final int width) {
        this.column = column;
        this.header = header;
        this.colSettings = colSettings;
        this.width = width;
    }

    public Column<R, ?> getColumn() {
        return column;
    }

    public Header<?> getHeader() {
        return header;
    }

    public ColSettings getColSettings() {
        return colSettings;
    }

    public int getWidth() {
        return width;
    }

    public Builder<R> copy() {
        return new Builder<>(this);
    }

    public static <R> Builder<R> builder() {
        return new Builder<>();
    }

    public static class Builder<R> {

        private Column<R, ?> column;
        private Header<?> header;
        private ColSettings colSettings = new ColSettings(false, false);
        private int width;

        public Builder() {

        }

        public Builder(final ColSpec<R> colSpec) {
            this.column = colSpec.column;
            this.header = colSpec.header;
            this.colSettings = colSpec.colSettings;
            this.width = colSpec.width;
        }

        public Builder<R> column(final Column<R, ?> column) {
            this.column = column;
            return this;
        }

        public Builder<R> header(final Header<?> header) {
            this.header = header;
            return this;
        }

        public Builder<R> name(final String name) {
            header = new SafeHtmlHeader(SafeHtmlUtils.fromSafeConstant(name));
            return this;
        }

        public Builder<R> colSettings(final ColSettings colSettings) {
            this.colSettings = colSettings;
            return this;
        }

        public Builder<R> width(final int width) {
            this.width = width;
            return this;
        }

        public Builder<R> resizable(final boolean resizable) {
            colSettings = colSettings.copy().resizable(resizable).build();
            return this;
        }

        public Builder<R> movable(final boolean movable) {
            colSettings = colSettings.copy().movable(movable).build();
            return this;
        }

        public ColSpec<R> build() {
            return new ColSpec<>(column, header, colSettings, width);
        }
    }
}
