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

package stroom.gwt.workbench.framework.client.manager;

import stroom.gwt.workbench.framework.client.tree.TagFilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/// Helpers for asking the workbench server which stories' classes have changed in git. Plain
/// Java so they can be unit tested.
public final class ChangeRequests {

    /// The most classes asked about in one request, which keeps the request's URL short.
    public static final int MAX_CLASSES_PER_REQUEST = 50;

    private ChangeRequests() {
        // Static utility
    }

    /// Splits items into batches.
    ///
    /// @param items     The items, in the order to keep.
    /// @param batchSize The most items in a batch, at least one.
    /// @return The batches, none of them empty.
    /// @throws IllegalArgumentException If the batch size is less than one.
    public static List<List<String>> batch(final Collection<String> items, final int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("Batch size must be at least one: " + batchSize);
        }
        final List<List<String>> batches = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (final String item : items) {
            if (current.size() == batchSize) {
                batches.add(current);
                current = new ArrayList<>();
            }
            current.add(item);
        }
        if (!current.isEmpty()) {
            batches.add(current);
        }
        return batches;
    }

    /// Records a class's change status, keeping the most significant status if the class
    /// already has one: new, then modified, then related.
    ///
    /// @param statusByClass The statuses so far, by class name.
    /// @param className     The class.
    /// @param status        One of [TagFilter#NEW], [TagFilter#MODIFIED] or [TagFilter#RELATED].
    ///                      Other values are ignored.
    public static void putStatus(final Map<String, String> statusByClass,
                                 final String className,
                                 final String status) {
        if (className == null || rank(status) == 0) {
            return;
        }
        final String existing = statusByClass.get(className);
        if (existing == null || rank(status) > rank(existing)) {
            statusByClass.put(className, status);
        }
    }

    private static int rank(final String status) {
        if (TagFilter.NEW.equals(status)) {
            return 3;
        }
        if (TagFilter.MODIFIED.equals(status)) {
            return 2;
        }
        if (TagFilter.RELATED.equals(status)) {
            return 1;
        }
        return 0;
    }
}
