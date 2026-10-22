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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/// Development checks of the ownership tree. While they are enabled, misusing ownership (owning a
/// disposed or shared component, owning one twice, releasing one that isn't a child) throws, and
/// the components that nothing owns are counted, so that leaks are found in workbench stories
/// rather than in production.
///
/// They are enabled on the JVM (tests) and in GWT modules that set the binding property
/// `stroom.componentChecks` to `ENABLED` (see `Component.gwt.xml`); the application leaves it
/// `DISABLED`, which compiles the checks out. With them disabled, misuse is ignored as safely as
/// it can be (see [ComponentNode#adopt(ComponentNode)]).
public final class ComponentChecks {

    // Live components that nothing owns, oldest first
    private static final Set<ComponentNode> UNOWNED = new LinkedHashSet<>();

    private static boolean enabled = !"DISABLED".equals(System.getProperty("stroom.componentChecks"));

    private ComponentChecks() {
        // Static only
    }

    /// @return Whether the checks are enabled.
    public static boolean isEnabled() {
        return enabled;
    }

    /// @return The number of live components that nothing owns.
    public static int unownedCount() {
        return UNOWNED.size();
    }

    /// @return The names of the live components that nothing owns, oldest first.
    public static List<String> unownedNames() {
        final List<String> names = new ArrayList<>(UNOWNED.size());
        for (final ComponentNode node : UNOWNED) {
            names.add(node.getName());
        }
        return names;
    }

    /// Turns the checks on or off, for tests of both behaviours.
    ///
    /// @param enabled Whether they are enabled.
    static void setEnabled(final boolean enabled) {
        ComponentChecks.enabled = enabled;
        if (!enabled) {
            UNOWNED.clear();
        }
    }

    /// Forgets the unowned components counted so far, so that tests start from nothing.
    static void reset() {
        UNOWNED.clear();
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

    static void unowned(final ComponentNode node) {
        if (enabled) {
            UNOWNED.add(node);
        }
    }

    static void owned(final ComponentNode node) {
        if (enabled) {
            UNOWNED.remove(node);
        }
    }
}
