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

package stroom.gwt.workbench.framework.client.manager.addons;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/// The actions logged by the Actions addon. Repeats of the same action are kept once with a
/// count and, as in Storybook, only the most recent actions are kept.
public class ActionLog {

    /// The most actions kept, the same as Storybook's default limit.
    public static final int DEFAULT_LIMIT = 50;

    private final int limit;
    private final List<Entry> entries = new ArrayList<>();

    /// Creates a log that keeps [#DEFAULT_LIMIT] actions.
    public ActionLog() {
        this(DEFAULT_LIMIT);
    }

    /// @param limit The most actions to keep, at least one.
    /// @throws IllegalArgumentException If the limit is less than one.
    public ActionLog(final int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("Limit must be at least one: " + limit);
        }
        this.limit = limit;
    }

    /// Logs an action.
    ///
    /// @param name   The name of the action, e.g. `onClick`. Null is logged as an empty name.
    /// @param detail Detail about the action, may be null.
    /// @return What changed in the log.
    public Change add(final String name, final String detail) {
        final String safeName = name != null
                ? name
                : "";
        final Entry last = entries.isEmpty()
                ? null
                : entries.get(entries.size() - 1);
        if (last != null && last.name.equals(safeName) && Objects.equals(last.detail, detail)) {
            last.count++;
            return Change.REPEATED;
        }
        entries.add(new Entry(safeName, detail));
        if (entries.size() > limit) {
            entries.remove(0);
            return Change.ADDED_AND_DROPPED_OLDEST;
        }
        return Change.ADDED;
    }

    /// Removes all the actions.
    public void clear() {
        entries.clear();
    }

    /// @return The actions, oldest first.
    public List<Entry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    /// @return The number of actions logged, counting repeats, among those kept.
    public int getTotal() {
        int total = 0;
        for (final Entry entry : entries) {
            total += entry.count;
        }
        return total;
    }


    // --------------------------------------------------------------------------------


    /// What [#add(String, String)] changed.
    public enum Change {
        /// The action repeated the last one, whose count went up.
        REPEATED,
        /// The action was added at the end.
        ADDED,
        /// The action was added at the end and the oldest one removed to stay within the limit.
        ADDED_AND_DROPPED_OLDEST
    }


    // --------------------------------------------------------------------------------


    /// A logged action.
    public static final class Entry {

        private final String name;
        private final String detail;
        private int count = 1;

        private Entry(final String name, final String detail) {
            this.name = name;
            this.detail = detail;
        }

        /// @return The name of the action, never null.
        public String getName() {
            return name;
        }

        /// @return Detail about the action, or null.
        public String getDetail() {
            return detail;
        }

        /// @return The number of times the action happened in a row.
        public int getCount() {
            return count;
        }
    }
}
