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

package stroom.widget.menu.client.presenter;

import stroom.svg.client.Icon;
import stroom.svg.client.Preset;

import com.google.gwt.user.client.Command;

public class IconMenuItem extends CommandMenuItem {

    private final Icon enabledIcon;
    private final Icon disabledIcon;

    public IconMenuItem(final int priority, final Icon enabledIcon, final Icon disabledIcon,
                        final String text, final String shortcut, final boolean enabled, final Command command) {
        super(priority, text, shortcut, enabled, command);
        this.enabledIcon = enabledIcon;
        this.disabledIcon = disabledIcon;
    }

    public IconMenuItem(final int priority, final String text, final String shortcut, final boolean enabled,
                        final Command command) {
        super(priority, text, shortcut, enabled, command);
        this.enabledIcon = null;
        this.disabledIcon = null;
    }

    public Icon getEnabledIcon() {
        return enabledIcon;
    }

    public Icon getDisabledIcon() {
        return disabledIcon;
    }

    public static Builder builder(final int priority) {
        return new Builder(priority);
    }

    public static class Builder {

        private final int priority;
        private String text = null;
        private String shortcut = null;
        private Command command = null;
        private boolean enabled = true;
        private Icon enabledIcon = null;
        private Icon disabledIcon = null;


        Builder(final int priority) {
            this.priority = priority;
        }

        public Builder withText(final String text) {
            this.text = text;
            return this;
        }

        public Builder withShortcut(final String shortcut) {
            this.shortcut = shortcut;
            return this;
        }

        public Builder withCommand(final Command command) {
            this.command = command;
            return this;
        }

        public Builder withEnabledState(final boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder disabled() {
            this.enabled = false;
            return this;
        }

        public Builder withIcon(final Icon icon) {
            this.enabledIcon = icon;
            return this;
        }

        public Builder withDisabledIcon(final Icon icon) {
            this.disabledIcon = icon;
            return this;
        }

        public Item build() {
            if (text == null && enabledIcon != null && enabledIcon instanceof Preset) {
                text = ((Preset) enabledIcon).getTitle();
            }
            return new IconMenuItem(
                    priority,
                    enabledIcon,
                    disabledIcon,
                    text,
                    shortcut,
                    enabled,
                    command);
        }
    }
}
