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

package stroom.widget.form.client;

import java.util.ArrayList;
import java.util.List;

/// Changes an ARIA id reference list, e.g. an `aria-describedby` attribute's value, which is the
/// ids of other elements separated by spaces. Several widgets may each add an id to one element's
/// list (e.g. a form group its help and a view its validation feedback), so each adds and removes
/// only its own id.
public final class AriaIdRefs {

    private AriaIdRefs() {
        // Static utility
    }

    /// @param ids   The list, or null or empty for none.
    /// @param id    The id to add.
    /// @param first Whether to add it first (read first), rather than last.
    /// @return The list with the id, once, where asked; or null if the id is null or blank (the
    /// list unchanged, as null for none).
    public static String add(final String ids, final String id, final boolean first) {
        final List<String> list = parse(remove(ids, id));
        if (id != null && !id.trim().isEmpty()) {
            if (first) {
                list.add(0, id.trim());
            } else {
                list.add(id.trim());
            }
        }
        return format(list);
    }

    /// @param ids The list, or null or empty for none.
    /// @param id  The id to remove.
    /// @return The list without the id, or null if that leaves it empty.
    public static String remove(final String ids, final String id) {
        final List<String> list = parse(ids);
        if (id != null) {
            list.removeIf(existing -> existing.equals(id.trim()));
        }
        return format(list);
    }

    private static List<String> parse(final String ids) {
        final List<String> list = new ArrayList<>();
        if (ids != null) {
            for (final String id : ids.trim().split("\\s+")) {
                if (!id.isEmpty() && !list.contains(id)) {
                    list.add(id);
                }
            }
        }
        return list;
    }

    private static String format(final List<String> list) {
        return list.isEmpty()
                ? null
                : String.join(" ", list);
    }
}
