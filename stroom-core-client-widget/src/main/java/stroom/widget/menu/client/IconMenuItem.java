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

package stroom.widget.menu.client;

import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.Command;

public class IconMenuItem extends MenuItem {

    private final SvgImage enabledIcon;
    private final SvgImage disabledIcon;
    private final IconColour iconColour;
    private final boolean highlight;

    protected IconMenuItem(final int priority,
                           final SvgImage enabledIcon,
                           final SvgImage disabledIcon,
                           final IconColour iconColour,
                           final SafeHtml text,
                           final SafeHtml tooltip,
                           final Action action,
                           final boolean enabled,
                           final Command command,
                           final boolean highlight) {
        this(priority, enabledIcon, disabledIcon, iconColour, text, tooltip, action, enabled, command,
                highlight, false);
    }

    /// @param opensNewTab Whether choosing the item opens a page in a new browser tab (see
    ///                    [MenuItem#isOpensNewTab]). The other parameters are as for the other
    ///                    constructor.
    protected IconMenuItem(final int priority,
                           final SvgImage enabledIcon,
                           final SvgImage disabledIcon,
                           final IconColour iconColour,
                           final SafeHtml text,
                           final SafeHtml tooltip,
                           final Action action,
                           final boolean enabled,
                           final Command command,
                           final boolean highlight,
                           final boolean opensNewTab) {
        this(priority, enabledIcon, disabledIcon, iconColour, text, tooltip, action, enabled, command,
                highlight, opensNewTab, null);
    }

    /// @param shortcut The shortcut shown beside the item as fixed text (see [MenuItem]), or null.
    ///                 The other parameters are as for the other constructors.
    protected IconMenuItem(final int priority,
                           final SvgImage enabledIcon,
                           final SvgImage disabledIcon,
                           final IconColour iconColour,
                           final SafeHtml text,
                           final SafeHtml tooltip,
                           final Action action,
                           final boolean enabled,
                           final Command command,
                           final boolean highlight,
                           final boolean opensNewTab,
                           final String shortcut) {
        super(priority, text, tooltip, action, enabled, command, opensNewTab, shortcut);
        this.enabledIcon = enabledIcon;
        this.disabledIcon = disabledIcon;
        this.iconColour = iconColour;
        this.highlight = highlight;
    }

    public SvgImage getEnabledIcon() {
        return enabledIcon;
    }

    public SvgImage getDisabledIcon() {
        return disabledIcon;
    }

    public IconColour getIconColour() {
        return iconColour;
    }

    public boolean isHighlight() {
        return highlight;
    }

    protected abstract static class AbstractBuilder<T extends IconMenuItem, B extends AbstractBuilder<T, ?>>
            extends MenuItem.AbstractBuilder<T, B> {

        protected SvgImage enabledIcon = null;
        protected SvgImage disabledIcon = null;
        protected IconColour iconColour;
        protected boolean highlight;
        protected String shortcut;

        public B icon(final SvgImage icon) {
            this.enabledIcon = icon;
            return self();
        }

        public B disabledIcon(final SvgImage icon) {
            this.disabledIcon = icon;
            return self();
        }

        public B iconColour(final IconColour iconColour) {
            this.iconColour = iconColour;
            return self();
        }

        public B highlight(final boolean highlight) {
            this.highlight = highlight;
            return self();
        }

        /// Shows a shortcut beside the item as fixed text, e.g. "Ctrl+C" for a key the browser
        /// handles, rather than the key bound to the item's action.
        ///
        /// @param shortcut The shortcut's text, or null for none.
        /// @return This builder.
        public B shortcut(final String shortcut) {
            this.shortcut = shortcut;
            return self();
        }

        protected abstract B self();

        public abstract T build();
    }

    public static class Builder
            extends AbstractBuilder<IconMenuItem, Builder> {

        @Override
        protected Builder self() {
            return this;
        }

        public IconMenuItem build() {
            return new IconMenuItem(
                    priority,
                    enabledIcon,
                    disabledIcon,
                    iconColour,
                    text,
                    tooltip,
                    action,
                    enabled,
                    command,
                    highlight,
                    opensNewTab,
                    shortcut);
        }
    }
}
