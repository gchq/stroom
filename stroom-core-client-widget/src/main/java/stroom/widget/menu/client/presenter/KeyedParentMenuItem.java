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
import stroom.widget.util.client.Future;
import stroom.widget.util.client.FutureImpl;

import java.util.List;

public class KeyedParentMenuItem extends IconMenuItem implements HasChildren {
    private final MenuItems menuItems;
    private final MenuKey childMenu;

    public KeyedParentMenuItem(final int priority, final String text, final MenuItems menuItems,
                               final MenuKey childMenu) {
        this(priority, null, null, text, null, true, menuItems, childMenu);
    }

    public KeyedParentMenuItem(final int priority, final Icon enabledIcon, final Icon disabledIcon,
                               final String text, final String shortcut, final boolean enabled, final MenuItems menuItems,
                               final MenuKey childMenu) {
        super(priority, enabledIcon, disabledIcon, text, shortcut, enabled, null);
        this.menuItems = menuItems;
        this.childMenu = childMenu;
    }

    @Override
    public Future<List<Item>> getChildren() {
        final FutureImpl<List<Item>> future = new FutureImpl<>();
        future.setResult(menuItems.getMenuItems(childMenu));
        return future;
    }
}
