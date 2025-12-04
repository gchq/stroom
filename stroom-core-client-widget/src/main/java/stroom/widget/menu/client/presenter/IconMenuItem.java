/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.svg.client.IconColour;
import stroom.svg.client.Preset;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
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
        super(priority, text, tooltip, action, enabled, command);
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

        public B icon(final Preset svgPreset) {
            this.enabledIcon = NullSafe.get(svgPreset, Preset::getSvgImage);
            return self();
        }

        public B icon(final SvgImage icon) {
            this.enabledIcon = icon;
            return self();
        }

        public B disabledIcon(final Preset svgPreset) {
            this.disabledIcon = NullSafe.get(svgPreset, Preset::getSvgImage);
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
//            if (text == null && enabledIcon != null && enabledIcon instanceof Preset) {
//                text = ((Preset) enabledIcon).getTitle();
//            }
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
                    highlight);
        }
    }
}
