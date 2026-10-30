/*
 * Copyright 2023 Crown Copyright
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

package stroom.widget.selection.client;

import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import java.util.function.Consumer;

public interface SelectionListModel<T, I extends SelectionItem> {

    void onRangeChange(I parent,
                       String filter,
                       boolean filterChange,
                       PageRequest pageRequest,
                       Consumer<ResultPage<I>> consumer);

    void reset();

    boolean displayFilter();

    boolean displayPath();

    boolean displayPager();

    /// The name of the top level, shown first in the path. It names a button that goes back to
    /// the top, so it says what is there rather than e.g. "Help", which reads as if it opens help.
    ///
    /// @return The top level's name.
    default String getPathRoot() {
        return "Help Contents";
    }

    I wrap(T item);

    T unwrap(I selectionItem);

    boolean isEmptyItem(I selectionItem);
}
