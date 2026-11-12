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

package stroom.dataconnection;

import stroom.credentials.impl.CredentialsService;
import stroom.data.shared.StreamTypeNames;
import stroom.dataconnection.impl.DataConnectionConfig;
import stroom.dataconnection.impl.DataConnectionStore;
import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.ExplorerNode;
import stroom.importexport.api.ImportExportSerializer;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportSettings.ImportMode;
import stroom.node.api.NodeInfo;
import stroom.processor.impl.ProcessorTaskTestHelper;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.DestroyReason;
import stroom.query.api.Query;
import stroom.query.api.Result;
import stroom.query.api.Row;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchResponse;
import stroom.query.api.TableResult;
import stroom.query.api.TimeRange;
import stroom.query.common.v2.ExpressionContextFactory;
import stroom.query.common.v2.ResultStoreManager;
import stroom.query.language.SearchRequestFactory;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.shared.QueryDoc;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestScenarioCreator;
import stroom.test.common.ProjectPathUtil;
import stroom.test.common.util.db.DbTestUtil;
import stroom.util.json.JsonUtil;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldif.LDIFException;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/// The example pack in the module's folder, imported as a user would, with every query in it run
/// as the Query pane runs it, with the query's own time range.
///
/// The "Stroom DB" source is set up as its documentation tells the user to: an account given only
/// the grants the documentation lists, a credential named "Stroom DB Reader" for it, and its URL
/// allowed. Its URL is then pointed at the test database, which is given a node record, two feeds of
/// streams and a processor so the queries have rows.
///
/// The "Directory" source is set up the same way, with a credential named "Directory Reader" and its
/// URL allowed, against UnboundID's in-memory directory of three people. Plain LDAP is allowed for it,
/// as the notes say only a test directory should be.
class TestDataConnectionExamplePack extends AbstractCoreIntegrationTest {

    private static final String PACK = "DataConnection-Examples.zip";
    private static final DocRef STROOM_DB = DataConnectionDoc.buildDocRef()
            .uuid("5d6f1d0e-2b0a-4c3e-9a51-6d2a0c0f6a02")
            .build();
    private static final String CREDENTIAL_NAME = "Stroom DB Reader";
    private static final String BUSY_FEED = "PACK_BUSY_FEED";
    private static final String QUIET_FEED = "PACK_QUIET_FEED";
    private static final String READER_USER = "pack_test_reader";
    private static final String READER_PASSWORD = "pack_test_reader_pw";
    // A grant in the source's set-up notes, e.g. "GRANT SELECT ON stroom.meta TO ...".
    private static final Pattern DOCUMENTED_GRANT = Pattern.compile("GRANT SELECT ON stroom\\.(\\w+) TO");
    private static final DocRef DIRECTORY = DataConnectionDoc.buildDocRef()
            .uuid("5d6f1d0e-2b0a-4c3e-9a51-6d2a0c0f6a05")
            .build();
    private static final String DIRECTORY_CREDENTIAL_NAME = "Directory Reader";
    private static final String DIRECTORY_BASE = "dc=example,dc=org";
    private static final String DIRECTORY_PEOPLE = "ou=People," + DIRECTORY_BASE;
    private static final String BIND_DN = "cn=stroom-reader,ou=Services," + DIRECTORY_BASE;
    private static final String BIND_PASSWORD = "reader-password";

    private static InMemoryDirectoryServer directory;

    @Inject
    private ImportExportSerializer importExportSerializer;
    @Inject
    private ExplorerNodeService explorerNodeService;
    @Inject
    private DataConnectionStore dataConnectionStore;
    @Inject
    private CredentialsService credentialsService;
    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private ProcessorTaskTestHelper processorTaskTestHelper;
    @Inject
    private NodeInfo nodeInfo;
    @Inject
    private SearchRequestFactory searchRequestFactory;
    @Inject
    private ExpressionContextFactory expressionContextFactory;
    @Inject
    private ResultStoreManager resultStoreManager;

    @TempDir
    private Path unpacked;

    private Map<String, QueryDoc> queries;

    /// Starts a directory of three people, two in Ops and one in Dev.
    ///
    /// @throws LDAPException If the directory cannot be started.
    /// @throws LDIFException If an entry cannot be written.
    @BeforeAll
    static void startDirectory() throws LDAPException, LDIFException {
        final InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(DIRECTORY_BASE);
        config.addAdditionalBindCredentials(BIND_DN, BIND_PASSWORD);
        config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig(
                "plain", InetAddress.getLoopbackAddress(), 0, null));
        directory = new InMemoryDirectoryServer(config);
        directory.add("dn: " + DIRECTORY_BASE, "objectClass: domain", "dc: example");
        directory.add("dn: " + DIRECTORY_PEOPLE, "objectClass: organizationalUnit", "ou: People");
        addPerson("asmith", "Alice Smith", "Ops");
        addPerson("bjones", "Bob Jones", "Dev");
        addPerson("cbrown", "Carol Brown", "Ops");
        directory.startListening();
    }

    private static void addPerson(final String uid, final String name, final String department)
            throws LDAPException, LDIFException {
        directory.add("dn: uid=" + uid + "," + DIRECTORY_PEOPLE,
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson",
                "uid: " + uid,
                "cn: " + name,
                "sn: " + name.substring(name.indexOf(' ') + 1),
                "mail: " + uid + "@example.org",
                "departmentNumber: " + department);
    }

    /// Stops the directory.
    @AfterAll
    static void stopDirectory() {
        directory.shutDown(true);
    }

    /// Unpacks and imports the pack, sets up its "Stroom DB" source against the test database, and
    /// adds the streams and processor tasks the database queries count.
    ///
    /// @throws IOException  If the pack cannot be read.
    /// @throws SQLException If the test database cannot be set up.
    @BeforeEach
    void importPack() throws IOException, SQLException {
        final Path pack = ProjectPathUtil.resolveDir("stroom-data-connection").resolve(PACK);
        queries = unpack(pack, unpacked);

        final ExplorerNode root = explorerNodeService.getRoot();
        importExportSerializer.read(unpacked, null, ImportSettings.builder()
                .importMode(ImportMode.IGNORE_CONFIRMATION)
                .useImportFolders(true)
                .useImportNames(true)
                .rootDocRef(root.getDocRef())
                .build());

        setUpStroomDb();
        setUpDirectory();
        addThisNode();
        addStreamsAndTasks();
    }

    // Unpacks the zip, returning each query by name.
    private static Map<String, QueryDoc> unpack(final Path pack, final Path dir) throws IOException {
        final Map<String, QueryDoc> queries = new LinkedHashMap<>();
        try (final ZipInputStream zip = new ZipInputStream(Files.newInputStream(pack))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                final Path file = dir.resolve(entry.getName()).normalize();
                assertThat(file).startsWithRaw(dir);
                Files.createDirectories(file.getParent());
                final byte[] bytes = zip.readAllBytes();
                Files.write(file, bytes);
                if (entry.getName().contains(".Query.") && entry.getName().endsWith(".meta")) {
                    final QueryDoc queryDoc = JsonUtil.readValue(bytes, QueryDoc.class);
                    queries.put(queryDoc.getName(), queryDoc);
                }
            }
        }
        return queries;
    }

    // Follows the source's set-up notes, with the test database in place of the user's: an account
    // given only the grants the notes list, a credential for it and the URL allowed.
    private void setUpStroomDb() throws SQLException {
        final DataConnectionDoc doc = dataConnectionStore.readDocument(STROOM_DB);
        final List<String> tables = documentedTables(doc.getDescription());
        assertThat(tables).isNotEmpty();

        final String url;
        final String dbName;
        try (final Connection connection = DbTestUtil.createTestDataSource().getConnection()) {
            url = connection.getMetaData().getURL();
            dbName = connection.getCatalog();
        }
        DbTestUtil.doWithRootConnection(connection -> {
            try (final Statement statement = connection.createStatement()) {
                statement.execute("CREATE USER IF NOT EXISTS '" + READER_USER + "'@'%' IDENTIFIED BY '"
                                  + READER_PASSWORD + "'");
                for (final String table : tables) {
                    statement.execute("GRANT SELECT ON `" + dbName + "`.`" + table + "` TO '"
                                      + READER_USER + "'@'%'");
                }
            } catch (final SQLException e) {
                throw new RuntimeException(e);
            }
        });

        credentialsService.storeCredentials(DataConnectionTestDatabase.credential(
                UUID.randomUUID().toString(), CREDENTIAL_NAME, READER_USER, READER_PASSWORD));
        setConfigValueMapper(DataConnectionConfig.class, config -> allowing(config, url));

        assertThat(doc.getJdbc().getCredentialName()).isEqualTo(CREDENTIAL_NAME);
        dataConnectionStore.writeDocument(doc.copy()
                .jdbc(doc.getJdbc().copy().url(url).build())
                .build());
    }

    // Allows only the test database's JDBC URL and the test directory, over plain LDAP.
    private static DataConnectionConfig allowing(final DataConnectionConfig config, final String jdbcUrl) {
        final DataConnectionConfig jdbcAllowed = DataConnectionTestDatabase.allowingOnly(config, jdbcUrl);
        return new DataConnectionConfig(
                jdbcAllowed.getAllowedUrlPatterns(),
                jdbcAllowed.getDeniedConnectionProperties(),
                jdbcAllowed.getDriverDirectory(),
                jdbcAllowed.getMaxQueryTimeout(),
                jdbcAllowed.getMaxRowsLimit(),
                jdbcAllowed.getMaxPoolSizeLimit(),
                jdbcAllowed.getConnectionPoolCache(),
                jdbcAllowed.getFieldCache(),
                jdbcAllowed.getAllowedAthenaWorkGroups(),
                jdbcAllowed.getAllowedAthenaOutputLocations(),
                jdbcAllowed.isAllowDefaultAwsCredentials(),
                jdbcAllowed.getMaxStoredDataSize(),
                jdbcAllowed.getTableCache(),
                List.of("^ldap://localhost:\\d+$"),
                true);
    }

    // Follows the Directory source's set-up notes, with the test directory in place of the user's.
    private void setUpDirectory() {
        credentialsService.storeCredentials(DataConnectionTestDatabase.credential(
                UUID.randomUUID().toString(), DIRECTORY_CREDENTIAL_NAME, BIND_DN, BIND_PASSWORD));
        final DataConnectionDoc doc = dataConnectionStore.readDocument(DIRECTORY);
        assertThat(doc.getLdap().getCredentialName()).isEqualTo(DIRECTORY_CREDENTIAL_NAME);
        assertThat(doc.getLdap().getBaseDn()).isEqualTo(DIRECTORY_PEOPLE);
        dataConnectionStore.writeDocument(doc.copy()
                .ldap(doc.getLdap().copy()
                        .url("ldap://localhost:" + directory.getListenPort("plain"))
                        .build())
                .build());
    }

    // The tables the source's set-up notes grant the reader account.
    private static List<String> documentedTables(final String description) {
        final List<String> tables = new ArrayList<>();
        final Matcher matcher = DOCUMENTED_GRANT.matcher(description);
        while (matcher.find()) {
            tables.add(matcher.group(1));
        }
        return tables;
    }

    // The test database is a fresh one, without the node record a running Stroom has for itself.
    private void addThisNode() throws SQLException {
        final long now = System.currentTimeMillis();
        try (final Connection connection = DbTestUtil.createTestDataSource().getConnection();
                final PreparedStatement statement = connection.prepareStatement(
                        "INSERT IGNORE INTO node (version, create_time_ms, create_user, update_time_ms, "
                        + "update_user, url, name, priority, enabled, build_version, last_boot_ms) "
                        + "VALUES (1, ?, 'test', ?, 'test', 'http://localhost:8080', ?, 1, 1, 'SNAPSHOT', ?)")) {
            statement.setLong(1, now);
            statement.setLong(2, now);
            statement.setString(3, nodeInfo.getThisNodeName());
            statement.setLong(4, now);
            statement.executeUpdate();
        }
    }

    // Two raw streams on the busy feed, which a processor makes tasks for, and one on the quiet feed.
    private void addStreamsAndTasks() {
        commonTestScenarioCreator.createSample2LineRawFile(BUSY_FEED, StreamTypeNames.RAW_EVENTS);
        commonTestScenarioCreator.createSample2LineRawFile(BUSY_FEED, StreamTypeNames.RAW_EVENTS);
        commonTestScenarioCreator.createSample2LineRawFile(QUIET_FEED, StreamTypeNames.RAW_EVENTS);
        commonTestScenarioCreator.createBasicTranslateStreamProcessor(BUSY_FEED);
        processorTaskTestHelper.createAndQueueTasks();
    }

    private SearchRequest searchRequest(final String stroomQl, final TimeRange timeRange) {
        final SearchRequest sampleRequest = new SearchRequest(
                null,
                null,
                Query.builder().timeRange(timeRange).build(),
                null,
                DateTimeSettings.builder().build(),
                false);
        final ExpressionContext expressionContext = expressionContextFactory.createContext(sampleRequest);
        return searchRequestFactory.create(stroomQl, sampleRequest, expressionContext);
    }

    // Runs the named query with its own time range, as the Query pane does, and returns the
    // selected fields of each row.
    private List<List<String>> run(final String queryName, final int fields) {
        final QueryDoc queryDoc = queries.get(queryName);
        assertThat(queryDoc).as(queryName).isNotNull();
        final SearchResponse response = resultStoreManager.search(
                searchRequest(queryDoc.getQuery(), queryDoc.getTimeRange()));
        resultStoreManager.destroy(response.getKey(), DestroyReason.NO_LONGER_NEEDED);
        assertThat(response.complete()).as(queryName).isTrue();
        assertThat(response.getErrorMessages()).as(queryName).isNullOrEmpty();
        final List<List<String>> rows = new ArrayList<>();
        for (final Result result : response.getResults()) {
            for (final Row row : ((TableResult) result).getRows()) {
                rows.add(row.getValues().subList(0, fields));
            }
        }
        return rows;
    }

    @Test
    void everyQueryIsValidStroomQl() {
        assertThat(queries).hasSize(15);
        queries.forEach((name, queryDoc) ->
                assertThatCode(() -> searchRequest(queryDoc.getQuery(), queryDoc.getTimeRange()))
                        .as(name)
                        .doesNotThrowAnyException());
    }

    @Test
    void teamsOnCallQueryGivesTheTeamsOnCall() {
        assertThat(run("Teams on call", 2)).containsExactly(
                List.of("Platform", "6"),
                List.of("Security", "5"),
                List.of("Support", "3"));
    }

    @Test
    void leftJoinGivesEveryPersonWithTheirTeam() {
        assertThat(blankForNull(run("Join - people with their team", 4))).containsExactly(
                List.of("Alice", "Platform", "6", "true"),
                List.of("Ben", "Data", "4", "false"),
                List.of("Chloe", "Security", "5", "true"),
                List.of("Dev", "Analysis", "8", "false"),
                List.of("Ella", "Platform", "6", "true"),
                List.of("Femi", "Design", "", ""),
                List.of("Gus", "Support", "3", "true"));
    }

    @Test
    void keepJoinGivesPeopleInOnCallTeams() {
        assertThat(run("Join - people in on-call teams", 2)).containsExactly(
                List.of("Alice", "Platform"),
                List.of("Chloe", "Security"),
                List.of("Ella", "Platform"),
                List.of("Gus", "Support"));
    }

    @Test
    void dropJoinGivesPeopleInNoKnownTeam() {
        assertThat(run("Join - people in no known team", 2)).containsExactly(List.of("Femi", "Design"));
    }

    @Test
    void joinToABracketedQueryComparesTeamSizesWithPeopleListed() {
        assertThat(run("Join - team sizes against people listed", 3)).containsExactly(
                List.of("Analysis", "8", "1"),
                List.of("Data", "4", "1"),
                List.of("Platform", "6", "2"),
                List.of("Security", "5", "1"),
                List.of("Support", "3", "1"));
    }

    @Test
    void joinOnADateFindsPeopleWhoStartedOnABankHoliday() {
        final List<List<String>> rows = run("Join - people who started on a bank holiday", 3);

        assertThat(rows).extracting(row -> row.get(0)).containsExactly("Ben", "Dev");
        assertThat(rows).extracting(row -> row.get(2)).containsExactly("Good Friday", "Spring bank holiday");
        assertThat(rows.get(0).get(1)).startsWith("2026-04-03");
        assertThat(rows.get(1).get(1)).startsWith("2026-05-25");
    }

    // The rows with each value that is missing as blank.
    private static List<List<String>> blankForNull(final List<List<String>> rows) {
        return rows.stream()
                .map(row -> row.stream()
                        .map(value -> value == null
                                ? ""
                                : value)
                        .toList())
                .toList();
    }

    @Test
    void bankHolidaysQueryGivesScotlandsHolidaysInOrder() {
        assertThat(run("Bank holidays in Scotland", 2))
                .extracting(row -> row.get(1))
                .containsExactly(
                        "New Year's Day",
                        "Good Friday",
                        "Early May bank holiday",
                        "Spring bank holiday",
                        "St Andrew's Day",
                        "Christmas Day",
                        "Boxing Day");
    }

    @Test
    void nodesQueryListsThisNode() {
        final List<List<String>> rows = run("Nodes", 6);
        assertThat(rows)
                .extracting(List::getFirst)
                .contains(nodeInfo.getThisNodeName());
    }

    @Test
    void streamsByFeedAndTypeQueryCountsTheStreams() {
        assertThat(run("Streams by feed and type", 3)).containsExactly(
                List.of(BUSY_FEED, StreamTypeNames.RAW_EVENTS, "2"),
                List.of(QUIET_FEED, StreamTypeNames.RAW_EVENTS, "1"));
    }

    @Test
    void streamsCreatedPerDayQueryCountsTodaysStreamsAndFeeds() {
        final List<List<String>> rows = run("Streams created per day", 3);
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getFirst()).as("day").isNotBlank();
        assertThat(rows.getFirst().subList(1, 3)).containsExactly("3", "2");
    }

    @Test
    void biggestFeedsQueryGivesTheBusiestFeedFirst() {
        final List<List<String>> rows = run("Biggest feeds counted in the database", 4);
        assertThat(rows)
                .extracting(row -> row.subList(0, 2))
                .containsExactly(
                        List.of(BUSY_FEED, "2"),
                        List.of(QUIET_FEED, "1"));
        assertThat(rows).allSatisfy(row -> assertThat(row.subList(2, 4))
                .as("first and last stream")
                .allSatisfy(time -> assertThat(time).isNotBlank()));
    }

    @Test
    void peopleQueryListsEveryoneInTheDirectory() {
        assertThat(run("People in the directory", 4)).containsExactly(
                List.of("Alice Smith", "asmith", "asmith@example.org", "Ops"),
                List.of("Bob Jones", "bjones", "bjones@example.org", "Dev"),
                List.of("Carol Brown", "cbrown", "cbrown@example.org", "Ops"));
    }

    @Test
    void departmentQueryCountsThePeopleInEach() {
        assertThat(run("People by department", 2)).containsExactly(
                List.of("Ops", "2"),
                List.of("Dev", "1"));
    }

    @Test
    void recentQueryFindsPeopleAddedInItsTimeRange() {
        assertThat(run("People added recently", 2))
                .extracting(row -> row.get(1))
                .as("everyone was added when the test started")
                .containsExactlyInAnyOrder("Alice Smith", "Bob Jones", "Carol Brown");
    }

    @Test
    void processorTasksQueryCountsTheTasksOfTheProcessedFeed() {
        final List<List<String>> rows = run("Processor tasks by status", 3);
        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.get(0)).as("feed").isEqualTo(BUSY_FEED);
            assertThat(row.get(1)).as("status").isNotBlank();
        });
        assertThat(rows.stream().mapToLong(row -> Long.parseLong(row.get(2))).sum())
                .as("tasks")
                .isEqualTo(2);
    }
}
