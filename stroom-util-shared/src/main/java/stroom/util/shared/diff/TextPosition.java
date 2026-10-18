/*
 * Copyright 2026 Crown Copyright
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

package stroom.util.shared.diff;

import java.util.Objects;

/// A zero based row and column in a text, as used by the code editor.
public final class TextPosition {

    private final int row;
    private final int column;

    /// @param row    The zero based row (line).
    /// @param column The zero based column within the row.
    public TextPosition(final int row, final int column) {
        this.row = row;
        this.column = column;
    }

    /// @return The zero based row (line).
    public int getRow() {
        return row;
    }

    /// @return The zero based column within the row.
    public int getColumn() {
        return column;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TextPosition that = (TextPosition) o;
        return row == that.row && column == that.column;
    }

    @Override
    public int hashCode() {
        return Objects.hash(row, column);
    }

    @Override
    public String toString() {
        return row + ":" + column;
    }
}
