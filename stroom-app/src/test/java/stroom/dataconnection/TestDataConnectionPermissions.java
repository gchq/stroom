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

import stroom.ai.impl.mock.MockAiModule;
import stroom.app.guice.CoreModule;
import stroom.app.guice.JerseyModule;
import stroom.app.uri.UriFactoryModule;
import stroom.credentials.impl.CredentialsService;
import stroom.credentials.shared.Credential;
import stroom.dataconnection.impl.DataConnectionConfig;
import stroom.dataconnection.impl.DataConnectionSearchProvider;
import stroom.dataconnection.impl.DataConnectionStore;
import stroom.dataconnection.shared.CsvSettings;
import stroom.dataconnection.shared.DataColumn;
import stroom.dataconnection.shared.DataColumnType;
import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.dataconnection.shared.DataConnectionType;
import stroom.dataconnection.shared.JdbcConnectionSettings;
import stroom.docref.DocRef;
import stroom.index.VolumeTestConfigModule;
import stroom.meta.statistics.impl.MockMetaStatisticsModule;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.DestroyReason;
import stroom.query.api.Query;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchResponse;
import stroom.query.api.TableResult;
import stroom.query.common.v2.ExpressionContextFactory;
import stroom.query.common.v2.ResultStoreManager;
import stroom.query.language.SearchRequestFactory;
import stroom.query.language.functions.ExpressionContext;
import stroom.resource.impl.ResourceModule;
import stroom.security.api.AppPermissionService;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserService;
import stroom.security.impl.DocumentPermissionServiceImpl;
import stroom.security.impl.SecurityContextModule;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.User;
import stroom.test.BootstrapTestModule;
import stroom.test.ConfigMapperSpy;
import stroom.test.StroomIntegrationTest;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.PermissionException;

import jakarta.inject.Inject;
import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Permissions for querying a Data Connection, with Stroom's real security rather than the mock
/// the core integration tests use.
///
/// Running a query needs Use on the document. The credential is resolved as the user running the
/// query, who needs Use on it but not the Manage Credentials application permission (design
/// decision 5).
@ExtendWith(GuiceExtension.class)
@IncludeModule(UriFactoryModule.class)
@IncludeModule(CoreModule.class)
@IncludeModule(BootstrapTestModule.class)
@IncludeModule(ResourceModule.class)
@IncludeModule(stroom.cluster.impl.MockClusterModule.class)
@IncludeModule(VolumeTestConfigModule.class)
@IncludeModule(SecurityContextModule.class)
@IncludeModule(MockMetaStatisticsModule.class)
@IncludeModule(stroom.test.DatabaseTestControlModule.class)
@IncludeModule(JerseyModule.class)
@IncludeModule(MockAiModule.class)
class TestDataConnectionPermissions extends StroomIntegrationTest {

    @Inject
    private DataConnectionStore store;
    @Inject
    private CredentialsService credentialsService;
    @Inject
    private DataConnectionSearchProvider searchProvider;
    @Inject
    private ResultStoreManager resultStoreManager;
    @Inject
    private SearchRequestFactory searchRequestFactory;
    @Inject
    private ExpressionContextFactory expressionContextFactory;
    @Inject
    private SecurityContext securityContext;
    @Inject
    private UserService userService;
    @Inject
    private DocumentPermissionServiceImpl documentPermissionService;
    @Inject
    private AppPermissionService appPermissionService;
    @Inject
    private ConfigMapperSpy configMapperSpy;

    private DocRef credentialDocRef;
    private DataConnectionDoc doc;
    // A CSV of logons to join to the JDBC document's staff.
    private DocRef logonsDocRef;

    @BeforeEach
    void setUp() throws SQLException {
        final String url = DataConnectionTestDatabase.prepare();
        configMapperSpy.setConfigValueMapper(DataConnectionConfig.class,
                config -> DataConnectionTestDatabase.allowingOnly(config, url));

        final String credentialName = "sqlds-reader-" + UUID.randomUUID();
        final String credentialUuid = UUID.randomUUID().toString();
        credentialDocRef = new DocRef(Credential.TYPE, credentialUuid, credentialName);

        // The credential and document belong to an administrator. The processing
        // user cannot store a credential, as it has no user account to record.
        final User owner = securityContext.asProcessingUserResult(() -> {
            final User user = userService.getOrCreateUser("sqlds-owner-" + UUID.randomUUID());
            appPermissionService.addPermission(user.asRef(), AppPermission.ADMINISTRATOR);
            return user;
        });
        doc = securityContext.asUserResult(owner.asRef(), () -> {
            credentialsService.storeCredentials(
                    DataConnectionTestDatabase.readerCredential(credentialUuid, credentialName));
            final DocRef docRef = store.createDocument("HR " + UUID.randomUUID());
            return store.writeDocument(store.readDocument(docRef).copy()
                    .jdbc(JdbcConnectionSettings.builder()
                            .url(url)
                            .credentialName(credentialName)
                            .build())
                    .build());
        });
        logonsDocRef = securityContext.asUserResult(owner.asRef(), () -> {
            final DocRef docRef = store.createDocument("Logons " + UUID.randomUUID());
            store.writeDocument(store.readDocument(docRef).copy()
                    .sourceType(DataConnectionType.CSV)
                    .csv(CsvSettings.builder()
                            .columns(List.of(
                                    new DataColumn("User", DataColumnType.TEXT),
                                    new DataColumn("Host", DataColumnType.TEXT)))
                            .data("User,Host\nSmith,h1\nJones,h2\nNobody,h3\n")
                            .build())
                    .build());
            return docRef;
        });
    }

    // Lets a user use the logons CSV, the first source of the join searches.
    private User canUseLogons(final User user) {
        securityContext.asProcessingUser(() ->
                documentPermissionService.setPermission(logonsDocRef, user.asRef(), DocumentPermission.USE));
        return user;
    }

    // Joins the logons CSV to the JDBC document's staff, as the user.
    private SearchResponse joinSearch(final User user) {
        return securityContext.asUserResult(user.asRef(), () -> {
            final SearchRequest sampleRequest = new SearchRequest(
                    null, null, Query.builder().build(), null, DateTimeSettings.builder().build(), false);
            final ExpressionContext expressionContext = expressionContextFactory.createContext(sampleRequest);
            final SearchRequest searchRequest = searchRequestFactory.create(
                    "from \"" + logonsDocRef.getName() + "\"\n"
                    + "left join \"" + doc.getName() + "\" as hr sql \"\"\""
                    + DataConnectionTestDatabase.ALL_STAFF_SQL + "\"\"\" on User = hr.name\n"
                    + "sort by Host\n"
                    + "select User, hr.id",
                    sampleRequest,
                    expressionContext);
            final SearchResponse response = resultStoreManager.search(searchRequest);
            resultStoreManager.destroy(response.getKey(), DestroyReason.NO_LONGER_NEEDED);
            return response;
        });
    }

    private static List<List<String>> rows(final SearchResponse response) {
        return response.getResults()
                .stream()
                .flatMap(result -> ((TableResult) result).getRows().stream())
                .map(row -> row.getValues()
                        .subList(0, 2)
                        .stream()
                        .map(value -> value == null
                                ? ""
                                : value)
                        .toList())
                .toList();
    }

    // A user with no application permissions, given Use on the document and the credential as asked.
    private User user(final boolean canUseDocument, final boolean canUseCredential) {
        return securityContext.asProcessingUserResult(() -> {
            final User user = userService.getOrCreateUser("sqlds-user-" + UUID.randomUUID());
            if (canUseDocument) {
                documentPermissionService.setPermission(doc.asDocRef(), user.asRef(), DocumentPermission.USE);
            }
            if (canUseCredential) {
                documentPermissionService.setPermission(credentialDocRef, user.asRef(), DocumentPermission.USE);
            }
            return user;
        });
    }

    private SearchResponse search(final User user) {
        return securityContext.asUserResult(user.asRef(), () -> {
            final SearchRequest sampleRequest = new SearchRequest(
                    null, null, Query.builder().build(), null, DateTimeSettings.builder().build(), false);
            final ExpressionContext expressionContext = expressionContextFactory.createContext(sampleRequest);
            final SearchRequest searchRequest = searchRequestFactory.create(
                    "from \"" + doc.getName() + "\" sql \"\"\"" + DataConnectionTestDatabase.ALL_STAFF_SQL
                    + "\"\"\" select name",
                    sampleRequest,
                    expressionContext);
            final SearchResponse response = resultStoreManager.search(searchRequest);
            resultStoreManager.destroy(response.getKey(), DestroyReason.NO_LONGER_NEEDED);
            return response;
        });
    }

    private static int rowCount(final SearchResponse response) {
        return response.getResults()
                .stream()
                .mapToInt(result -> ((TableResult) result).getRows().size())
                .sum();
    }

    private static List<String> errors(final SearchResponse response) {
        return response.getErrorMessages() == null
                ? List.of()
                : response.getErrorMessages().stream().map(ErrorMessage::getMessage).toList();
    }

    @Test
    void joinToADocumentTheUserCanUseAddsItsFields() {
        final SearchResponse response = joinSearch(canUseLogons(user(true, true)));

        assertThat(errors(response)).isEmpty();
        assertThat(rows(response)).containsExactly(
                List.of("Smith", "1"),
                List.of("Jones", "2"),
                List.of("Nobody", ""));
    }

    @Test
    void joinToADocumentTheUserCannotUseIsRefused() {
        final User user = canUseLogons(user(false, true));

        // The joined source can't even be found by a user who can't use it.
        assertThatThrownBy(() -> joinSearch(user))
                .hasMessageContaining("HR ")
                .hasMessageContaining("not found. You may not have permission to use it");
    }

    @Test
    void joinWithoutUseOnTheJoinedSourcesCredentialFails() {
        final SearchResponse response = joinSearch(canUseLogons(user(true, false)));

        assertThat(rowCount(response)).isZero();
        assertThat(errors(response))
                .anySatisfy(message -> assertThat(message).contains("Unable to use credential"))
                .anySatisfy(message -> assertThat(message).contains("to join failed"));
    }

    @Test
    void useOnTheDocumentAndCredentialIsEnough() {
        final User user = user(true, true);
        assertThat(securityContext.asUserResult(user.asRef(), () ->
                securityContext.hasAppPermission(AppPermission.CREDENTIALS)))
                .as("the user does not have the Manage Credentials permission")
                .isFalse();

        final SearchResponse response = search(user);

        assertThat(errors(response)).isEmpty();
        assertThat(rowCount(response)).isEqualTo(3);
    }

    @Test
    void userWithoutUseOnTheCredentialGetsNoRows() {
        final SearchResponse response = search(user(true, false));

        assertThat(rowCount(response)).isZero();
        assertThat(errors(response)).anySatisfy(message ->
                assertThat(message).contains("Unable to use credential"));
    }

    @Test
    void userWithoutUseOnTheDocumentCannotQuery() {
        final User user = user(false, true);

        assertThatThrownBy(() -> search(user)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void userWithoutUseOnTheCredentialCannotListCachedFields() {
        // The processing user's lookup puts the columns in the cache.
        securityContext.asProcessingUser(() ->
                searchProvider.getFields(doc.asDocRef(), DataConnectionTestDatabase.ALL_STAFF_SQL));
        final User user = user(true, false);

        assertThatThrownBy(() -> securityContext.asUser(user.asRef(), () ->
                searchProvider.getFields(doc.asDocRef(), DataConnectionTestDatabase.ALL_STAFF_SQL)))
                .isInstanceOf(PermissionException.class)
                .hasMessageContaining("Unable to use credential");
    }

    @Test
    void userWithUseOnBothCanListFields() {
        final User user = user(true, true);

        assertThat(securityContext.asUserResult(user.asRef(), () ->
                searchProvider.getFields(doc.asDocRef(), DataConnectionTestDatabase.ALL_STAFF_SQL)))
                .hasSize(3);
    }
}
