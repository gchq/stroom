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

public class IconMenuItem extends MenuItem {

    private final Icon enabledIcon;
    private final Icon disabledIcon;
    private final boolean highlight;

    protected IconMenuItem(final int priority,
                           final Icon enabledIcon,
                           final Icon disabledIcon,
                           final String text,
                           final String shortcut,
                           final boolean enabled,
                           final Command command,
                           final boolean highlight) {
        super(priority, text, shortcut, enabled, command);
        this.enabledIcon = enabledIcon;
        this.disabledIcon = disabledIcon;
        this.highlight = highlight;
    }

    public Icon getEnabledIcon() {
        return enabledIcon;
    }

    public Icon getDisabledIcon() {
        return disabledIcon;
    }

    public boolean isHighlight() {
        return highlight;
    }

    protected abstract static class AbstractBuilder<T extends IconMenuItem, B extends AbstractBuilder<T, ?>>
            extends MenuItem.AbstractBuilder<T, B> {

        protected Icon enabledIcon = null;
        protected Icon disabledIcon = null;
        protected boolean highlight;

        public B icon(final Icon icon) {
            this.enabledIcon = icon;
            return self();
        }

        public B disabledIcon(final Icon icon) {
            this.disabledIcon = icon;
            return self();
        }

        public B highlight(final boolean highlight) {
            this.highlight = highlight;
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
                    command,
                    highlight);
        }
    }
}
