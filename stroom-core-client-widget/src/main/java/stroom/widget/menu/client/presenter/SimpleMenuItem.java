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

import com.google.gwt.user.client.Command;

public class SimpleMenuItem extends CommandMenuItem {

    public SimpleMenuItem(final int priority,
                          final String text,
                          final String shortcut,
                          final boolean enabled,
                          final Command command) {
        super(priority, text, shortcut, enabled, command);
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

        public Item build() {
            return new SimpleMenuItem(
                    priority,
                    text,
                    shortcut,
                    enabled,
                    command);
        }
    }
}
