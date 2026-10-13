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


package stroom.gwt.workbench.client.app.main;

import stroom.gwt.workbench.client.app.rest.JsonValues;
import stroom.gwt.workbench.client.widgets.tree.ExplorerFixture;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestAppShellFixtures {

    @Test
    void testDictTree_hasFixedUuids() {
        final ExplorerFixture tree = AppShellFixtures.dictTree();

        assertThat(tree.get("Dictionaries").getUuid()).isEqualTo("folder-root");
        assertThat(tree.get("Countries").getUuid()).isEqualTo("dict-countries");
        assertThat(tree.get("Colours").getUuid()).isEqualTo("dict-colours");
        assertThat(tree.get("EVENTS").getUuid()).isEqualTo("feed-events");
        assertThat(tree.get("EVENTS").getType()).isEqualTo("Feed");
    }

    @Test
    void testRootedTree_hasStroomsRootUuids() {
        final ExplorerFixture tree = AppShellFixtures.rootedTree();

        assertThat(tree.get("Favourites").getUuid()).isEqualTo("1");
        assertThat(tree.get("System").getUuid()).isEqualTo("0");
        // The dictionaries are in System's tree
        assertThat(tree.get("Countries").getRootNodeUuid()).isEqualTo("0");
    }

    @Test
    void testExplorerPermissions_answersEachNodeAsSent() {
        final String request = "[{\"type\": \"Dictionary\", \"uuid\": \"d1\", \"name\": \"A\", \"depth\": 2},"
                + " {\"type\": \"Folder\", \"uuid\": \"f1\"}]";

        final String reply = AppShellFixtures.explorerPermissions(request,
                AppShellFixtures.VIEW_ONLY, List.of("Dictionary"));

        assertThat(JsonValues.jsonEquals(reply, "["
                + "{\"explorerNode\": {\"type\": \"Dictionary\", \"uuid\": \"d1\", \"name\": \"A\", \"depth\": 2},"
                + " \"documentPermissions\": [\"VIEW\"], \"createPermissions\": [\"Dictionary\"], \"admin\": false},"
                + "{\"explorerNode\": {\"type\": \"Folder\", \"uuid\": \"f1\"},"
                + " \"documentPermissions\": [\"VIEW\"], \"createPermissions\": [\"Dictionary\"], \"admin\": false}"
                + "]")).isTrue();
    }

    @Test
    void testExplorerPermissions_noNodes() {
        assertThat(AppShellFixtures.explorerPermissions("[]", AppShellFixtures.FULL_PERMISSIONS, List.of()))
                .isEqualTo("[]");
    }

    @Test
    void testDecorate_namesAKnownDocument() {
        final String reply = AppShellFixtures.decorate(Map.of("dict-countries", "Countries"),
                "{\"docRef\": {\"type\": \"Dictionary\", \"uuid\": \"dict-countries\"}}");

        assertThat(JsonValues.jsonEquals(reply,
                "{\"type\": \"Dictionary\", \"uuid\": \"dict-countries\", \"name\": \"Countries\"}")).isTrue();
    }

    @Test
    void testDecorate_keepsTheNameOfAnUnknownDocument() {
        final String reply = AppShellFixtures.decorate(Map.of(),
                "{\"docRef\": {\"type\": \"Feed\", \"uuid\": \"x\", \"name\": \"X\"}}");

        assertThat(JsonValues.jsonEquals(reply, "{\"type\": \"Feed\", \"uuid\": \"x\", \"name\": \"X\"}")).isTrue();
    }

    @Test
    void testCreated_isANewNodeInTheDestinationsTree() {
        final String reply = AppShellFixtures.created("{\"docType\": \"Dictionary\", \"docName\": \"Regions\", "
                + "\"destinationFolder\": {\"type\": \"Folder\", \"uuid\": \"f1\", \"rootNodeUuid\": \"r1\"}}");

        assertThat(JsonValues.jsonEquals(reply, "{\"type\": \"Dictionary\", \"uuid\": \"new-doc\", "
                + "\"name\": \"Regions\", \"rootNodeUuid\": \"r1\", "
                + "\"uniqueKey\": {\"type\": \"Dictionary\", \"uuid\": \"new-doc\", \"rootNodeUuid\": \"r1\"}, "
                + "\"nodeFlags\": [\"L\"]}")).isTrue();
    }

    @Test
    void testCreated_withoutADestination() {
        final String reply = AppShellFixtures.created("{\"docType\": \"Feed\", \"docName\": \"F\"}");

        assertThat(JsonValues.jsonContains(reply, "{\"type\": \"Feed\", \"rootNodeUuid\": null}")).isTrue();
    }

    @Test
    void testRenamed_isTheNodeWithItsNewName() {
        final String reply = AppShellFixtures.renamed("{\"explorerNode\": {\"type\": \"Dictionary\", "
                + "\"uuid\": \"d1\", \"name\": \"Countries\", \"tags\": [\"a\"]}, \"docName\": \"Nations\"}");

        assertThat(JsonValues.jsonEquals(reply,
                "{\"type\": \"Dictionary\", \"uuid\": \"d1\", \"name\": \"Nations\", \"tags\": [\"a\"]}")).isTrue();
    }

    @Test
    void testDeleted_reportsEveryDocumentDeleted() {
        final String reply = AppShellFixtures.deleted("{\"docRefs\": [{\"type\": \"Dictionary\", \"uuid\": \"d1\"},"
                + " {\"type\": \"Dictionary\", \"uuid\": \"d2\"}]}");

        assertThat(JsonValues.jsonEquals(reply, "{\"explorerNodes\": [{\"type\": \"Dictionary\", \"uuid\": \"d1\"},"
                + " {\"type\": \"Dictionary\", \"uuid\": \"d2\"}], \"message\": \"\"}")).isTrue();
    }

    @Test
    void testToJson_writesEachKindOfValue() {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("s", "a\"b\\c\nd\te\u0001");
        map.put("n", JsonValues.parse("10"));
        map.put("d", JsonValues.parse("1.5"));
        map.put("b", true);
        map.put("z", null);
        map.put("l", Arrays.asList(1, "x"));

        final String json = AppShellFixtures.toJson(map);

        assertThat(json).isEqualTo("{\"s\":\"a\\\"b\\\\c\\nd\\te\\u0001\",\"n\":10,\"d\":1.5,\"b\":true,"
                + "\"z\":null,\"l\":[1,\"x\"]}");
        // And reads back as the same value
        assertThat(JsonValues.parse(json)).isEqualTo(JsonValues.parse(json));
        assertThat(((Map<?, ?>) JsonValues.parse(json)).get("s")).isEqualTo("a\"b\\c\nd\te\u0001");
    }

    @Test
    void testExplorerPermissions_refusesInvalidJson() {
        assertThatThrownBy(() -> AppShellFixtures.explorerPermissions("not json", List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
