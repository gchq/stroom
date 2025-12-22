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
import stroom.svg.shared.SvgImage;
import stroom.widget.util.client.Future;
import stroom.widget.util.client.FutureImpl;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.safehtml.shared.SafeHtml;

import java.util.List;
import java.util.Objects;

public class KeyedParentMenuItem extends IconMenuItem implements HasChildren {

    private final MenuItems menuItems;
    private final MenuKey menuKey;

    KeyedParentMenuItem(final int priority,
                        final SvgImage enabledIcon,
                        final SvgImage disabledIcon,
                        final IconColour iconColour,
                        final SafeHtml text,
                        final SafeHtml tooltip,
                        final Action action,
                        final boolean enabled,
                        final MenuItems menuItems,
                        final MenuKey menuKey) {
        super(priority,
                enabledIcon,
                disabledIcon,
                iconColour,
                text,
                tooltip,
                action,
                enabled,
                null,
                false);
        this.menuItems = menuItems;
        this.menuKey = menuKey;
    }

    @Override
    public Future<List<Item>> getChildren() {
        final FutureImpl<List<Item>> future = new FutureImpl<>();
        future.setResult(menuItems.getMenuItems(menuKey));
        return future;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final KeyedParentMenuItem that = (KeyedParentMenuItem) o;
        return Objects.equals(menuKey, that.menuKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(menuKey);
    }

    public static class Builder extends AbstractBuilder<KeyedParentMenuItem, Builder> {

        MenuItems menuItems;
        MenuKey menuKey;

        public Builder menuItems(final MenuItems menuItems) {
            this.menuItems = menuItems;
            return self();
        }

        public Builder menuKey(final MenuKey menuKey) {
            this.menuKey = menuKey;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public KeyedParentMenuItem build() {
//            if (text == null && enabledIcon != null && enabledIcon instanceof Preset) {
//                text = ((Preset) enabledIcon).getTitle();
//            }
            return new KeyedParentMenuItem(
                    priority,
                    enabledIcon,
                    disabledIcon,
                    iconColour,
                    text,
                    tooltip,
                    action,
                    enabled,
                    menuItems,
                    menuKey);
        }
    }
}
