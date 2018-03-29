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

package stroom.task;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TaskThreadInfoUtil {
    public static String getInfo(final Collection<TaskThread> taskThreads) {
        if (taskThreads == null || taskThreads.size() == 0) {
            return "";
        }

        final Set<TaskThread> allTaskThreads = new HashSet<>();

        // Build a tree map.
        final Map<TaskThread, Set<TaskThread>> childMap = new HashMap<>();
        for (final TaskThread taskThread : taskThreads) {
            childMap.put(taskThread, taskThread.getChildren());
            allTaskThreads.add(taskThread);
        }

        final StringBuilder sb = new StringBuilder();

        // Get a list of taskThreads that have no parent taskThread or who have a
        // parent taskThread that no longer seems to exist.
        final Set<TaskThread> roots = new HashSet<>(allTaskThreads);
        for (final TaskThread taskThread : taskThreads) {
            roots.removeAll(taskThread.getChildren());
        }

        // Build the tree with the root taskThreads.
        addLevel(sb, childMap, roots, "");

        return sb.toString();
    }

    private static void addLevel(final StringBuilder sb, final Map<TaskThread, Set<TaskThread>> map,
                                 final Set<TaskThread> list, final String prefix) {
        if (list != null && list.size() > 0) {
            for (final TaskThread taskThread : list) {
                // Indent the message if needed.
                sb.append(prefix);
                // Add the progress message.
                sb.append("---o ");
                sb.append(taskThread.getInfo());
                sb.append("\n");

                final Set<TaskThread> children = map.get(taskThread);
                addLevel(sb, map, children, "   +" + prefix);
            }
        }
    }
}
