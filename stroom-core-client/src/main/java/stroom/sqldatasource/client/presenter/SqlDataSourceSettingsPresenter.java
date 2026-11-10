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

package stroom.sqldatasource.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.credentials.client.presenter.CredentialClient;
import stroom.credentials.client.presenter.CredentialListModel;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialType;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocPresenter;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.item.client.SelectionBox;
import stroom.sqldatasource.client.presenter.SqlDataSourceSettingsPresenter.SqlDataSourceSettingsView;
import stroom.sqldatasource.shared.AthenaConnectionSettings;
import stroom.sqldatasource.shared.JdbcConnectionSettings;
import stroom.sqldatasource.shared.SqlConnectionType;
import stroom.sqldatasource.shared.SqlDataSourceDoc;
import stroom.sqldatasource.shared.SqlDataSourceResource;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.NullSafe;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/// The settings tab of a SQL Data Source: its connection, by JDBC or Amazon Athena, and its limits.
///
/// Each connection type has its own credential picker, listing only the credentials it can use:
/// usernames and passwords for JDBC, AWS access keys for Athena. The settings of both types are
/// kept, so switching type and back loses nothing.
public class SqlDataSourceSettingsPresenter
        extends DocPresenter<SqlDataSourceSettingsView, SqlDataSourceDoc>
        implements SqlDataSourceSettingsUiHandlers {

    private static final SqlDataSourceResource SQL_DATA_SOURCE_RESOURCE = GWT.create(SqlDataSourceResource.class);

    private final RestFactory restFactory;
    private final CredentialChoice jdbcCredential = new CredentialChoice();
    private final CredentialChoice athenaCredential = new CredentialChoice();

    /// @param eventBus         The event bus.
    /// @param view             The view.
    /// @param restFactory      Calls the server.
    /// @param credentialClient Looks up credentials.
    @Inject
    public SqlDataSourceSettingsPresenter(final EventBus eventBus,
                                          final SqlDataSourceSettingsView view,
                                          final RestFactory restFactory,
                                          final CredentialClient credentialClient) {
        super(eventBus, view);
        this.restFactory = restFactory;

        view.getJdbcCredentialSelectionBox().setModel(credentialModel(
                eventBus, credentialClient, CredentialType.USERNAME_PASSWORD));
        view.getAthenaCredentialSelectionBox().setModel(credentialModel(
                eventBus, credentialClient, CredentialType.AWS_KEY));
        registerHandler(view.getJdbcCredentialSelectionBox().addValueChangeHandler(event -> {
            jdbcCredential.pick(event.getValue());
            view.setJdbcCredentialNote(null);
        }));
        registerHandler(view.getAthenaCredentialSelectionBox().addValueChangeHandler(event -> {
            athenaCredential.pick(event.getValue());
            view.setAthenaCredentialNote(null);
        }));
        view.setUiHandlers(this);
    }

    private CredentialListModel credentialModel(final EventBus eventBus,
                                                final CredentialClient credentialClient,
                                                final CredentialType credentialType) {
        final CredentialListModel model = new CredentialListModel(eventBus, credentialClient, Set.of(credentialType));
        model.setTaskMonitorFactory(this);
        return model;
    }

    @Override
    protected void onRead(final DocRef docRef, final SqlDataSourceDoc doc, final boolean readOnly) {
        final SqlDataSourceSettingsView view = getView();
        view.setConnectionType(Objects.requireNonNullElse(doc.getConnectionType(), SqlConnectionType.JDBC));

        final JdbcConnectionSettings jdbc = Objects.requireNonNullElse(
                doc.getJdbc(),
                JdbcConnectionSettings.builder().build());
        view.setJdbcUrl(jdbc.getUrl());
        view.setConnectionProperties(ConnectionPropertiesText.format(jdbc.getConnectionProperties()));
        view.setMaxPoolSize(Objects.requireNonNullElse(
                jdbc.getMaxPoolSize(),
                JdbcConnectionSettings.DEFAULT_MAX_POOL_SIZE));
        view.setFetchSize(Objects.requireNonNullElse(jdbc.getFetchSize(), JdbcConnectionSettings.DEFAULT_FETCH_SIZE));
        showCredential(jdbc.getCredentialName(),
                jdbcCredential,
                view.getJdbcCredentialSelectionBox(),
                view::setJdbcCredentialNote);

        final AthenaConnectionSettings athena = Objects.requireNonNullElse(
                doc.getAthena(),
                AthenaConnectionSettings.builder().build());
        view.setRegion(athena.getRegion());
        view.setWorkGroup(Objects.requireNonNullElse(
                athena.getWorkGroup(),
                AthenaConnectionSettings.DEFAULT_WORK_GROUP));
        view.setCatalog(Objects.requireNonNullElse(athena.getCatalog(), AthenaConnectionSettings.DEFAULT_CATALOG));
        view.setDatabase(Objects.requireNonNullElse(
                athena.getDatabase(),
                AthenaConnectionSettings.DEFAULT_DATABASE));
        view.setOutputLocation(athena.getOutputLocation());
        view.setResultReuseMinutes(Objects.requireNonNullElse(athena.getResultReuseMinutes(), 0));
        showCredential(athena.getCredentialName(),
                athenaCredential,
                view.getAthenaCredentialSelectionBox(),
                view::setAthenaCredentialNote);

        view.setTimeField(doc.getTimeField());
        view.setQueryTimeoutSeconds(Objects.requireNonNullElse(
                doc.getQueryTimeoutSeconds(),
                SqlDataSourceDoc.DEFAULT_QUERY_TIMEOUT_SECONDS));
        view.setMaxRows(Objects.requireNonNullElse(doc.getMaxRows(), SqlDataSourceDoc.DEFAULT_MAX_ROWS));
    }

    /// Shows the document's credential in its picker, once looked up. If it cannot be shown, as it
    /// is gone or the user may not use it, a note says so and the document keeps it.
    private void showCredential(final String credentialName,
                                final CredentialChoice choice,
                                final SelectionBox<Credential> selectionBox,
                                final Consumer<String> noteConsumer) {
        choice.reset(credentialName);
        selectionBox.setValue(null);
        noteConsumer.accept(null);
        if (!NullSafe.isBlankString(credentialName)) {
            restFactory
                    .create(CredentialClient.CREDENTIALS_RESOURCE)
                    .method(res -> res.getCredentialByName(credentialName))
                    .onSuccess(credential -> {
                        // The user may have picked another while the lookup ran, or the
                        // document may have been read again.
                        if (choice.awaits(credentialName)) {
                            if (credential == null) {
                                noteConsumer.accept(choice.unresolvedNote());
                            } else {
                                selectionBox.setValue(credential);
                            }
                        }
                    })
                    .onFailure(error -> {
                        if (choice.awaits(credentialName)) {
                            noteConsumer.accept(choice.unresolvedNote());
                        }
                    })
                    .taskMonitorFactory(this)
                    .exec();
        }
    }

    @Override
    protected SqlDataSourceDoc onWrite(final SqlDataSourceDoc doc) {
        final SqlDataSourceSettingsView view = getView();
        final JdbcConnectionSettings jdbc = JdbcConnectionSettings.builder()
                .url(blankToNull(view.getJdbcUrl()))
                .credentialName(jdbcCredential.getName())
                .connectionProperties(ConnectionPropertiesText.parse(view.getConnectionProperties()))
                .maxPoolSize(view.getMaxPoolSize())
                .fetchSize(view.getFetchSize())
                .build();
        final AthenaConnectionSettings athena = AthenaConnectionSettings.builder()
                .region(blankToNull(view.getRegion()))
                .workGroup(blankToNull(view.getWorkGroup()))
                .catalog(blankToNull(view.getCatalog()))
                .database(blankToNull(view.getDatabase()))
                .outputLocation(blankToNull(view.getOutputLocation()))
                .credentialName(athenaCredential.getName())
                .resultReuseMinutes(view.getResultReuseMinutes())
                .build();
        return doc.copy()
                .connectionType(view.getConnectionType())
                .jdbc(jdbc)
                .athena(athena)
                .timeField(blankToNull(view.getTimeField()))
                .queryTimeoutSeconds(view.getQueryTimeoutSeconds())
                .maxRows(view.getMaxRows())
                .build();
    }

    private static String blankToNull(final String value) {
        return NullSafe.isBlankString(value)
                ? null
                : value.trim();
    }

    @Override
    public void onTestConnection(final TaskMonitorFactory taskMonitorFactory) {
        // Send the document as edited, with its real identity, as the server checks the user can
        // edit it before testing it with the credential it names.
        final SqlDataSourceDoc doc = onWrite(getEntity());
        restFactory
                .create(SQL_DATA_SOURCE_RESOURCE)
                .method(res -> res.testConnection(doc))
                .onSuccess(result -> {
                    if (result.isOk()) {
                        AlertEvent.fireInfo(this, "Connection Success", result.getMessage(), null);
                    } else {
                        AlertEvent.fireError(this, "Connection Failure", result.getMessage(), null);
                    }
                })
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }


    // --------------------------------------------------------------------------------


    /// The view of a SQL Data Source's settings.
    public interface SqlDataSourceSettingsView
            extends View, ReadOnlyChangeHandler, HasUiHandlers<SqlDataSourceSettingsUiHandlers> {

        /// @return The connection type chosen.
        SqlConnectionType getConnectionType();

        /// Shows a connection type, with the settings that go with it.
        ///
        /// @param connectionType The connection type.
        void setConnectionType(SqlConnectionType connectionType);

        /// @return The JDBC URL as entered.
        String getJdbcUrl();

        /// @param jdbcUrl The JDBC URL to show, or `null`.
        void setJdbcUrl(String jdbcUrl);

        /// @return The picker of the JDBC username and password credential.
        SelectionBox<Credential> getJdbcCredentialSelectionBox();

        /// Shows or hides a note about the JDBC credential.
        ///
        /// @param note The note, or `null` to hide it.
        void setJdbcCredentialNote(String note);

        /// @return The connection properties as entered, one `name=value` per line.
        String getConnectionProperties();

        /// @param connectionProperties The connection properties to show.
        void setConnectionProperties(String connectionProperties);

        /// @return The largest connection pool.
        int getMaxPoolSize();

        /// @param maxPoolSize The largest connection pool to show.
        void setMaxPoolSize(int maxPoolSize);

        /// @return The rows fetched in each round trip.
        int getFetchSize();

        /// @param fetchSize The fetch size to show.
        void setFetchSize(int fetchSize);

        /// @return The AWS region as entered.
        String getRegion();

        /// @param region The AWS region to show, or `null`.
        void setRegion(String region);

        /// @return The Athena workgroup as entered.
        String getWorkGroup();

        /// @param workGroup The Athena workgroup to show.
        void setWorkGroup(String workGroup);

        /// @return The data catalog as entered.
        String getCatalog();

        /// @param catalog The data catalog to show.
        void setCatalog(String catalog);

        /// @return The database as entered.
        String getDatabase();

        /// @param database The database to show.
        void setDatabase(String database);

        /// @return The S3 output location as entered.
        String getOutputLocation();

        /// @param outputLocation The S3 output location to show, or `null`.
        void setOutputLocation(String outputLocation);

        /// @return The picker of the AWS access key credential.
        SelectionBox<Credential> getAthenaCredentialSelectionBox();

        /// Shows or hides a note about the AWS access key credential.
        ///
        /// @param note The note, or `null` to hide it.
        void setAthenaCredentialNote(String note);

        /// @return How old an Athena result may be and still be reused, in minutes.
        int getResultReuseMinutes();

        /// @param resultReuseMinutes The result reuse age to show.
        void setResultReuseMinutes(int resultReuseMinutes);

        /// @return The time column as entered.
        String getTimeField();

        /// @param timeField The time column to show, or `null`.
        void setTimeField(String timeField);

        /// @return The query timeout in seconds.
        int getQueryTimeoutSeconds();

        /// @param queryTimeoutSeconds The query timeout to show.
        void setQueryTimeoutSeconds(int queryTimeoutSeconds);

        /// @return The row limit.
        int getMaxRows();

        /// @param maxRows The row limit to show.
        void setMaxRows(int maxRows);
    }
}
