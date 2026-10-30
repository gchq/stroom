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

package stroom.main.client;

import stroom.util.shared.NullSafe;

/// Builds the browser page title so that it reflects the content tab the user is working on.
public final class PageTitle {

    static final String SEPARATOR = " - ";

    private PageTitle() {
        // Utility class
    }

    /// Build the page title from the configured base title and the label of the selected tab.
    ///
    /// @param baseTitle The configured HTML title, e.g. `Stroom`.
    /// @param tabLabel  The label of the selected content tab, or `null` if no tab is selected.
    /// @return `baseTitle - tabLabel`, or just `baseTitle` if `tabLabel` is blank.
    public static String build(final String baseTitle, final String tabLabel) {
        if (NullSafe.isBlankString(tabLabel)) {
            return baseTitle;
        }
        return baseTitle + SEPARATOR + tabLabel.trim();
    }
}
