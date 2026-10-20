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
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.Command;

public abstract class MenuItem extends Item {

    private final SafeHtml text;
    private final SafeHtml tooltip;
    private final Action action;
    private final Command command;
    private final boolean enabled;
    private final boolean opensNewTab;

    protected MenuItem(final int priority,
                       final SafeHtml text,
                       final SafeHtml tooltip,
                       final Action action,
                       final boolean enabled,
                       final Command command) {
        this(priority, text, tooltip, action, enabled, command, false);
    }

    /// @param priority    Where the item goes in its menu, lowest first.
    /// @param text        The item's text.
    /// @param tooltip     The item's tooltip, or null for its text.
    /// @param action      The keyboard shortcut shown beside the item, or null.
    /// @param enabled     Whether the item can be chosen.
    /// @param command     What choosing the item does, or null.
    /// @param opensNewTab Whether choosing the item opens a page in a new browser tab, which the
    ///                    menu tells screen readers (see [#isOpensNewTab]).
    protected MenuItem(final int priority,
                       final SafeHtml text,
                       final SafeHtml tooltip,
                       final Action action,
                       final boolean enabled,
                       final Command command,
                       final boolean opensNewTab) {
        super(priority);
        this.text = text;
        this.tooltip = tooltip;
        this.action = action;
        this.enabled = enabled;
        this.command = command;
        this.opensNewTab = opensNewTab;
    }

    public SafeHtml getText() {
        return text;
    }

    public SafeHtml getTooltip() {
        return tooltip;
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

    /// @return True if choosing the item opens a page in a new browser tab. The menu then ends
    /// the item's accessible name with the visually hidden text " (opens in new tab)", leaving its
    /// visible text as it is.
    public boolean isOpensNewTab() {
        return opensNewTab;
    }


    // --------------------------------------------------------------------------------


    protected abstract static class AbstractBuilder<T extends MenuItem, B extends MenuItem.AbstractBuilder<T, ?>>
            extends Item.AbstractBuilder<T, B> {

        protected SafeHtml text;
        protected SafeHtml tooltip;
        protected Action action;
        protected Command command;
        protected boolean enabled = true;
        protected boolean opensNewTab;

        public B text(final SafeHtml text) {
            this.text = text;
            return self();
        }

        public B text(final String text) {
            this.text = SafeHtmlUtil.from(text);
            return self();
        }

        public B tooltip(final String tooltip) {
            this.tooltip = SafeHtmlUtil.from(tooltip);
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

        /// Marks the item as opening a page in a new browser tab (e.g. with `Window.open`), so
        /// that screen readers are told so (see [MenuItem#isOpensNewTab]).
        ///
        /// @return This builder.
        public B opensNewTab() {
            this.opensNewTab = true;
            return self();
        }

        protected abstract B self();

        public abstract T build();
    }
}
