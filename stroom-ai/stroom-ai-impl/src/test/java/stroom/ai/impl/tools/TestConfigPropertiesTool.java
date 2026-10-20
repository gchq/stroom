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

package stroom.ai.impl.tools;

import stroom.config.global.api.GlobalConfigReader;
import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.OverrideValue;
import stroom.node.api.FindNodeCriteria;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.security.api.UserIdentity;
import stroom.security.shared.AppPermission;
import stroom.task.api.ExecutorProvider;
import stroom.util.shared.PermissionException;
import stroom.util.shared.PropertyPath;

import event.logging.OtherObject;
import event.logging.SearchEventAction;
import event.logging.ViewEventAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class TestConfigPropertiesTool {

    private static final String DEFAULT_MAX_RESULTS = "stroom.ui.defaultMaxResults";
    private static final String STATS_MAX_RESULTS = "stroom.statistics.sql.search.maxResults";
    private static final String MAX_SORTED_ITEMS = "stroom.search.resultStore.maxSortedItems";
    private static final String TRIMMED_SIZE_LIMIT = "stroom.search.resultStore.map.trimmedSizeLimit";
    private static final String MAX_QUERY_ROWS = "stroom.ai.workspace.maxQueryRows";
    private static final String RECORD_LIMIT = "stroom.ui.process.defaultRecordLimit";
    private static final String HTML_TITLE = "stroom.ui.htmlTitle";
    private static final String HELP_URL = "stroom.ui.helpUrl";
    private static final String TOOL_TIME_LIMIT = "stroom.ai.workspace.toolTimeLimit";
    private static final String MAX_STORE_SIZE = "stroom.search.resultStore.lmdb.maxStoreSize";
    private static final String CLIENT_SECRET = "stroom.security.authentication.openId.clientSecret";
    private static final String SMTP_PASSWORD = "stroom.analytics.smtp.password";
    private static final String HEADERS = "stroom.ui.extraHeaders";
    private static final String KAFKA_CONTENT = "stroom.kafka.skeletonConfigContent";
    private static final long SET_AT = Instant.parse("2026-10-01T09:30:00Z").toEpochMilli();
    private static final long FIRST_SET_AT = Instant.parse("2026-09-01T08:00:00Z").toEpochMilli();

    /// Stands in for [ConfigPropertiesTool#HOW_TO_CHANGE] in results.
    private static final String FOOTER = "(how values are set)";

    @TempDir
    Path tempDir;

    private ToolTestFixture fixture;
    private final GlobalConfigReader reader = Mockito.mock(GlobalConfigReader.class);
    private final NodeService nodeService = Mockito.mock(NodeService.class);
    private final NodeInfo nodeInfo = Mockito.mock(NodeInfo.class);
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    /// The properties, by path, as the reader gives them.
    private final Map<String, ConfigProperty> properties = new LinkedHashMap<>();
    /// What each node's YAML holds, by node then path; a node missing here can't be reached.
    private final Map<String, Map<String, OverrideValue<String>>> nodeYaml = new LinkedHashMap<>();
    private ConfigPropertiesTool tool;

    @BeforeEach
    void setUp() {
        fixture = new ToolTestFixture(tempDir);
        Mockito.when(fixture.securityContext.hasAppPermission(AppPermission.MANAGE_PROPERTIES_PERMISSION))
                .thenReturn(true);
        Mockito.when(fixture.securityContext.asUserResult(Mockito.<UserIdentity>any(), Mockito.any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());
        Mockito.when(fixture.workspace.withTimeLimit(Mockito.any(Duration.class), Mockito.any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());
        Mockito.when(nodeInfo.getThisNodeName()).thenReturn("node1");
        Mockito.when(nodeService.findNodeNames(Mockito.any())).thenReturn(List.of("node2", "node1"));
        Mockito.when(reader.list()).thenAnswer(invocation -> new ArrayList<>(properties.values()));
        Mockito.when(reader.fetch(Mockito.any())).thenAnswer(invocation ->
                Optional.ofNullable(properties.get(invocation.<PropertyPath>getArgument(0).toString())));
        Mockito.when(reader.fetchYamlOverrideValue(Mockito.any(), Mockito.anyString())).thenAnswer(invocation -> {
            final String path = invocation.<PropertyPath>getArgument(0).toString();
            final String node = invocation.getArgument(1);
            if ("slow".equals(node)) {
                Thread.sleep(5_000);
            }
            final Map<String, OverrideValue<String>> yaml = nodeYaml.get(node);
            if (yaml == null) {
                throw new RuntimeException("Unable to connect to http://admin:letmein@" + node + ":8080");
            }
            return yaml.getOrDefault(path, OverrideValue.unSet(String.class));
        });
        final ExecutorProvider executorProvider = Mockito.mock(ExecutorProvider.class);
        Mockito.when(executorProvider.get()).thenReturn(executorService);

        add(property(DEFAULT_MAX_RESULTS, "String", "1000000,100,10,1",
                "The default maximum number of search results that new dashboard tables will request."));
        add(property(STATS_MAX_RESULTS, "Integer", "100000",
                "The maximum number of records that can be returned from the statistics DB in a single query "
                + "prior to aggregation")
                .databaseOverrideValue("50000")
                .createUser("alice")
                .createTimeMs(FIRST_SET_AT)
                .updateUser("admin")
                .updateTimeMs(SET_AT));
        add(property(MAX_SORTED_ITEMS, "Integer", "500000",
                "Maximum number of results that can be returned in a single page if the results are sorted. "
                + "This will affect downloading all search results if results are sorted."));
        add(property(TRIMMED_SIZE_LIMIT, "Integer", "500000",
                "The trimmed size of sorted results for on heap result stores."));
        add(property(MAX_QUERY_ROWS, "Integer", "10000",
                "The most rows a StroomQL query run by the agent (run_stroomql) may return."));
        add(property(RECORD_LIMIT, "Integer", "1000000",
                "The default number of records that batch search processing will be limited by."));
        add(property(HTML_TITLE, "String", "Stroom", "The title to use for the application in the browser.")
                .databaseOverrideValue("Database title")
                .yamlOverrideValue("My Stroom"));
        add(property(HELP_URL, "String", "https://bob:hunter2@docs.example.com/help",
                "The URL of the help pages."));
        add(property(TOOL_TIME_LIMIT, "StroomDuration", "PT30S", "How long a tool call may take.")
                .editable(false)
                .requireRestart(true));
        add(property(MAX_STORE_SIZE, "ByteSize", "10GiB", "The maximum size of the LMDB store.")
                .requireUiRestart(true));
        add(property(CLIENT_SECRET, "String", null, "The client secret of the identity provider.")
                .yamlOverrideValue("abc123topsecret"));
        // Not sanitised as the reader would, to show the tool masks it whatever it is given.
        add(property(SMTP_PASSWORD, "String", "defpass", "The SMTP password.")
                .password(true)
                .databaseOverrideValue("dbpass")
                .yamlOverrideValue("yamlpass"));
        add(property(HEADERS, "Map of String, String", "|=user=bob|password=s3cret|host=mail",
                "Extra headers."));
        add(property(KAFKA_CONTENT, "String", "bootstrap.servers=kafka:9092\nssl.key.password=kafkapass\n",
                "The skeleton Kafka config."));

        nodeYaml.put("node1", new LinkedHashMap<>());
        nodeYaml.put("node2", new LinkedHashMap<>(Map.of(
                STATS_MAX_RESULTS, OverrideValue.with("2000"),
                SMTP_PASSWORD, OverrideValue.with("nodepass"))));

        tool = new ConfigPropertiesTool(fixture.workspace, () -> reader, fixture.securityContext,
                () -> nodeService, () -> nodeInfo, executorProvider, fixture.auditLog(), Duration.ofMillis(300));
    }

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
    }

    @Test
    void testWithoutManagePropertiesIsRefusedWithNothingElse() {
        Mockito.when(fixture.securityContext.hasAppPermission(AppPermission.MANAGE_PROPERTIES_PERMISSION))
                .thenReturn(false);

        for (final String args : List.of("{\"mode\":\"search\",\"query\":\"max results\"}",
                "{\"mode\":\"get\",\"paths\":[\"" + STATS_MAX_RESULTS + "\"]}",
                "{\"mode\":\"list\",\"prefix\":\"stroom.ui\"}")) {
            assertThat(run(args)).isEqualTo("Error: " + ConfigPropertiesTool.REFUSED);
        }
        Mockito.verifyNoInteractions(reader, nodeService);
        final List<SearchEventAction> searches = fixture.logged(SearchEventAction.class);
        assertThat(searches).hasSize(3);
        assertThat(searches).allSatisfy(search -> {
            assertThat(search.getOutcome().isSuccess()).isFalse();
            assertThat(search.getOutcome().isPermitted()).isFalse();
        });
    }

    @Test
    void testAFailedPermissionCheckIsRefused() {
        Mockito.when(fixture.securityContext.hasAppPermission(AppPermission.MANAGE_PROPERTIES_PERMISSION))
                .thenThrow(new IllegalStateException("No user"));

        assertThat(run("{\"mode\":\"search\",\"query\":\"max\"}"))
                .isEqualTo("Error: " + ConfigPropertiesTool.REFUSED);
        Mockito.verifyNoInteractions(reader);
    }

    @Test
    void testARefusalByTheConfigServiceIsRefused() {
        Mockito.when(reader.list()).thenThrow(new PermissionException(null, "Not allowed"));

        assertThat(run("{\"mode\":\"list\"}")).isEqualTo("Error: " + ConfigPropertiesTool.REFUSED);
    }

    @Test
    void testABadModeIsAnError() {
        assertThat(run("{\"mode\":\"set\"}")).startsWith("Error: The mode must be");
    }

    @Test
    void testSearchFindsTheResultCapsBestFirst() {
        final String result = run("{\"mode\":\"search\",\"query\":\"max results\"}");

        // Every layer of the search result cap is found.
        assertThat(result).contains(DEFAULT_MAX_RESULTS, STATS_MAX_RESULTS, MAX_SORTED_ITEMS, MAX_QUERY_ROWS,
                RECORD_LIMIT);
        assertThat(result).doesNotContain(HTML_TITLE);
        // Those with both words in their name come first, then those with them elsewhere.
        assertThat(result.indexOf(DEFAULT_MAX_RESULTS)).isLessThan(result.indexOf(MAX_SORTED_ITEMS));
        assertThat(result.indexOf(STATS_MAX_RESULTS)).isLessThan(result.indexOf(MAX_SORTED_ITEMS));
        assertThat(result.indexOf(MAX_SORTED_ITEMS)).isLessThan(result.indexOf(MAX_QUERY_ROWS));
        assertThat(result).contains("- " + DEFAULT_MAX_RESULTS + " = `1000000,100,10,1` (Default) - The default "
                                    + "maximum number of search results");
        assertThat(result).contains("- " + STATS_MAX_RESULTS + " = `50000` (Database; default `100000`)");
        assertThat(result).contains("Values are as on node node1");
        assertThat(result).endsWith("\n\n" + FOOTER);
    }

    @Test
    void testSearchRanksPathWordsAboveDescriptionWords() {
        final String result = run("{\"mode\":\"search\",\"query\":\"sorted\"}");

        assertThat(result).contains(MAX_SORTED_ITEMS, TRIMMED_SIZE_LIMIT);
        assertThat(result.indexOf(MAX_SORTED_ITEMS)).isLessThan(result.indexOf(TRIMMED_SIZE_LIMIT));
    }

    @Test
    void testSearchUnderAPrefix() {
        final String result = run("{\"mode\":\"search\",\"query\":\"max\",\"prefix\":\"search\"}");

        assertThat(result).contains(MAX_SORTED_ITEMS, MAX_STORE_SIZE);
        assertThat(result).doesNotContain(DEFAULT_MAX_RESULTS, STATS_MAX_RESULTS, MAX_QUERY_ROWS);
    }

    @Test
    void testSearchOnlyThoseOverridden() {
        final String result = run("{\"mode\":\"search\",\"overridden\":true}");

        assertThat(result).contains(STATS_MAX_RESULTS, HTML_TITLE, CLIENT_SECRET, SMTP_PASSWORD);
        assertThat(result).doesNotContain(DEFAULT_MAX_RESULTS, MAX_SORTED_ITEMS);
        assertThat(result).contains("- " + HTML_TITLE + " = `My Stroom` (YAML; default `Stroom`)");
    }

    @Test
    void testSearchOnlyThoseDifferingFromTheirDefault() {
        add(property("stroom.ui.welcomeHtml", "String", "Hi", "The welcome message.")
                .databaseOverrideValue("Hi"));

        final String result = run("{\"mode\":\"search\",\"differs_from_default\":true,\"prefix\":\"stroom.ui\"}");

        assertThat(result).contains(HTML_TITLE);
        assertThat(result).doesNotContain("stroom.ui.welcomeHtml", DEFAULT_MAX_RESULTS);
    }

    @Test
    void testSearchSaysWhenItIsCapped() {
        final String result = run("{\"mode\":\"search\",\"query\":\"max results\",\"limit\":2}");

        assertThat(result).contains("more not shown; give a larger limit");
        assertThat(result.lines().filter(line -> line.startsWith("- stroom.")).count()).isEqualTo(2);
    }

    @Test
    void testSearchNeedsSomethingToSearchFor() {
        assertThat(run("{\"mode\":\"search\"}")).startsWith("Error: Give the words to search for");
    }

    @Test
    void testGetGivesTheDatabaseOverrideWithWhoSetItAndWhen() {
        final String result = run("{\"mode\":\"get\",\"paths\":[\"" + STATS_MAX_RESULTS + "\"]}");

        assertThat(result).contains("## " + STATS_MAX_RESULTS);
        assertThat(result).contains("Type: Integer. A whole number.");
        assertThat(result).contains("Default: `100000`");
        assertThat(result).contains("Database value: `50000` (last set by admin at 2026-10-01T09:30:00Z; first set "
                                    + "by alice at 2026-09-01T08:00:00Z)");
        assertThat(result).contains("YAML value on this node (node1): not set");
        assertThat(result).contains("Effective value on this node: `50000`, from the database value");
        assertThat(result).contains("Editable on the Properties screen: yes");
        assertThat(result).contains("A change takes effect: without a restart");
        assertThat(result).endsWith("\n\n" + FOOTER);
    }

    @Test
    void testGetSaysYamlWinsOverTheDatabase() {
        final String result = run("{\"mode\":\"get\",\"paths\":[\"" + HTML_TITLE + "\"]}");

        assertThat(result).contains("Database value: `Database title`");
        assertThat(result).contains("YAML value on this node (node1): `My Stroom`");
        assertThat(result).contains("Effective value on this node: `My Stroom`, from this node's YAML (which takes "
                                    + "precedence over the database value). The database value is not used on "
                                    + "this node");
    }

    @Test
    void testGetGivesRestartFlagsAndWhatValuesMean() {
        final String result = run("{\"mode\":\"get\",\"paths\":[\"" + TOOL_TIME_LIMIT + "\",\"" + MAX_STORE_SIZE
                                  + "\",\"" + DEFAULT_MAX_RESULTS + "\"]}");

        assertThat(result).contains("Type: StroomDuration. A length of time: ISO-8601 (e.g. PT30S is 30 seconds");
        assertThat(result).contains("PT30S is 30 seconds.");
        assertThat(result).contains("Editable on the Properties screen: no (read-only");
        assertThat(result).contains("only after every node is restarted");
        assertThat(result).contains("10GiB is 10,737,418,240 bytes.");
        assertThat(result).contains("only after the nodes serving the UI are restarted");
        assertThat(result).contains("Each number is the most rows kept at a level of grouping");
    }

    @Test
    void testGetGivesSiblingsByPath() {
        final String result = run("{\"mode\":\"get\",\"paths\":[\"stroom.UI.defaultMaxResults\"]}");

        assertThat(result).contains("## " + DEFAULT_MAX_RESULTS);
        assertThat(result).contains("Siblings under stroom.ui: extraHeaders, helpUrl, htmlTitle, process.*");
    }

    @Test
    void testGetSuggestsForAnUnknownPath() {
        final String result = run("{\"mode\":\"get\",\"paths\":[\"stroom.ui.maxResults\"]}");

        assertThat(result).contains("No property stroom.ui.maxResults. Did you mean: " + DEFAULT_MAX_RESULTS);
        assertThat(fixture.logged(ViewEventAction.class)).isEmpty();
    }

    @Test
    void testGetGivesEachNodesYamlAndTheNodesThatCouldNotBeReached() {
        final String result = run("{\"mode\":\"get\",\"paths\":[\"" + STATS_MAX_RESULTS + "\"],"
                                  + "\"nodes\":[\"node1\",\"node2\",\"node3\",\"slow\"]}");

        assertThat(result).contains("- node1: not set; effective there `50000` (Database)");
        assertThat(result).contains("- node2: `2000`; effective there `2000` (YAML)");
        assertThat(result).contains("- node3: couldn't be reached (Unable to connect to http://admin:"
                                    + ConfigValueMasker.MASK + "@node3:8080)");
        assertThat(result).contains("- slow: couldn't be reached (no answer within 300 milliseconds)");
        assertThat(result).doesNotContain("letmein");
    }

    @Test
    void testGetAsksEveryEnabledNode() {
        final String result = run("{\"mode\":\"get\",\"paths\":[\"" + STATS_MAX_RESULTS + "\"],"
                                  + "\"all_nodes\":true}");

        Mockito.verify(nodeService).findNodeNames(Mockito.eq(FindNodeCriteria.allEnabled()));
        assertThat(result.indexOf("- node1:")).isLessThan(result.indexOf("- node2:"));
        final List<ViewEventAction> views = fixture.logged(ViewEventAction.class);
        assertThat(views).hasSize(1);
        assertThat(views.getFirst().getOutcome().getDescription()).contains("with the YAML values of 2 node(s)");
    }

    @Test
    void testGetLimitsTheNodeLookups() {
        final List<String> paths = properties.keySet().stream().limit(10).toList();
        final List<String> nodes = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            nodes.add("node" + i);
        }
        final String result = run("{\"mode\":\"get\",\"paths\":[\"" + String.join("\",\"", paths) + "\"],"
                                  + "\"nodes\":[\"" + String.join("\",\"", nodes) + "\"]}");

        assertThat(result).contains("Not asking 7 nodes for 10 properties' YAML values");
        Mockito.verify(reader, Mockito.never()).fetchYamlOverrideValue(Mockito.any(), Mockito.anyString());
    }

    @Test
    void testPasswordsAreAlwaysMasked() {
        final String get = run("{\"mode\":\"get\",\"paths\":[\"" + SMTP_PASSWORD + "\"],"
                               + "\"nodes\":[\"node1\",\"node2\"]}");
        final String search = run("{\"mode\":\"search\",\"query\":\"password\"}");
        final String list = run("{\"mode\":\"list\",\"prefix\":\"stroom.analytics.smtp\"}");

        for (final String result : List.of(get, search, list)) {
            assertThat(result).doesNotContain("defpass", "dbpass", "yamlpass", "nodepass");
        }
        assertThat(get).contains("Default: " + ConfigValueMasker.MASK + " (masked)");
        assertThat(get).contains("Database value: " + ConfigValueMasker.MASK + " (masked)");
        assertThat(get).contains("YAML value on this node (node1): " + ConfigValueMasker.MASK + " (masked)");
        assertThat(get).contains("Effective value on this node: " + ConfigValueMasker.MASK + " (masked)");
        assertThat(get).contains("- node1: not set; effective there " + ConfigValueMasker.MASK);
        assertThat(get).contains("- node2: " + ConfigValueMasker.MASK + " (masked)");
    }

    @Test
    void testCredentialsInOtherValuesAreMasked() {
        final String result = run("{\"mode\":\"get\",\"paths\":[\"" + CLIENT_SECRET + "\",\"" + HELP_URL + "\",\""
                                  + HEADERS + "\",\"" + KAFKA_CONTENT + "\"]}");
        final String search = run("{\"mode\":\"search\",\"overridden\":true}");

        assertThat(result).doesNotContain("abc123topsecret", "hunter2", "s3cret", "kafkapass");
        assertThat(search).doesNotContain("abc123topsecret");
        // A secret by name is masked though not marked as a password.
        assertThat(result).contains("YAML value on this node (node1): " + ConfigValueMasker.MASK + " (masked)");
        // Only the credentials are masked in the rest.
        assertThat(result).contains("`https://bob:" + ConfigValueMasker.MASK + "@docs.example.com/help`");
        assertThat(result).contains("`|=user=bob|password=" + ConfigValueMasker.MASK + "|host=mail`");
        assertThat(result).contains("bootstrap.servers=kafka:9092\nssl.key.password=" + ConfigValueMasker.MASK);
    }

    @Test
    void testListGivesABranchOfTheTree() {
        final String result = run("{\"mode\":\"list\",\"prefix\":\"stroom.ui.\"}");

        assertThat(result).startsWith("Under stroom.ui: 4 properties and 1 branch.");
        assertThat(result).contains("- " + DEFAULT_MAX_RESULTS + " = `1000000,100,10,1` (Default)");
        assertThat(result).contains("- " + HTML_TITLE + " = `My Stroom` (YAML; default `Stroom`)");
        assertThat(result).contains("- stroom.ui.process (branch: 1 property below; list it to see them)");
        assertThat(result).doesNotContain("- " + RECORD_LIMIT);
        assertThat(result).endsWith("\n\n" + FOOTER);
    }

    @Test
    void testListTheTopOfTheTree() {
        final String result = run("{\"mode\":\"list\"}");

        assertThat(result).startsWith("Under stroom: 0 properties and 7 branches.");
        assertThat(result).contains("- stroom.search (branch: 3 properties below");
    }

    @Test
    void testListSaysWhenItIsCapped() {
        final String result = run("{\"mode\":\"list\",\"prefix\":\"ui\",\"limit\":1}");

        assertThat(result).contains("- " + DEFAULT_MAX_RESULTS);
        assertThat(result).doesNotContain("- " + HTML_TITLE);
        assertThat(result).contains("more not shown");
    }

    @Test
    void testListAnUnknownBranch() {
        assertThat(run("{\"mode\":\"list\",\"prefix\":\"stroom.nothing\"}"))
                .startsWith("Error: There are no properties under stroom.nothing.");
    }

    @Test
    void testCallsAreAudited() {
        run("{\"mode\":\"search\",\"query\":\"max results\",\"limit\":3}");
        run("{\"mode\":\"list\",\"prefix\":\"stroom.ui\"}");
        run("{\"mode\":\"get\",\"paths\":[\"" + STATS_MAX_RESULTS + "\",\"stroom.nope\"]}");

        final List<SearchEventAction> searches = fixture.logged(SearchEventAction.class);
        assertThat(searches).hasSize(2);
        assertThat(searches.getFirst().getQuery().getRaw()).isEqualTo("config_properties search: 'max results'");
        assertThat(searches.getFirst().getResults().getObjects()).hasSize(3);
        assertThat(searches.getFirst().getResults().getObjects()).allSatisfy(object ->
                assertThat(((OtherObject) object).getType()).isEqualTo("ConfigProperty"));
        assertThat(searches.get(1).getQuery().getRaw()).isEqualTo("config_properties list: under stroom.ui");

        final List<ViewEventAction> views = fixture.logged(ViewEventAction.class);
        assertThat(views).hasSize(1);
        assertThat(views.getFirst().getObjects()).hasSize(1);
        final OtherObject viewed = (OtherObject) views.getFirst().getObjects().getFirst();
        assertThat(viewed.getType()).isEqualTo("ConfigProperty");
        assertThat(viewed.getId()).isEqualTo(STATS_MAX_RESULTS);
    }

    @Test
    void testWordsSplitPathsAndPlurals() {
        assertThat(ConfigPropertiesTool.words("stroom.ui.defaultMaxResults"))
                .containsExactly("stroom", "ui", "default", "max", "result");
        assertThat(ConfigPropertiesTool.words("Why are the queries capped?")).containsExactly("query", "capped");
    }

    @Test
    void testNormalisePrefix() {
        assertThat(ConfigPropertiesTool.normalisePrefix("ui.")).isEqualTo("stroom.ui");
        assertThat(ConfigPropertiesTool.normalisePrefix("stroom.search")).isEqualTo("stroom.search");
        assertThat(ConfigPropertiesTool.normalisePrefix("stroom")).isEqualTo("stroom");
        assertThat(ConfigPropertiesTool.normalisePrefix(" ")).isNull();
    }

    /// @return What the tool gives, without the note on how values are set that ends each result (which names a
    /// property as an example).
    private String run(final String arguments) {
        final String result = tool.execute(ToolTestFixture.request(ConfigPropertiesTool.NAME, arguments), 1);
        if (result.endsWith(ConfigPropertiesTool.HOW_TO_CHANGE)) {
            return result.substring(0, result.length() - ConfigPropertiesTool.HOW_TO_CHANGE.length()) + FOOTER;
        }
        return result;
    }

    private void add(final ConfigProperty.Builder builder) {
        final ConfigProperty property = builder.build();
        properties.put(property.getNameAsString(), property);
    }

    private static ConfigProperty.Builder property(final String path,
                                                   final String type,
                                                   final String defaultValue,
                                                   final String description) {
        return ConfigProperty.builder()
                .name(path)
                .dataTypeName(type)
                .defaultValue(defaultValue)
                .description(description)
                .editable(true);
    }
}
