/*
 * Copyright 2016 Crown Copyright
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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TaskThreadInfoUtil {
    public static String getInfo(final Collection<TaskState> taskStates) {
        if (taskStates == null || taskStates.size() == 0) {
            return "";
        }

        final Set<TaskState> allTaskStates = new HashSet<>();

        // Build a tree map.
        final Map<TaskState, Set<TaskState>> childMap = new HashMap<>();
        for (final TaskState taskState : taskStates) {
            childMap.put(taskState, taskState.getChildren());
            allTaskStates.add(taskState);
        }

        final StringBuilder sb = new StringBuilder();

        // Get a list of taskThreads that have no parent taskThread or who have a
        // parent taskThread that no longer seems to exist.
        final Set<TaskState> roots = new HashSet<>(allTaskStates);
        for (final TaskState taskState : taskStates) {
            roots.removeAll(taskState.getChildren());
        }

        // Build the tree with the root taskThreads.
        addLevel(sb, childMap, roots, "");

        return sb.toString();
    }

    private static void addLevel(final StringBuilder sb, final Map<TaskState, Set<TaskState>> map,
                                 final Set<TaskState> list, final String prefix) {
        if (list != null && list.size() > 0) {
            for (final TaskState taskState : list) {
                // Indent the message if needed.
                sb.append(prefix);
                // Add the progress message.
                sb.append("---o ");
                sb.append(taskState.getInfo());
                sb.append("\n");

                final Set<TaskState> children = map.get(taskState);
                addLevel(sb, map, children, "   +" + prefix);
            }
        }
    }
}
