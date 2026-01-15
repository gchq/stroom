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

package stroom.task.impl;

import stroom.util.shared.NullSafe;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class TaskThreadInfoUtil {

    public static String getInfo(final Collection<TaskContextImpl> taskContexts) {
        if (NullSafe.isEmptyCollection(taskContexts)) {
            return "";
        }

        final Set<TaskContextImpl> allTaskContexts = new HashSet<>();

        // Build a tree map.
        final Map<TaskContextImpl, Set<TaskContextImpl>> childMap = new HashMap<>();
        for (final TaskContextImpl taskContext : taskContexts) {
            childMap.put(taskContext, taskContext.getChildren());
            allTaskContexts.add(taskContext);
        }

        final StringBuilder sb = new StringBuilder();

        // Get a list of taskThreads that have no parent taskThread or who have a
        // parent taskThread that no longer seems to exist.
        final Set<TaskContextImpl> roots = new HashSet<>(allTaskContexts);
        for (final TaskContextImpl taskContext : taskContexts) {
            roots.removeAll(taskContext.getChildren());
        }

        // Build the tree with the root taskThreads.
        addLevel(sb, childMap, roots, "");

        return sb.toString();
    }

    private static void addLevel(final StringBuilder sb, final Map<TaskContextImpl, Set<TaskContextImpl>> map,
                                 final Set<TaskContextImpl> list, final String prefix) {
        if (NullSafe.hasItems(list)) {
            for (final TaskContextImpl taskContext : list) {
                // Indent the message if needed.
                sb.append(prefix);
                // Add the progress message.
                sb.append("---o ");
                sb.append(taskContext.getInfo());
                sb.append("\n");

                final Set<TaskContextImpl> children = map.get(taskContext);
                addLevel(sb, map, children, "   +" + prefix);
            }
        }
    }
}
