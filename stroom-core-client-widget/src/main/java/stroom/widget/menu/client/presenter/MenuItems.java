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

import com.google.inject.Singleton;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

@Singleton
public class MenuItems {

    private final Map<MenuKey, Set<Item>> menuItems = new HashMap<>();
    private final Map<MenuKey, List<Item>> compressedMenuItems = new HashMap<>();
    private boolean needsCompression = true;

    public List<Item> getMenuItems(final MenuKey menuKey) {
        if (needsCompression) {
            compress();
        }

        return compressedMenuItems.get(menuKey);
    }

    public void addMenuItem(final MenuKey menuKey, final Item item) {
        needsCompression = true;
        menuItems.computeIfAbsent(menuKey, k -> new HashSet<>()).add(item);
    }

    private void compress() {
        for (final Entry<MenuKey, Set<Item>> entry : menuItems.entrySet()) {
            final Set<Item> items = entry.getValue();
            // Sort the items.
            final List<Item> sortedItems = new ArrayList<>(items);
            sortedItems.sort(new ItemComparator());

            final List<Item> compressedItems = new ArrayList<>();
            for (final Item item : sortedItems) {
                if (item != null) {
                    compressedItems.add(item);
                }
            }

            // Cleanse the child items.
            if (!compressedItems.isEmpty()) {
                // Remove leading separators.
                while (!compressedItems.isEmpty() && compressedItems.get(0) instanceof Separator) {
                    compressedItems.remove(0);
                }

                // Remove trailing separators.
                while (!compressedItems.isEmpty()
                       && compressedItems.get(compressedItems.size() - 1)  instanceof Separator) {
                    compressedItems.remove(compressedItems.size() - 1);
                }

                // Remove consecutive separators.
                boolean lastSeparator = false;
                for (int i = compressedItems.size() - 1; i >= 0; i--) {
                    if (compressedItems.get(i) instanceof Separator) {
                        if (lastSeparator) {
                            compressedItems.remove(i);
                        }
                        lastSeparator = true;
                    } else {
                        lastSeparator = false;
                    }
                }
            }

            compressedMenuItems.put(entry.getKey(), compressedItems);
        }

        needsCompression = false;
    }

    public void clear(final MenuKey menuKey) {
        menuItems.remove(menuKey);
        compressedMenuItems.remove(menuKey);
    }

    public void clear() {
        menuItems.clear();
        compressedMenuItems.clear();
    }

    @Override
    public String toString() {
        final List<MenuKey> keys = new ArrayList<>(menuItems.keySet());
        keys.sort(new MenuKeyComparator());

        final StringBuilder sb = new StringBuilder();
        for (final MenuKey key : keys) {
            sb.append(" + ");
            sb.append(key.toString());
            sb.append("\n");

            final Set<Item> items = menuItems.get(key);
            // Sort the items.
            final List<Item> sortedItems = new ArrayList<>(items);
            sortedItems.sort(new ItemComparator());

            for (final Item item : sortedItems) {
                sb.append("    - ");
                sb.append(item.toString());
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    public static class ItemComparator implements Comparator<Item> {

        @Override
        public int compare(final Item o1, final Item o2) {
            if (o1.getPriority() == o2.getPriority()) {
                if (o1 instanceof MenuItem && o2 instanceof MenuItem) {
                    return ((MenuItem) o1).getText().asString().compareTo(((MenuItem) o2).getText().asString());
                } else if (o1 instanceof Separator && o2 instanceof Separator) {
                    return 0;
                } else if (o1 instanceof Separator) {
                    return -1;
                } else if (o2 instanceof Separator) {
                    return 1;
                }
            }

            return Integer.compare(o1.getPriority(), o2.getPriority());
        }
    }

    private static class MenuKeyComparator implements Comparator<MenuKey> {

        @Override
        public int compare(final MenuKey o1, final MenuKey o2) {
            if (o1.toString() == null && o2.toString() == null) {
                return 0;
            } else if (o1.toString() == null) {
                return -1;
            } else if (o2.toString() == null) {
                return 1;
            }

            return o1.toString().compareTo(o2.toString());
        }
    }
}
