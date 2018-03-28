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

import stroom.task.Monitor;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MonitorInfoUtil {
    public static String getInfo(final Collection<Monitor> monitors) {
        if (monitors == null || monitors.size() == 0) {
            return "";
        }

        final Set<Monitor> allMonitors = new HashSet<>();

        // Build a tree map.
        final Map<Monitor, Set<Monitor>> childMap = new HashMap<>();
        for (final Monitor monitor : monitors) {
            childMap.put(monitor, monitor.getChildren());
            allMonitors.add(monitor);
        }

        final StringBuilder sb = new StringBuilder();

        // Get a list of monitors that have no parent monitor or who have a
        // parent monitor that no longer seems to exist.
        final Set<Monitor> roots = new HashSet<>(allMonitors);
        for (final Monitor monitor : monitors) {
            roots.removeAll(monitor.getChildren());
        }

        // Build the tree with the root monitors.
        addLevel(sb, childMap, roots, "");

        return sb.toString();
    }

    private static void addLevel(final StringBuilder sb, final Map<Monitor, Set<Monitor>> map,
                                 final Set<Monitor> list, final String prefix) {
        if (list != null && list.size() > 0) {
            for (final Monitor monitor : list) {
                // Indent the message if needed.
                sb.append(prefix);
                // Add the progress message.
                sb.append("---o ");
                sb.append(monitor.getInfo());
                sb.append("\n");

                final Set<Monitor> children = map.get(monitor);
                addLevel(sb, map, children, "   +" + prefix);
            }
        }
    }
}
