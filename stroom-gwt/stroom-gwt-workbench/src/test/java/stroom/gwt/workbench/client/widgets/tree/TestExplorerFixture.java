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


package stroom.gwt.workbench.client.widgets.tree;

import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerNodeKey;
import stroom.gwt.workbench.client.app.rest.JsonValues;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestExplorerFixture {

    private static ExplorerFixture fixture() {
        return new ExplorerFixture(ExplorerFixture.folder("System",
                ExplorerFixture.folder("Feeds",
                        ExplorerFixture.doc("TEST_FEED", "Feed"),
                        ExplorerFixture.doc("PROD_FEED", "Feed")),
                ExplorerFixture.folder("Pipelines",
                        ExplorerFixture.doc("Ingest", "Pipeline"),
                        ExplorerFixture.doc("Standard XSLT", "XSLT").withInfo("ERROR", "Missing \"index\""))));
    }

    @Test
    void testUuidsDepthsAndRoots() {
        final ExplorerFixture fixture = fixture();
        assertThat(fixture.get("System").getUuid()).isEqualTo("folder-1");
        assertThat(fixture.get("Feeds").getUuid()).isEqualTo("folder-2");
        assertThat(fixture.get("PROD_FEED").getUuid()).isEqualTo("feed-2");
        assertThat(fixture.get("Ingest").getDepth()).isEqualTo(2);
        assertThat(fixture.get("Ingest").getRootNodeUuid()).isEqualTo("folder-1");
        assertThat(fixture.get("System").getRootNodeUuid()).isEqualTo("folder-1");
        assertThat(fixture.get("Feeds").isFolder()).isTrue();
    }

    @Test
    void testExplicitUuids() {
        final ExplorerFixture fixture = new ExplorerFixture(
                ExplorerFixture.folder("System", "System",
                        ExplorerFixture.folder("My Folder",
                                ExplorerFixture.doc("Alpha", "Dictionary").withUuid("a"),
                                ExplorerFixture.doc("Beta", "Dictionary")).withUuid("f1")).withUuid("sys"));
        assertThat(fixture.get("System").getUuid()).isEqualTo("sys");
        assertThat(fixture.get("System").getType()).isEqualTo("System");
        assertThat(fixture.get("My Folder").getUuid()).isEqualTo("f1");
        assertThat(fixture.get("Alpha").getUuid()).isEqualTo("a");
        assertThat(fixture.get("Alpha").getRootNodeUuid()).isEqualTo("sys");
        // Numbered as before when not set
        assertThat(fixture.get("Beta").getUuid()).isEqualTo("dictionary-1");
    }

    @Test
    void testToExplorerNodeAndDocRef() {
        final ExplorerFixture fixture = fixture();
        final ExplorerNode node = fixture.toExplorerNode("Ingest");
        assertThat(node.getType()).isEqualTo("Pipeline");
        assertThat(node.getUuid()).isEqualTo("pipeline-1");
        assertThat(node.getName()).isEqualTo("Ingest");
        assertThat(node.getUniqueKey()).isEqualTo(new ExplorerNodeKey("Pipeline", "pipeline-1", "folder-1"));
        assertThat(fixture.toDocRef("Ingest")).isEqualTo(new DocRef("Pipeline", "pipeline-1", "Ingest"));
    }

    @Test
    void testFirstFetchOpensTheRoot() {
        final Map<?, ?> result = fetch(fixture(), "{\"openItems\":[],\"minDepth\":1,\"filter\":{}}");
        final Map<?, ?> system = node(result, 0);
        assertThat(system.get("name")).isEqualTo("System");
        assertThat(system.get("nodeFlags")).isEqualTo(List.of("F"));
        assertThat(names(system.get("children"))).containsExactly("Feeds", "Pipelines");
        // The closed folders' children aren't returned
        assertThat(asMap(asList(system.get("children")).get(0)).get("children")).isNull();
        assertThat(uuids(result.get("openedItems"))).containsExactly("folder-1");
        assertThat(asMap(asList(result.get("openedItems")).get(0)).get("rootNodeUuid")).isEqualTo("folder-1");
    }

    @Test
    void testOpenItemsAreReturnedOpen() {
        final Map<?, ?> result = fetch(fixture(), "{\"openItems\":[{\"type\":\"Folder\",\"uuid\":\"folder-1\"},"
                                                  + "{\"type\":\"Folder\",\"uuid\":\"folder-2\"}],\"minDepth\":0,"
                                                  + "\"filter\":{}}");
        final Map<?, ?> feeds = asMap(asList(node(result, 0).get("children")).get(0));
        assertThat(names(feeds.get("children"))).containsExactly("TEST_FEED", "PROD_FEED");
        assertThat(asMap(asList(feeds.get("children")).get(0)).get("nodeFlags")).isEqualTo(List.of("L"));
        assertThat(uuids(result.get("openedItems"))).isEmpty();
    }

    @Test
    void testOpenToDepth() {
        final Map<?, ?> result = fetch(fixture().openToDepth(2), "{\"minDepth\":1,\"filter\":{}}");
        assertThat(uuids(result.get("openedItems"))).containsExactly("folder-1", "folder-2", "folder-3");
        // Only on the first fetch (minDepth > 0)
        final Map<?, ?> later = fetch(fixture().openToDepth(2), "{\"minDepth\":0,\"filter\":{}}");
        assertThat(uuids(later.get("openedItems"))).isEmpty();
        assertThat(asMap(asList(later.get("rootNodes")).get(0)).get("children")).isNull();
    }

    @Test
    void testEnsureVisibleOpensTheAncestors() {
        final Map<?, ?> result = fetch(fixture(), "{\"minDepth\":0,\"filter\":{},"
                                                  + "\"ensureVisible\":[{\"type\":\"Feed\",\"uuid\":\"feed-2\"}]}");
        assertThat(uuids(result.get("openedItems"))).containsExactly("folder-1", "folder-2");
        final Map<?, ?> feeds = asMap(asList(node(result, 0).get("children")).get(0));
        assertThat(names(feeds.get("children"))).contains("PROD_FEED");
    }

    @Test
    void testIncludedTypes() {
        final Map<?, ?> result = fetch(fixture(), "{\"minDepth\":0,\"openItems\":[{\"uuid\":\"folder-1\"},"
                                                  + "{\"uuid\":\"folder-3\"}],"
                                                  + "\"filter\":{\"includedTypes\":[\"Pipeline\"]}}");
        // Feeds holds no pipelines, so isn't shown
        assertThat(names(node(result, 0).get("children"))).containsExactly("Pipelines");
        final Map<?, ?> pipelines = asMap(asList(node(result, 0).get("children")).get(0));
        assertThat(names(pipelines.get("children"))).containsExactly("Ingest");
    }

    @Test
    void testNameFilterMarksMatchesAndOpensTheirFolders() {
        final Map<?, ?> result = fetch(fixture(), "{\"minDepth\":0,\"filter\":{\"nameFilter\":\" FEED \"}}");
        final Map<?, ?> system = node(result, 0);
        assertThat(system.get("nodeFlags")).isEqualTo(List.of("F", "FN"));
        assertThat(names(system.get("children"))).containsExactly("Feeds");
        final Map<?, ?> feeds = asMap(asList(system.get("children")).get(0));
        assertThat(feeds.get("nodeFlags")).isEqualTo(List.of("F", "FM"));
        assertThat(names(feeds.get("children"))).containsExactly("TEST_FEED", "PROD_FEED");
        assertThat(uuids(result.get("temporaryOpenedItems"))).containsExactly("folder-1", "folder-2");
        assertThat(result.get("qualifiedFilterInput")).isEqualTo("feed");
    }

    @Test
    void testNameFilterWithNoMatches() {
        final Map<?, ?> result = fetch(fixture(), "{\"minDepth\":1,\"filter\":{\"nameFilter\":\"zzz\"}}");
        assertThat(asList(result.get("rootNodes"))).isEmpty();
    }

    @Test
    void testAlerts() {
        final String request = "{\"minDepth\":0,\"showAlerts\":true,\"openItems\":[{\"uuid\":\"folder-1\"},"
                               + "{\"uuid\":\"folder-3\"}],\"filter\":{}}";
        final Map<?, ?> result = fetch(fixture(), request);
        assertThat(node(result, 0).get("nodeFlags")).isEqualTo(List.of("F", "I"));
        final Map<?, ?> pipelines = asMap(asList(node(result, 0).get("children")).get(1));
        final Map<?, ?> xslt = asMap(asList(pipelines.get("children")).get(1));
        assertThat(xslt.get("nodeInfoList"))
                .isEqualTo(List.of(Map.of("severity", "ERROR", "description", "Missing \"index\"")));

        // Not without showAlerts
        final Map<?, ?> without = fetch(fixture(), request.replace("true", "false"));
        assertThat(node(without, 0).get("nodeFlags")).isEqualTo(List.of("F"));
    }

    @Test
    void testGetFromDocRef() {
        final Map<?, ?> node = asMap(JsonValues.parse(fixture().getFromDocRef(
                "{\"type\":\"Pipeline\",\"uuid\":\"pipeline-1\",\"name\":\"Old name\"}")));
        assertThat(node.get("name")).isEqualTo("Ingest");
        assertThat(node.get("rootNodeUuid")).isEqualTo("folder-1");
        assertThat(node.get("nodeFlags")).isEqualTo(List.of("L"));
        assertThat(node.get("children")).isNull();
        assertThat(asMap(node.get("uniqueKey")).get("uuid")).isEqualTo("pipeline-1");

        final Map<?, ?> unknown = asMap(JsonValues.parse(fixture().getFromDocRef(
                "{\"type\":\"Feed\",\"uuid\":\"gone\",\"name\":\"Gone\"}")));
        assertThat(unknown.get("name")).isEqualTo("Gone");
        assertThat(unknown.get("rootNodeUuid")).isNull();
        assertThat(asMap(unknown.get("uniqueKey")).get("type")).isEqualTo("Feed");
    }

    @Test
    void testInvalidRequest() {
        assertThatThrownBy(() -> fixture().fetch("{"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testUnknownName() {
        assertThatThrownBy(() -> fixture().get("Nope"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testQuote() {
        assertThat(ExplorerFixture.quote(null)).isEqualTo("null");
        assertThat(ExplorerFixture.quote("a\"b\\c\n\u0001")).isEqualTo("\"a\\\"b\\\\c\\n\\u0001\"");
    }

    private static Map<?, ?> fetch(final ExplorerFixture fixture, final String request) {
        return asMap(JsonValues.parse(fixture.fetch(request)));
    }

    private static Map<?, ?> node(final Map<?, ?> result, final int index) {
        return asMap(asList(result.get("rootNodes")).get(index));
    }

    private static List<String> names(final Object nodes) {
        final List<String> names = new ArrayList<>();
        for (final Object node : asList(nodes)) {
            names.add((String) asMap(node).get("name"));
        }
        return names;
    }

    private static List<String> uuids(final Object keys) {
        final List<String> uuids = new ArrayList<>();
        for (final Object key : asList(keys)) {
            uuids.add((String) asMap(key).get("uuid"));
        }
        return uuids;
    }

    private static Map<?, ?> asMap(final Object value) {
        return (Map<?, ?>) value;
    }

    private static List<?> asList(final Object value) {
        return (List<?>) value;
    }
}
