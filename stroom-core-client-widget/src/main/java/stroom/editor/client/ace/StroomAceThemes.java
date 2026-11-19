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

package stroom.editor.client.ace;

import stroom.ui.config.shared.AceEditorTheme;

/// Registers Stroom's own editor themes (e.g. [AceEditorTheme#STROOM_DARK]) with Ace, so that Ace
/// can set them by name like its own themes. Ace loads its themes from `theme-*.js` files, but
/// Stroom's are defined here instead, with their CSS in Stroom's `editor-themes.css`, where it can
/// use the Stroom theme's colours.
final class StroomAceThemes {

    private static boolean registered;

    private StroomAceThemes() {
        // Static methods only
    }

    /// Registers the themes with Ace, if they are not already registered. Ace must be loaded.
    static void register() {
        if (!registered) {
            for (final AceEditorTheme theme : AceEditorTheme.values()) {
                if (theme.isStroomTheme()) {
                    define(theme.getName(), "ace-" + theme.getName().replace('_', '-'), theme.isDark());
                }
            }
            registered = true;
        }
    }

    // Defines the theme's module as a theme-*.js file would, but with no CSS of its own
    private static native void define(String name, String cssClass, boolean isDark) /*-{
        $wnd.ace.define("ace/theme/" + name, ["require", "exports", "module"], function (require, exports) {
            exports.isDark = isDark;
            exports.cssClass = cssClass;
            exports.cssText = "";
        });
    }-*/;
}
