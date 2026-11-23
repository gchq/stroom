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

package stroom.query.language.functions;

import stroom.query.language.functions.ref.StoredValues;

/// The generator of `windowKey()`, which a result store groups the rows of a query with a `window`
/// clause by. As well as the key of a row's window it says whether any row that is really in that
/// window was grouped into the row, or only copies of rows from other windows made for functions
/// such as `lag`.
public interface WindowRowGenerator extends Generator {

    /// Gets the key of the window that a stored row is in.
    ///
    /// @param storedValues The values of the stored row.
    /// @return The window key, or `null` if the row was never in a window.
    Long getWindowKey(StoredValues storedValues);

    /// Gets whether a stored row holds any row that is really in its window.
    ///
    /// @param storedValues The values of the stored row.
    /// @return False if the row was made only from copies of rows that are in other windows.
    boolean hasRealRows(StoredValues storedValues);
}
