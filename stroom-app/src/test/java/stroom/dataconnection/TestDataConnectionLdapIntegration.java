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
import stroom.dataconnection.impl.DataConnectionConfig;
import stroom.dataconnection.impl.DataConnectionStore;
import stroom.dataconnection.impl.DataConnectionTester;
import stroom.dataconnection.shared.DataColumn;
import stroom.dataconnection.shared.DataColumnType;
import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.dataconnection.shared.DataConnectionType;
import stroom.dataconnection.shared.LdapSettings;
import stroom.docref.DocRef;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.DestroyReason;
import stroom.query.api.Param;
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
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.shared.EntityServiceException;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// An LDAP Data Connection queried with StroomQL, through the real store, credentials store and
/// query machinery, against UnboundID's in-memory directory over plain LDAP.
class TestDataConnectionLdapIntegration extends AbstractCoreIntegrationTest {

    private static final String BASE = "dc=example,dc=org";
    private static final String PEOPLE = "ou=People," + BASE;
    private static final String BIND_DN = "cn=Stroom Reader," + BASE;
    private static final String BIND_PASSWORD = "reader-password";
    private static final List<DataColumn> ATTRIBUTES = List.of(
            new DataColumn("uid", DataColumnType.TEXT),
            new DataColumn("cn", DataColumnType.TEXT),
            new DataColumn("departmentNumber", DataColumnType.TEXT),
            new DataColumn("createTimestamp", DataColumnType.DATE));

    private static InMemoryDirectoryServer server;

    @Inject
    private DataConnectionStore store;
    @Inject
    private CredentialsService credentialsService;
    @Inject
    private DataConnectionTester connectionTester;
    @Inject
    private ResultStoreManager resultStoreManager;
    @Inject
    private SearchRequestFactory searchRequestFactory;
    @Inject
    private ExpressionContextFactory expressionContextFactory;

    private String credentialName;

    @BeforeAll
    static void startDirectory() throws Exception {
        final InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(BASE);
        config.addAdditionalBindCredentials(BIND_DN, BIND_PASSWORD);
        config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig(
                "plain", InetAddress.getLoopbackAddress(), 0, null));
        server = new InMemoryDirectoryServer(config);
        server.add("dn: " + BASE, "objectClass: domain", "dc: example");
        server.add("dn: " + PEOPLE, "objectClass: organizationalUnit", "ou: People");
        addPerson("asmith", "Alice Smith", "Ops");
        addPerson("bjones", "Bob Jones", "Dev");
        addPerson("cbrown", "Carol Brown", "Ops");
        server.startListening();
    }

    private static void addPerson(final String uid, final String name, final String department) throws Exception {
        server.add("dn: uid=" + uid + "," + PEOPLE,
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson",
                "uid: " + uid,
                "cn: " + name,
                "sn: " + name.substring(name.indexOf(' ') + 1),
                "departmentNumber: " + department);
    }

    @AfterAll
    static void stopDirectory() {
        server.shutDown(true);
    }

    private static String url() {
        return "ldap://localhost:" + server.getListenPort("plain");
    }

    @BeforeEach
    void setUp() {
        credentialName = "directory-reader-" + UUID.randomUUID();
        credentialsService.storeCredentials(DataConnectionTestDatabase.credential(
                UUID.randomUUID().toString(), credentialName, BIND_DN, BIND_PASSWORD));
        allowLdap(List.of("^ldap://localhost:\\d+$"), true);
    }

    // The LDAP controls an administrator sets, keeping every other setting.
    private void allowLdap(final List<String> allowedLdapUrlPatterns, final boolean allowPlainLdap) {
        setConfigValueMapper(DataConnectionConfig.class, config -> new DataConnectionConfig(
                config.getAllowedUrlPatterns(),
                config.getDeniedConnectionProperties(),
                config.getDriverDirectory(),
                config.getMaxQueryTimeout(),
                config.getMaxRowsLimit(),
                config.getMaxPoolSizeLimit(),
                config.getConnectionPoolCache(),
                config.getFieldCache(),
                config.getAllowedAthenaWorkGroups(),
                config.getAllowedAthenaOutputLocations(),
                config.isAllowDefaultAwsCredentials(),
                config.getMaxStoredDataSize(),
                config.getTableCache(),
                allowedLdapUrlPatterns,
                allowPlainLdap));
    }

    private DataConnectionDoc saveDocument(final UnaryOperator<LdapSettings.Builder> changes) {
        final DocRef docRef = store.createDocument("Staff " + UUID.randomUUID());
        final DataConnectionDoc doc = store.readDocument(docRef);
        return store.writeDocument(doc.copy()
                .sourceType(DataConnectionType.LDAP)
                .ldap(changes.apply(LdapSettings.builder()
                                .url(url())
                                .credentialName(credentialName)
                                .baseDn(PEOPLE)
                                .attributes(ATTRIBUTES))
                        .build())
                .build());
    }

    private SearchResponse search(final String stroomQl, final TimeRange timeRange, final List<Param> params) {
        final SearchRequest sampleRequest = new SearchRequest(
                null,
                null,
                Query.builder()
                        .timeRange(timeRange)
                        .params(params)
                        .build(),
                null,
                DateTimeSettings.builder().build(),
                false);
        final ExpressionContext expressionContext = expressionContextFactory.createContext(sampleRequest);
        final SearchRequest request = searchRequestFactory.create(stroomQl, sampleRequest, expressionContext);
        final SearchResponse response = resultStoreManager.search(request);
        resultStoreManager.destroy(response.getKey(), DestroyReason.NO_LONGER_NEEDED);
        assertThat(response.complete()).as("search is complete").isTrue();
        return response;
    }

    private static List<List<String>> rows(final SearchResponse response, final int selectedFields) {
        assertThat(response.getErrorMessages()).isNullOrEmpty();
        final List<List<String>> rows = new ArrayList<>();
        for (final Result result : response.getResults()) {
            for (final Row row : ((TableResult) result).getRows()) {
                rows.add(row.getValues().subList(0, selectedFields));
            }
        }
        return rows;
    }

    @Test
    void ldapSettingsAreKeptBySaving() {
        final DataConnectionDoc doc = saveDocument(UnaryOperator.identity());

        final DataConnectionDoc read = store.readDocument(doc.asDocRef());

        assertThat(read.getSourceType()).isEqualTo(DataConnectionType.LDAP);
        assertThat(read.getLdap().getAttributes()).isEqualTo(ATTRIBUTES);
    }

    @Test
    void stroomQlSearchesTheDirectoryWithTheFilterInTheSqlClause() {
        final DataConnectionDoc doc = saveDocument(UnaryOperator.identity());

        final SearchResponse response = search("""
                from "%s"
                sql \"""
                (&(objectClass=person)(departmentNumber=Ops))
                \"""
                sort by cn
                select cn, uid, dn""".formatted(doc.getName()), null, null);

        assertThat(rows(response, 3)).containsExactly(
                List.of("Alice Smith", "asmith", "uid=asmith," + PEOPLE),
                List.of("Carol Brown", "cbrown", "uid=cbrown," + PEOPLE));
    }

    @Test
    void stroomClausesWorkOnTheEntries() {
        final DataConnectionDoc doc = saveDocument(UnaryOperator.identity());

        final SearchResponse response = search("""
                from "%s"
                sql \"""
                (objectClass=person)
                \"""
                where cn != "Bob Jones"
                eval people = count()
                group by departmentNumber
                sort by departmentNumber
                select departmentNumber, people""".formatted(doc.getName()), null, null);

        assertThat(rows(response, 2)).containsExactly(List.of("Ops", "2"));
    }

    @Test
    void parameterValueIsMatchedAsText() {
        final DataConnectionDoc doc = saveDocument(UnaryOperator.identity());
        final String stroomQl = """
                from "%s"
                sql \"""
                (uid=:user)
                \"""
                select uid""".formatted(doc.getName());

        assertThat(rows(search(stroomQl, null, List.of(new Param("user", "bjones"))), 1))
                .containsExactly(List.of("bjones"));
        assertThat(rows(search(stroomQl, null, List.of(new Param("user", "*)(uid=*"))), 1))
                .as("an injection attempt finds no one")
                .isEmpty();
    }

    @Test
    void filterTakesTheTimeRange() {
        final DataConnectionDoc doc = saveDocument(UnaryOperator.identity());
        final String stroomQl = """
                from "%s"
                sql \"""
                (&(objectClass=person)(createTimestamp>=:from))
                \"""
                select uid""".formatted(doc.getName());

        assertThat(rows(search(stroomQl, new TimeRange(null, "now() - 1d", "now() + 1d"), null), 1)).hasSize(3);
        assertThat(rows(search(stroomQl, new TimeRange(null, "now() + 1d", "now() + 2d"), null), 1)).isEmpty();
    }

    @Test
    void saveRefusesAUrlNotAllowed() {
        allowLdap(List.of(), true);

        assertThatThrownBy(() -> saveDocument(UnaryOperator.identity()))
                .isInstanceOf(EntityServiceException.class)
                .hasMessageContaining("dataConnection.allowedLdapUrlPatterns");
    }

    @Test
    void saveRefusesPlainLdapUnlessAllowed() {
        allowLdap(List.of("^ldap://localhost:\\d+$"), false);

        assertThatThrownBy(() -> saveDocument(UnaryOperator.identity()))
                .isInstanceOf(EntityServiceException.class)
                .hasMessageContaining("dataConnection.allowPlainLdap");
    }

    @Test
    void detectColumnsListsTheDirectorysAttributes() {
        final DataConnectionDoc doc = saveDocument(settings -> settings.attributes(null));

        assertThat(connectionTester.detectColumns(doc))
                .extracting(DataColumn::getName)
                .contains("uid", "cn", "sn", "departmentNumber", "objectClass");
    }
}
