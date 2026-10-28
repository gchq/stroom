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

package stroom.widget.menu.client;

import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.Command;

/// A menu item that turns a setting on or off, e.g. an editor's 'Wrap Lines'.
///
/// The menu shows a tick in the item's icon place while the setting is on, and tells screen readers
/// that the item is a check box (`role="menuitemcheckbox"`) and whether it is ticked
/// (`aria-checked`), so the item's text is just the setting's name, with no "(ON)" or "(OFF)".
/// Choosing the item runs its command, which should turn the setting the other way.
public class CheckMenuItem extends IconMenuItem {

    private final boolean checked;

    protected CheckMenuItem(final int priority,
                            final IconColour iconColour,
                            final SafeHtml text,
                            final SafeHtml tooltip,
                            final Action action,
                            final boolean enabled,
                            final Command command,
                            final boolean checked) {
        super(priority,
                checked
                        ? SvgImage.TICK
                        : null,
                null,
                iconColour,
                text,
                tooltip,
                action,
                enabled,
                command,
                false);
        this.checked = checked;
    }

    /// @return True if the setting is on, i.e. the item is ticked.
    public boolean isChecked() {
        return checked;
    }


    // --------------------------------------------------------------------------------


    /// Builds a [CheckMenuItem]. Any icon given is ignored, as the tick takes the icon's place.
    public static class Builder extends AbstractBuilder<CheckMenuItem, Builder> {

        private boolean checked;

        /// Sets whether the setting is on, i.e. whether the item is ticked.
        ///
        /// @param checked True if the setting is on.
        /// @return This builder.
        public Builder checked(final boolean checked) {
            this.checked = checked;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public CheckMenuItem build() {
            return new CheckMenuItem(
                    priority,
                    iconColour,
                    text,
                    tooltip,
                    action,
                    enabled,
                    command,
                    checked);
        }
    }
}
