/*
 * Copyright 2026 Crown Copyright
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

package stroom.document.client;

import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerNodePermissions;
import stroom.feed.shared.FeedDoc;
import stroom.security.shared.DocumentPermission;
import stroom.widget.util.client.KeyBinding.Action;

import java.util.List;
import java.util.Map;

/// Whether the keys for an explorer item's actions (F2 to rename it, Alt+Enter for its info) can act
/// on the items chosen, as the item's menu would allow, and if not, why not, for screen reader users.
final class ExplorerItemKeys {

    private ExplorerItemKeys() {
        // Static methods only
    }

    /// Why an item's action can't be done on the chosen items, or null if it can.
    ///
    /// @param action      [Action#EXPLORER_INFO] or [Action#EXPLORER_RENAME].
    /// @param items       The items chosen in the explorer.
    /// @param permissions The user's permissions on each item.
    /// @return The reason, e.g. "Feeds can't be renamed", or null.
    static String whyNot(final Action action,
                         final List<ExplorerNode> items,
                         final Map<ExplorerNode, ExplorerNodePermissions> permissions) {
        final boolean info = action == Action.EXPLORER_INFO;
        if (items.size() != 1) {
            return info
                    ? "Info is shown for one item at a time"
                    : "Items are renamed one at a time";
        }

        final ExplorerNode item = items.get(0);
        final String name = item.getName();
        final ExplorerNodePermissions itemPermissions = permissions.get(item);
        if (info) {
            if (DocumentTypes.isSystem(item.getType())) {
                return "No info for " + name;
            } else if (!has(itemPermissions, DocumentPermission.VIEW)) {
                return "You can't view " + name;
            }
        } else if (DocumentTypes.isSystem(item.getType())) {
            return name + " can't be renamed";
        } else if (FeedDoc.TYPE.equals(item.getType())) {
            // Feeds are a special case so can't be renamed, see https://github.com/gchq/stroom/issues/2912
            return "Feeds can't be renamed";
        } else if (!has(itemPermissions, DocumentPermission.EDIT)) {
            return "You can't rename " + name;
        }
        return null;
    }

    private static boolean has(final ExplorerNodePermissions permissions, final DocumentPermission permission) {
        return permissions != null && permissions.hasDocumentPermission(permission);
    }
}
