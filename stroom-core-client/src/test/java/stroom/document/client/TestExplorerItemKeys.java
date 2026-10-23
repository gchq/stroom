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

import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerNodePermissions;
import stroom.feed.shared.FeedDoc;
import stroom.security.shared.DocumentPermission;
import stroom.widget.util.client.KeyBinding.Action;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestExplorerItemKeys {

    private static final ExplorerNode COUNTRIES = node("Dictionary", "dict-countries", "Countries");
    private static final ExplorerNode COLOURS = node("Dictionary", "dict-colours", "Colours");
    private static final ExplorerNode EVENTS = node(FeedDoc.TYPE, "feed-events", "EVENTS");
    private static final ExplorerNode SYSTEM = node(ExplorerConstants.SYSTEM_TYPE, "system", "System");

    @Test
    void allowsInfoAndRenameWithThePermissions() {
        final Map<ExplorerNode, ExplorerNodePermissions> permissions = permissions(COUNTRIES,
                DocumentPermission.VIEW, DocumentPermission.EDIT);

        assertThat(ExplorerItemKeys.whyNot(Action.EXPLORER_INFO, List.of(COUNTRIES), permissions)).isNull();
        assertThat(ExplorerItemKeys.whyNot(Action.EXPLORER_RENAME, List.of(COUNTRIES), permissions)).isNull();
    }

    @Test
    void allowsBothForAnAdministrator() {
        final Map<ExplorerNode, ExplorerNodePermissions> permissions = new HashMap<>();
        permissions.put(COUNTRIES, new ExplorerNodePermissions(COUNTRIES, Set.of(), Set.of(), true));

        assertThat(ExplorerItemKeys.whyNot(Action.EXPLORER_INFO, List.of(COUNTRIES), permissions)).isNull();
        assertThat(ExplorerItemKeys.whyNot(Action.EXPLORER_RENAME, List.of(COUNTRIES), permissions)).isNull();
    }

    @Test
    void saysWhyNotWithoutThePermissions() {
        final Map<ExplorerNode, ExplorerNodePermissions> permissions = permissions(COUNTRIES,
                DocumentPermission.VIEW);

        assertThat(ExplorerItemKeys.whyNot(Action.EXPLORER_INFO, List.of(COUNTRIES), permissions)).isNull();
        assertThat(ExplorerItemKeys.whyNot(Action.EXPLORER_RENAME, List.of(COUNTRIES), permissions))
                .isEqualTo("You can't rename Countries");

        final Map<ExplorerNode, ExplorerNodePermissions> none = permissions(COUNTRIES);
        assertThat(ExplorerItemKeys.whyNot(Action.EXPLORER_INFO, List.of(COUNTRIES), none))
                .isEqualTo("You can't view Countries");
        // No permissions fetched at all
        assertThat(ExplorerItemKeys.whyNot(Action.EXPLORER_INFO, List.of(COUNTRIES), Collections.emptyMap()))
                .isEqualTo("You can't view Countries");
    }

    @Test
    void saysFeedsCantBeRenamed() {
        final Map<ExplorerNode, ExplorerNodePermissions> permissions = permissions(EVENTS,
                DocumentPermission.VIEW, DocumentPermission.EDIT);

        assertThat(ExplorerItemKeys.whyNot(Action.EXPLORER_RENAME, List.of(EVENTS), permissions))
                .isEqualTo("Feeds can't be renamed");
        // A feed's info is shown
        assertThat(ExplorerItemKeys.whyNot(Action.EXPLORER_INFO, List.of(EVENTS), permissions)).isNull();
    }

    @Test
    void saysTheSystemRootIsNeitherRenamedNorHasInfo() {
        final Map<ExplorerNode, ExplorerNodePermissions> permissions = new HashMap<>();
        permissions.put(SYSTEM, new ExplorerNodePermissions(SYSTEM, Set.of(), Set.of(), true));

        assertThat(ExplorerItemKeys.whyNot(Action.EXPLORER_RENAME, List.of(SYSTEM), permissions))
                .isEqualTo("System can't be renamed");
        assertThat(ExplorerItemKeys.whyNot(Action.EXPLORER_INFO, List.of(SYSTEM), permissions))
                .isEqualTo("No info for System");
    }

    @Test
    void actsOnOneItemAtATime() {
        final Map<ExplorerNode, ExplorerNodePermissions> permissions = permissions(COUNTRIES,
                DocumentPermission.VIEW, DocumentPermission.EDIT);
        permissions.putAll(permissions(COLOURS, DocumentPermission.VIEW, DocumentPermission.EDIT));
        final List<ExplorerNode> both = List.of(COUNTRIES, COLOURS);

        assertThat(ExplorerItemKeys.whyNot(Action.EXPLORER_INFO, both, permissions))
                .isEqualTo("Info is shown for one item at a time");
        assertThat(ExplorerItemKeys.whyNot(Action.EXPLORER_RENAME, both, permissions))
                .isEqualTo("Items are renamed one at a time");
        // None chosen
        assertThat(ExplorerItemKeys.whyNot(Action.EXPLORER_RENAME, List.of(), permissions))
                .isEqualTo("Items are renamed one at a time");
    }

    private static ExplorerNode node(final String type, final String uuid, final String name) {
        return ExplorerNode.builder().type(type).uuid(uuid).name(name).build();
    }

    private static Map<ExplorerNode, ExplorerNodePermissions> permissions(final ExplorerNode node,
                                                                          final DocumentPermission... granted) {
        final Map<ExplorerNode, ExplorerNodePermissions> permissions = new HashMap<>();
        permissions.put(node, new ExplorerNodePermissions(node, Set.of(), Set.of(granted), false));
        return permissions;
    }
}
