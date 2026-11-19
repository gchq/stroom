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

package stroom.widget.component.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/// Development checks of the ownership tree. While they are enabled, misusing ownership (owning a
/// disposed or shared controller, owning one twice, releasing one that isn't a child) throws, and
/// the controllers that nothing owns are counted, so that leaks are found in workbench stories
/// rather than in production.
///
/// They are enabled on the JVM (tests) and in GWT modules that set the binding property
/// `stroom.controllerChecks` to `ENABLED` (see `Component.gwt.xml`); the application leaves it
/// `DISABLED`, which compiles the checks out. With them disabled, misuse is ignored as safely as
/// it can be (see [ControllerNode#adopt(ControllerNode)]).
public final class ControllerChecks {

    // A node not counted as unowned (generations start at 0)
    static final int NOT_COUNTED = -1;

    // How many live controllers of each name nothing owns. Counts, not the nodes, so that counting
    // doesn't keep alive what nothing else refers to
    private static final Map<String, Integer> UNOWNED = new TreeMap<>();

    private static boolean enabled = !"DISABLED".equals(System.getProperty("stroom.controllerChecks"));
    private static int unownedCount;
    // Changed by reset(), so that nodes counted before it aren't taken off the new count
    private static int generation;

    private ControllerChecks() {
        // Static only
    }

    /// @return Whether the checks are enabled.
    public static boolean isEnabled() {
        return enabled;
    }

    /// @return The number of live controllers that nothing owns.
    public static int unownedCount() {
        return unownedCount;
    }

    /// @return The names of the live controllers that nothing owns, in name order, each as many
    /// times as there are such controllers.
    public static List<String> unownedNames() {
        final List<String> names = new ArrayList<>(unownedCount);
        for (final Map.Entry<String, Integer> entry : UNOWNED.entrySet()) {
            for (int i = 0; i < entry.getValue(); i++) {
                names.add(entry.getKey());
            }
        }
        return names;
    }

    /// Turns the checks on or off, for tests of both behaviours.
    ///
    /// @param enabled Whether they are enabled.
    static void setEnabled(final boolean enabled) {
        ControllerChecks.enabled = enabled;
        if (!enabled) {
            reset();
        }
    }

    /// Forgets the unowned controllers counted so far, so that tests start from nothing.
    static void reset() {
        UNOWNED.clear();
        unownedCount = 0;
        generation++;
    }

    /// Reports misuse of ownership.
    ///
    /// @param message What was wrong.
    /// @return False if the checks are disabled, so the caller ignores the misuse.
    /// @throws IllegalStateException If the checks are enabled.
    static boolean fail(final String message) {
        if (enabled) {
            throw new IllegalStateException(message);
        }
        return false;
    }

    static void unowned(final ControllerNode node) {
        if (enabled && node.countedUnowned != generation) {
            node.countedUnowned = generation;
            UNOWNED.merge(node.getName(), 1, Integer::sum);
            unownedCount++;
        }
    }

    static void owned(final ControllerNode node) {
        if (enabled && node.countedUnowned == generation) {
            node.countedUnowned = NOT_COUNTED;
            final int count = UNOWNED.get(node.getName()) - 1;
            if (count == 0) {
                UNOWNED.remove(node.getName());
            } else {
                UNOWNED.put(node.getName(), count);
            }
            unownedCount--;
        }
    }
}
