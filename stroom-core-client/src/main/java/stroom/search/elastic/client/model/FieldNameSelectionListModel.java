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

package stroom.search.elastic.client.model;

import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.widget.selection.client.SimpleSelectionItemWrapper;
import stroom.widget.selection.client.SimpleSelectionListModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/// A drop-down list of field names that is always filterable and paged, so it stays usable for indexes with many
/// fields. Typing in the filter keeps the names that contain the text, ignoring case, with names that start with
/// it listed first. The 'None' item, if set, is listed first while there is no filter.
public class FieldNameSelectionListModel extends SimpleSelectionListModel<String> {

    @Override
    public void onRangeChange(final SimpleSelectionItemWrapper<String> parent,
                              final String filter,
                              final boolean filterChange,
                              final PageRequest pageRequest,
                              final Consumer<ResultPage<SimpleSelectionItemWrapper<String>>> consumer) {
        final List<SimpleSelectionItemWrapper<String>> matches = findMatches(items, filter);
        consumer.accept(ResultPage.createPageLimitedList(matches, toPageRequest(pageRequest, filterChange,
                matches.size())));
    }

    /// Finds the items whose label contains the filter, ignoring case. Items whose label starts with the filter
    /// come first; otherwise the items keep their order. A blank filter matches every item, including the 'None'
    /// item, and a non-blank one never matches the 'None' item.
    ///
    /// @param items  the items to search
    /// @param filter the filter text, or `null`
    /// @return the matching items
    static List<SimpleSelectionItemWrapper<String>> findMatches(
            final List<SimpleSelectionItemWrapper<String>> items,
            final String filter) {
        final String lowerFilter = NullSafe.trim(filter).toLowerCase(Locale.ROOT);
        if (lowerFilter.isEmpty()) {
            return new ArrayList<>(NullSafe.list(items));
        }
        final List<SimpleSelectionItemWrapper<String>> startsWith = new ArrayList<>();
        final List<SimpleSelectionItemWrapper<String>> contains = new ArrayList<>();
        for (final SimpleSelectionItemWrapper<String> item : NullSafe.list(items)) {
            if (item.getItem() != null) {
                final String lowerLabel = NullSafe.string(item.getLabel()).toLowerCase(Locale.ROOT);
                if (lowerLabel.startsWith(lowerFilter)) {
                    startsWith.add(item);
                } else if (lowerLabel.contains(lowerFilter)) {
                    contains.add(item);
                }
            }
        }
        final List<SimpleSelectionItemWrapper<String>> matches = new ArrayList<>(startsWith);
        matches.addAll(contains);
        return matches;
    }

    // A new filter starts from the first page, and a page past the end (e.g. after the filter narrowed the list)
    // falls back to the first page
    private static PageRequest toPageRequest(final PageRequest pageRequest,
                                             final boolean filterChange,
                                             final int matchCount) {
        if (pageRequest == null) {
            return null;
        }
        final int offset = pageRequest.getOffset() == null
                ? 0
                : pageRequest.getOffset();
        return filterChange || offset >= matchCount
                ? new PageRequest(0, pageRequest.getLength())
                : pageRequest;
    }

    @Override
    public boolean displayFilter() {
        return true;
    }

    @Override
    public boolean displayPager() {
        return true;
    }
}
