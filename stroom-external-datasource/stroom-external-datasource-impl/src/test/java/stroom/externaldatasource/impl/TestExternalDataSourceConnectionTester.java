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

package stroom.externaldatasource.impl;

import stroom.credentials.api.StoredSecrets;
import stroom.externaldatasource.shared.AthenaConnectionSettings;
import stroom.externaldatasource.shared.DataColumn;
import stroom.externaldatasource.shared.DataColumnType;
import stroom.externaldatasource.shared.ExcelSettings;
import stroom.externaldatasource.shared.ExternalDataSourceDoc;
import stroom.externaldatasource.shared.ExternalDataSourceTestResponse;
import stroom.externaldatasource.shared.ExternalDataSourceType;
import stroom.externaldatasource.shared.JdbcConnectionSettings;
import stroom.externaldatasource.shared.LdapSettings;
import stroom.security.api.SecurityContext;
import stroom.security.mock.MockSecurityContext;
import stroom.util.io.PathCreator;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResourceKey;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.athena.model.GetDatabaseResponse;
import software.amazon.awssdk.services.athena.model.GetWorkGroupResponse;
import software.amazon.awssdk.services.athena.model.WorkGroup;
import software.amazon.awssdk.services.athena.model.WorkGroupConfiguration;
import software.amazon.awssdk.services.athena.model.WorkGroupState;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestExternalDataSourceConnectionTester {

    private FakeJdbcDriver driver;

    @BeforeEach
    void setUp() throws SQLException {
        driver = FakeJdbcDriver.register();
    }

    @AfterEach
    void tearDown() throws SQLException {
        driver.deregister();
    }

    private final AthenaClient athenaClient = Mockito.mock(AthenaClient.class);
    private final ExcelUploads excelUploads = Mockito.mock(ExcelUploads.class);
    private final ExternalDataSourceStore store = Mockito.mock(ExternalDataSourceStore.class);

    private ExternalDataSourceConnectionTester tester(final SecurityContext securityContext) {
        final ExternalDataSourceConfig config = new ExternalDataSourceConfig(
                List.of(FakeJdbcDriver.ALLOWED_URL_PATTERN), null, null, null, null, null, null, null,
                List.of("^eu-west-2/primary$"), null, true, null, null);
        final StoredSecrets storedSecrets = Mockito.mock(StoredSecrets.class);
        final JdbcConnectionResolver resolver = new JdbcConnectionResolver(() -> config, () -> storedSecrets);
        final JdbcDriverLoader driverLoader = new JdbcDriverLoader(() -> config, Mockito.mock(PathCreator.class));
        final AthenaConnectionResolver athenaResolver = new AthenaConnectionResolver(
                () -> config,
                () -> storedSecrets);
        final SqlFieldCache fieldCache = Mockito.mock(SqlFieldCache.class);
        final LdapConnector ldapConnector = new LdapConnector(
                new LdapConnectionResolver(() -> config, () -> storedSecrets),
                new LdapConnectionFactory(),
                null,
                () -> config);
        return new ExternalDataSourceConnectionTester(
                () -> securityContext,
                new Connectors(
                        new JdbcConnector(null, resolver, driverLoader, fieldCache),
                        new AthenaConnector(null, athenaResolver, connection -> athenaClient, fieldCache),
                        new CsvConnector(null, () -> config),
                        new ExcelConnector(null, () -> config),
                        ldapConnector),
                () -> config,
                excelUploads,
                () -> store,
                ldapConnector);
    }

    private static ExternalDataSourceDoc athenaDoc(final String region) {
        return ExternalDataSourceDoc.builder()
                .uuid("uuid-1")
                .name("Events")
                .sourceType(ExternalDataSourceType.ATHENA)
                .athena(AthenaConnectionSettings.builder()
                        .region(region)
                        .database("events")
                        .build())
                .build();
    }

    @SuppressWarnings("unchecked")
    private void givenWorkGroup(final Long scanLimit) {
        Mockito.when(athenaClient.getWorkGroup(Mockito.any(Consumer.class))).thenReturn(GetWorkGroupResponse.builder()
                .workGroup(WorkGroup.builder()
                        .name("primary")
                        .state(WorkGroupState.ENABLED)
                        .configuration(WorkGroupConfiguration.builder()
                                .bytesScannedCutoffPerQuery(scanLimit)
                                .enforceWorkGroupConfiguration(false)
                                .resultConfiguration(result -> result.outputLocation("s3://results/stroom/"))
                                .build())
                        .build())
                .build());
        Mockito.when(athenaClient.getDatabase(Mockito.any(Consumer.class))).thenReturn(GetDatabaseResponse.builder()
                .database(database -> database.name("events"))
                .build());
    }

    private static ExternalDataSourceDoc doc(final String url) {
        return ExternalDataSourceDoc.builder()
                .uuid("uuid-1")
                .name("HR")
                .jdbc(JdbcConnectionSettings.builder()
                        .url(url)
                        .build())
                .build();
    }

    @Test
    void successfulTestReportsTheDatabaseAndAdvisesReadOnly() {
        final ExternalDataSourceTestResponse response = tester(new MockSecurityContext()).test(doc(FakeJdbcDriver.URL));

        assertThat(response.isOk()).isTrue();
        assertThat(response.getMessage())
                .contains("FakeDB 1.2.3")
                .contains("Fake Driver 4.5")
                .contains(JdbcConnector.READ_ONLY_ADVICE);
    }

    @Test
    void testConnectionsAreReadOnlyAndClosed() throws SQLException {
        tester(new MockSecurityContext()).test(doc(FakeJdbcDriver.URL));

        assertThat(driver.getConnections()).isNotEmpty();
        for (final Connection connection : driver.getConnections()) {
            Mockito.verify(connection).setReadOnly(true);
            Mockito.verify(connection).close();
        }
    }

    @Test
    void urlNotAllowedFailsWithoutConnecting() {
        final ExternalDataSourceTestResponse response = tester(new MockSecurityContext())
                .test(doc("jdbc:mysql://elsewhere:3306/hr"));

        assertThat(response.isOk()).isFalse();
        assertThat(response.getMessage()).contains("allowedUrlPatterns");
        assertThat(driver.getConnections()).isEmpty();
    }

    @Test
    void missingUrlFails() {
        final ExternalDataSourceTestResponse response = tester(new MockSecurityContext())
                .test(ExternalDataSourceDoc.builder()
                        .uuid("uuid-1")
                        .build());

        assertThat(response.isOk()).isFalse();
        assertThat(response.getMessage()).contains("No JDBC URL");
    }

    @Test
    @SuppressWarnings("unchecked")
    void athenaTestLooksUpTheWorkGroupAndDatabaseWithoutRunningAQuery() {
        // Every Athena query is billed, so the test must not run one.
        givenWorkGroup(1_000_000_000L);

        final ExternalDataSourceTestResponse response = tester(new MockSecurityContext()).test(athenaDoc("eu-west-2"));

        assertThat(response.isOk()).isTrue();
        assertThat(response.getMessage())
                .contains("Amazon Athena in eu-west-2")
                .contains("Workgroup: primary (ENABLED)")
                .contains("Workgroup result location: s3://results/stroom/")
                .contains("Database: AwsDataCatalog.events")
                .contains("Stroom's own AWS identity")
                .contains("Workgroup data scan limit per query: 1.0G")
                .contains("Document data scan limit: none set")
                .contains("Workgroup enforces its settings: no, so the document's output location is used")
                .contains(AthenaConnector.ADVICE);
        Mockito.verify(athenaClient, Mockito.never()).startQueryExecution(Mockito.any(Consumer.class));
        Mockito.verify(athenaClient).close();
    }

    @Test
    void athenaTestPointsOutAMissingScanLimit() {
        givenWorkGroup(null);

        final ExternalDataSourceTestResponse response = tester(new MockSecurityContext()).test(athenaDoc("eu-west-2"));

        assertThat(response.getMessage())
                .contains("Workgroup data scan limit per query: none set")
                .contains("Document data scan limit: none set");
    }

    @Test
    void athenaTestReportsTheDocumentsScanLimit() {
        givenWorkGroup(null);
        final ExternalDataSourceDoc doc = athenaDoc("eu-west-2");
        final ExternalDataSourceDoc withLimit = doc.copy()
                .athena(doc.getAthena().copy()
                        .maxDataScannedMb(500)
                        .build())
                .build();

        final ExternalDataSourceTestResponse response = tester(new MockSecurityContext()).test(withLimit);

        assertThat(response.getMessage()).contains("Document data scan limit: 500 MB, checked while a query runs");
    }

    @Test
    void athenaWorkGroupNotAllowedFailsWithoutCallingAws() {
        final ExternalDataSourceTestResponse response = tester(new MockSecurityContext()).test(athenaDoc("us-east-1"));

        assertThat(response.isOk()).isFalse();
        assertThat(response.getMessage()).contains("allowedAthenaWorkGroups");
        Mockito.verifyNoInteractions(athenaClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void athenaErrorIsReported() {
        Mockito.when(athenaClient.getWorkGroup(Mockito.any(Consumer.class)))
                .thenThrow(new IllegalStateException("The security token included in the request is invalid."));

        final ExternalDataSourceTestResponse response = tester(new MockSecurityContext()).test(athenaDoc("eu-west-2"));

        assertThat(response.isOk()).isFalse();
        assertThat(response.getMessage()).contains("security token");
    }

    @Test
    void userWhoCannotEditIsRefused() {
        // Otherwise a user could send a credential they can use to a server of their choosing.
        final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        Mockito.when(securityContext.hasDocumentPermission(Mockito.any(), Mockito.any())).thenReturn(false);

        assertThatThrownBy(() -> tester(securityContext).test(doc(FakeJdbcDriver.URL)))
                .isInstanceOf(PermissionException.class);
        assertThat(driver.getConnections()).isEmpty();
    }

    private static ExternalDataSourceDoc ldapDoc() {
        return ExternalDataSourceDoc.builder()
                .uuid("uuid-1")
                .name("Staff")
                .sourceType(ExternalDataSourceType.LDAP)
                .ldap(LdapSettings.builder()
                        .url("ldaps://directory.example.org:636")
                        .credentialName("directory-reader")
                        .baseDn("ou=People,dc=example,dc=org")
                        .build())
                .build();
    }

    @Test
    void ldapAttributeDetectionNeedsEditPermission() {
        // Detecting searches the directory with the credential the document names.
        final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        Mockito.when(securityContext.hasDocumentPermission(Mockito.any(), Mockito.any())).thenReturn(false);

        assertThatThrownBy(() -> tester(securityContext).detectColumns(ldapDoc()))
                .isInstanceOf(PermissionException.class);
    }

    @Test
    void ldapAttributeDetectionChecksTheSettingsFirst() {
        // No LDAP URL is allowed by the configuration here, so nothing is contacted.
        assertThatThrownBy(() -> tester(new MockSecurityContext()).detectColumns(ldapDoc()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("externalDataSource.allowedLdapUrlPatterns");
    }

    @Test
    void ldapTestOfAUrlNotAllowedFails() {
        final ExternalDataSourceTestResponse response = tester(new MockSecurityContext()).test(ldapDoc());

        assertThat(response.isOk()).isFalse();
        assertThat(response.getMessage()).contains("externalDataSource.allowedLdapUrlPatterns");
    }

    @Test
    void noDocumentIsRefused() {
        assertThatThrownBy(() -> tester(new MockSecurityContext()).test(null))
                .isInstanceOf(EntityServiceException.class);
    }

    private static final ResourceKey UPLOAD = new ResourceKey("upload-1", "teams.xlsx");
    private static final byte[] TEAMS = TestExcelReader.xlsx(new Object[][]{
            {"team", "members"},
            {"Red", 4},
            {"Blue", 7}});

    private static ExternalDataSourceDoc excelDoc(final ResourceKey uploadKey) {
        return ExternalDataSourceDoc.builder()
                .uuid("uuid-1")
                .name("Teams")
                .sourceType(ExternalDataSourceType.EXCEL)
                .excel(ExcelSettings.builder()
                        .fileName(uploadKey == null
                                ? "teams.xlsx"
                                : null)
                        .uploadKey(uploadKey)
                        .build())
                .build();
    }

    @Test
    void columnsOfAnUploadedWorkbookAreDetected() {
        Mockito.when(excelUploads.read(UPLOAD)).thenReturn(TEAMS);

        assertThat(tester(new MockSecurityContext()).detectColumns(excelDoc(UPLOAD))).containsExactly(
                new DataColumn("team", DataColumnType.TEXT),
                new DataColumn("members", DataColumnType.LONG));
        Mockito.verifyNoInteractions(store);
    }

    @Test
    void storedWorkbookIsCheckedWhenNothingIsUploaded() {
        // The editor never has the workbook, so the stored one is read.
        final ExternalDataSourceDoc stored = excelDoc(null).copy()
                .excel(ExcelSettings.builder()
                        .data(TEAMS)
                        .build())
                .build();
        Mockito.when(store.readDocument(Mockito.any())).thenReturn(stored);

        final ExternalDataSourceTestResponse response = tester(new MockSecurityContext()).test(excelDoc(null));

        assertThat(response.isOk()).isTrue();
        assertThat(response.getMessage()).startsWith("Read 2 rows of 2 columns.");
        Mockito.verifyNoInteractions(excelUploads);
    }

    @Test
    void excelDocumentWithoutAWorkbookHasNoColumnsToDetect() {
        // No file name means no workbook, e.g. after Remove, so the store is not read.
        final ExternalDataSourceDoc removed = excelDoc(null).copy()
                .excel(ExcelSettings.builder().build())
                .build();

        assertThatThrownBy(() -> tester(new MockSecurityContext()).detectColumns(removed))
                .hasMessageContaining("There is no workbook");
        Mockito.verifyNoInteractions(store);
    }
}
