/*
 * Copyright 2016 Crown Copyright
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

package stroom.dashboard.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.dashboard.client.main.RefreshInterval;
import stroom.dashboard.shared.Automate;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.EmbeddedQueryComponentSettings;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.docref.DocRef;
import stroom.explorer.client.component.DocSelectionController;
import stroom.explorer.client.component.ExplorerPopupController;
import stroom.query.client.QueryClient;
import stroom.query.shared.QueryDoc;
import stroom.query.shared.QueryTablePreferences;
import stroom.security.shared.DocumentPermission;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.NullSafe;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;

/// An Embedded Query pane's Basic settings tab ([BasicEmbeddedQuerySettings]): its name, the query it
/// runs (a saved one, or a copy made with Copy Query), when it runs and its table's page size.
public class BasicEmbeddedQuerySettingsController
        extends BasicSettingsTabController<BasicEmbeddedQuerySettings> {

    private final DocSelectionController querySelection;
    private final Provider<ExplorerPopupController> docSelectionPopupProvider;
    private final QueryClient queryClient;
    private QueryDoc loaded;
    private QueryDoc readEmbeddedQueryDoc;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                  The application's event bus.
    /// @param view                      The content.
    /// @param docSelectionProvider      Makes the saved query's picker.
    /// @param docSelectionPopupProvider Makes the chooser of the query to copy.
    /// @param queryClient               Loads the query to copy.
    @Inject
    public BasicEmbeddedQuerySettingsController(final EventBus eventBus,
                                                final BasicEmbeddedQuerySettings view,
                                                final Provider<DocSelectionController> docSelectionProvider,
                                                final Provider<ExplorerPopupController> docSelectionPopupProvider,
                                                final QueryClient queryClient) {
        super(eventBus, view);
        this.querySelection = make(docSelectionProvider);
        querySelection.attach(view.getQueryRefBox());
        this.docSelectionPopupProvider = docSelectionPopupProvider;
        this.queryClient = queryClient;
        view.setOnReferenceChange(querySelection::setEnabled);
        view.setOnCopyQuery(this::onCopyQuery);

        querySelection.setIncludedTypes(QueryDoc.TYPE);
        querySelection.setRequiredPermissions(DocumentPermission.USE);
    }

    /// @return The StroomQL of the embedded (copied) query, or null if the query is a reference.
    public String getEmbeddedQueryString() {
        if (getView().isReference()) {
            return null;
        }
        final QueryDoc embeddedQueryDoc = loaded != null
                ? loaded
                : readEmbeddedQueryDoc;
        return NullSafe.get(embeddedQueryDoc, QueryDoc::getQuery);
    }

    /// @return The query referred to, or null if none is chosen.
    public DocRef getQuery() {
        return querySelection.getSelectedEntityReference();
    }

    private void setQuery(final DocRef queryRef) {
        querySelection.setSelectedEntityReference(queryRef, true);
    }

    @Override
    public void read(final ComponentConfig componentConfig) {
        super.read(componentConfig);

        final EmbeddedQueryComponentSettings settings = (EmbeddedQueryComponentSettings) componentConfig.getSettings();
        getView().setReference(settings.reference());
        readEmbeddedQueryDoc = settings.getEmbeddedQueryDoc();
        querySelection.setEnabled(settings.reference());
        setQuery(settings.getQueryRef());

        Automate automate = settings.getAutomate();
        if (automate == null) {
            automate = Automate.builder().build();
        }

        getView().setQueryOnOpen(automate.isOpen());
        getView().setAutoRefresh(automate.isRefresh());
        // Dashboards saved before the interval had a default may have none.
        getView().setRefreshInterval(NullSafe.nonBlankStringElse(
                automate.getRefreshInterval(),
                Automate.DEFAULT_REFRESH_INTERVAL));
        getView().setPageSize(NullSafe.getOrElse(
                settings,
                EmbeddedQueryComponentSettings::getQueryTablePreferences,
                QueryTablePreferences::getPageSize,
                100));
    }

    @Override
    public ComponentConfig write(final ComponentConfig componentConfig) {
        final ComponentConfig result = super.write(componentConfig);
        final EmbeddedQueryComponentSettings oldSettings = (EmbeddedQueryComponentSettings) result.getSettings();
        final EmbeddedQueryComponentSettings newSettings = writeSettings(oldSettings);
        return result.copy().settings(newSettings).build();
    }

    private EmbeddedQueryComponentSettings writeSettings(final EmbeddedQueryComponentSettings settings) {
        QueryTablePreferences queryTablePreferences = settings.getQueryTablePreferences();

        QueryDoc embeddedQueryDoc = null;
        if (!getView().isReference()) {
            embeddedQueryDoc = settings.getEmbeddedQueryDoc();
            if (loaded != null) {
                embeddedQueryDoc = loaded;
                if (queryTablePreferences == null &&
                    embeddedQueryDoc.getQueryTablePreferences() != null) {
                    queryTablePreferences = embeddedQueryDoc.getQueryTablePreferences();
                }
            }

            if (embeddedQueryDoc == null) {
                final long now = System.currentTimeMillis();
                embeddedQueryDoc = new QueryDoc(
                        "embedded",
                        "embedded",
                        "embedded",
                        now,
                        now,
                        "embedded",
                        "embedded",
                        null,
                        null,
                        "",
                        null);
            }
        }

        final QueryTablePreferences.Builder builder = QueryTablePreferences.copy(queryTablePreferences);
        builder.pageSize(getView().getPageSize());

        return settings
                .copy()
                .reference(getView().isReference())
                .queryRef(getQuery())
                .embeddedQueryDoc(embeddedQueryDoc)
                .automate(Automate.builder()
                        .open(getView().isQueryOnOpen())
                        .refresh(getView().isAutoRefresh())
                        .refreshInterval(getView().getRefreshInterval())
                        .build())
                .queryTablePreferences(builder.build())
                .build();
    }

    private void onCopyQuery() {
        final ExplorerPopupController chooser = docSelectionPopupProvider.get();
        chooser.setIncludeNullSelection(false);
        chooser.setCaption("Choose Query To Copy");
        chooser.setIncludedTypes(QueryDoc.TYPE);
        chooser.setRequiredPermissions(DocumentPermission.USE);
        chooser.showOnce(this, (doc, e) -> {
            if (doc != null) {
                queryClient.loadQueryDoc(doc,
                        guard(queryDoc -> {
                            loaded = queryDoc;
                            if (loaded == null) {
                                AlertEvent.fireError(
                                        this,
                                        "Error loading '" + doc + "'",
                                        e::hide);
                            } else {
                                AlertEvent.fireInfo(
                                        this,
                                        "Copy of '" + loaded.getName() + "' complete",
                                        e::hide);
                            }
                        }),
                        new DefaultErrorHandler(this, e::hide),
                        taskMonitor());
            } else {
                e.hide();
            }
        });
    }

    @Override
    public boolean validate() {
        final String problem = RefreshInterval.validate(getView().getRefreshInterval());
        if (problem != null) {
            AlertEvent.fireError(this, problem, null);
            return false;
        }
        return true;
    }

    @Override
    public boolean isDirty(final ComponentConfig componentConfig) {
        if (super.isDirty(componentConfig)) {
            return true;
        }

        final EmbeddedQueryComponentSettings oldSettings =
                (EmbeddedQueryComponentSettings) componentConfig.getSettings();
        final EmbeddedQueryComponentSettings newSettings = writeSettings(oldSettings);

        final boolean equal = Objects.equals(oldSettings.getReference(), newSettings.getReference()) &&
                              Objects.equals(oldSettings.getQueryRef(), newSettings.getQueryRef()) &&
                              Objects.equals(oldSettings.getAutomate(), newSettings.getAutomate()) &&
                              Objects.equals(oldSettings.getQueryTablePreferences(),
                                      newSettings.getQueryTablePreferences());

        return !equal;
    }

    private TaskMonitorFactory taskMonitor() {
        final TaskMonitorFactory provided = getProvidedTaskMonitor();
        return provided != null ? provided : new DefaultTaskMonitorFactory(this);
    }
}
