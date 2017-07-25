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

package stroom.util.task;

import stroom.util.shared.Monitor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class MonitorInfoUtil {
    public static String getInfo(final Collection<Monitor> monitors) {
        if (monitors == null || monitors.size() == 0) {
            return "";
        }

        final Set<Monitor> allMonitors = new HashSet<Monitor>();

        // Build a tree map.
        final Map<Monitor, List<Monitor>> map = new HashMap<Monitor, List<Monitor>>();
        for (final Monitor monitor : monitors) {
            List<Monitor> children = map.get(monitor.getParent());
            if (children == null) {
                children = new ArrayList<Monitor>();
                map.put(monitor.getParent(), children);
            }
            children.add(monitor);
            allMonitors.add(monitor);
        }

        final StringBuilder sb = new StringBuilder();

        // Get a list of monitors that have no parent monitor or who have a
        // parent monitor that no longer seems to exist.
        final List<Monitor> roots = new ArrayList<Monitor>();
        for (final Entry<Monitor, List<Monitor>> entry : map.entrySet()) {
            final Monitor parent = entry.getKey();
            final List<Monitor> children = entry.getValue();
            if (parent == null) {
                roots.addAll(children);
            } else if (!allMonitors.contains(parent)) {
                roots.addAll(children);
            }
        }

        // Build the tree with the root monitors.
        addLevel(sb, map, roots, "");

        return sb.toString();
    }

    private static void addLevel(final StringBuilder sb, final Map<Monitor, List<Monitor>> map,
                                 final List<Monitor> list, final String prefix) {
        if (list != null && list.size() > 0) {
            for (final Monitor monitor : list) {
                // Indent the message if needed.
                sb.append(prefix);
                // Add the progress message.
                sb.append("---o ");
                sb.append(monitor.getInfo());
                sb.append("\n");

                final List<Monitor> children = map.get(monitor);
                addLevel(sb, map, children, "   +" + prefix);
            }
        }
    }
}
