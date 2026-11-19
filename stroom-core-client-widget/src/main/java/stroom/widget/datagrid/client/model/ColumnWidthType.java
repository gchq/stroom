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

package stroom.widget.datagrid.client.model;

/// How a column's starting pixel width is worked out.
///
/// The starting width is resolved once, on the grid's first layout with a non-zero width. After
/// that every column is a plain pixel width owned by the user. See [ColumnWidthResolver].
public enum ColumnWidthType {

    /// The declared width in pixels is used as it is.
    FIXED,

    /// A percentage of the grid's initial visible width, converted to pixels once.
    PERCENT,

    /// The declared width is a floor. The column grows to take a weighted share of any space left
    /// over once the fixed and percent columns have been sized. It never shrinks.
    FILL
}
