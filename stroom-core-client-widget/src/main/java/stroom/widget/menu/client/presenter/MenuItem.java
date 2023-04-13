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

import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.user.client.Command;

public abstract class MenuItem extends Item {

    private final String text;
    private final Action action;
    private final Command command;
    private final boolean enabled;

    protected MenuItem(final int priority,
                       final String text,
                       final Action action,
                       final boolean enabled,
                       final Command command) {
        super(priority);
        this.text = text;
        this.action = action;
        this.enabled = enabled;
        this.command = command;
    }

    public String getText() {
        return text;
    }

    public Action getAction() {
        return action;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Command getCommand() {
        return command;
    }

    protected abstract static class AbstractBuilder<T extends MenuItem, B extends MenuItem.AbstractBuilder<T, ?>>
            extends Item.AbstractBuilder<T, B> {

        protected String text;
        protected Action action;
        protected Command command;
        protected boolean enabled = true;

        public B text(final String text) {
            this.text = text;
            return self();
        }

        public B action(final Action action) {
            this.action = action;
            return self();
        }

        public B command(final Command command) {
            this.command = command;
            return self();
        }

        public B enabled(final boolean enabled) {
            this.enabled = enabled;
            return self();
        }

        protected abstract B self();

        public abstract T build();
    }
}
