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

package stroom.gwt.workbench.framework.client.preview;

import stroom.gwt.workbench.framework.client.BrowserUtil;

/// Lets stories report actions (e.g. a button being clicked) to the Actions tab of the addon
/// panel, the equivalent of Storybook's `action()`/`fn()`.
public final class StoryActions {

    private StoryActions() {
        // Static utility
    }

    /// Logs an action to the Actions tab.
    ///
    /// @param name   The name of the action, e.g. `onClick`.
    /// @param detail Detail about the action, e.g. a new value. May be null.
    public static void log(final String name, final String detail) {
        BrowserUtil.postToManager(BrowserUtil.ACTION_MESSAGE, name, detail);
    }

    /// Logs an action with no detail to the Actions tab.
    ///
    /// @param name The name of the action, e.g. `onClick`.
    public static void log(final String name) {
        log(name, null);
    }
}
