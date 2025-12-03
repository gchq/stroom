/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.dashboard.client;

import stroom.alert.client.event.AlertEvent;
import stroom.core.client.ContentManager;
import stroom.core.client.event.CloseContentEvent;
import stroom.dashboard.client.event.ReopenResultStoreEvent;
import stroom.dashboard.client.main.DashboardSuperPresenter;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.DashboardResource;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.document.client.DocumentPlugin;
import stroom.document.client.DocumentPluginEventManager;
import stroom.document.client.event.OpenDocumentEvent.CommonDocLinkTab;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.hyperlink.client.ShowDashboardEvent;
import stroom.query.api.ResultStoreInfo;
import stroom.query.api.SearchRequestSource;
import stroom.query.api.SearchRequestSource.SourceType;
import stroom.security.client.api.ClientSecurityContext;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.gwt.http.client.URL;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.inject.Singleton;

@Singleton
public class DashboardPlugin extends DocumentPlugin<DashboardDoc> {

    private static final DashboardResource DASHBOARD_RESOURCE = GWT.create(DashboardResource.class);

    private final Provider<DashboardSuperPresenter> dashboardSuperPresenterProvider;
    private final RestFactory restFactory;
    private String currentUuid;

    @Inject
    public DashboardPlugin(final EventBus eventBus,
                           final Provider<DashboardSuperPresenter> dashboardSuperPresenterProvider,
                           final RestFactory restFactory,
                           final ContentManager contentManager,
                           final DocumentPluginEventManager entityPluginEventManager,
                           final ClientSecurityContext securityContext) {
        super(eventBus, contentManager, entityPluginEventManager, securityContext);
        this.dashboardSuperPresenterProvider = dashboardSuperPresenterProvider;
        this.restFactory = restFactory;

        registerHandler(eventBus.addHandler(ShowDashboardEvent.getType(),
                event -> openParameterisedDashboard(event.getContext(), event.getHref())));
        registerHandler(eventBus.addHandler(ReopenResultStoreEvent.getType(),
                event -> reopen(event.getResultStoreInfo())));
    }

    @Override
    public MyPresenterWidget<?> open(final DocRef docRef,
                                     final boolean forceOpen,
                                     final boolean fullScreen,
                                     final CommonDocLinkTab selectedLinkTab,
                                     final Consumer<MyPresenterWidget<?>> callbackOnOpen,
                                     final TaskMonitorFactory taskMonitorFactory) {
        if (docRef.getType().equals(getType())) {
            currentUuid = docRef.getUuid();
        }
        return super.open(docRef, forceOpen, fullScreen, selectedLinkTab, callbackOnOpen, taskMonitorFactory);
    }

    private void openParameterisedDashboard(final Object context, final String href) {
        final Map<String, String> map = buildListParamMap(href);
        final String title = map.get("title");
        String uuid = map.get("uuid");
        final String params = map.get("params");
        final boolean queryOnOpen = !Boolean.FALSE.toString().equalsIgnoreCase(map.get("queryOnOpen"));

        if (uuid == null || uuid.trim().isEmpty()) {
            uuid = currentUuid;
        }

        if (uuid == null || uuid.trim().isEmpty()) {
            AlertEvent.fireError(this, "No dashboard UUID has been provided for link", null);
        } else {
            final DocRef docRef = new DocRef(DashboardDoc.TYPE, uuid);

            // If the item isn't already open but we are forcing it open then,
            // create a new presenter and register it as open.
            final DashboardSuperPresenter presenter = dashboardSuperPresenterProvider.get();
            presenter.setParentContext(context);
            presenter.setParamsFromLink(params);
            presenter.setCustomTitle(title);
            presenter.setQueryOnOpen(queryOnOpen);

            //        // Register the tab as being open.
            //        documentToTabDataMap.put(docRef, tabData);
            //        tabDataToDocumentMap.put(tabData, docRef);

            // Load the document and show the tab.
            final CloseContentEvent.Handler closeHandler = event -> {
                // Tell the presenter we are closing.
                presenter.onClose();
                // Actually close the tab.
                event.getCallback().closeTab(true);
            };
            showDocument(docRef,
                    presenter,
                    closeHandler,
                    presenter,
                    false,
                    new DefaultTaskMonitorFactory(this));
        }
    }

    private Map<String, String> buildListParamMap(final String queryString) {
        final Map<String, String> out = new HashMap<>();
        if (queryString != null && queryString.length() > 1) {
            final String qs = queryString.substring(1);

            for (final String kvPair : qs.split("&")) {
                final String[] kv = kvPair.split("=", 2);

                final String key = kv[0];
                if (key.isEmpty()) {
                    continue;
                }

                String val = kv.length > 1
                        ? kv[1]
                        : "";
                try {
                    val = URL.decodeQueryString(val);
                } catch (final RuntimeException e) {
                    GWT.log("Cannot decode a URL query string parameter=" + key +
                            " value=" + val, e);
                }

                out.putIfAbsent(key, val);
            }
        }

        return out;
    }

    private void reopen(final ResultStoreInfo resultStoreInfo) {
        final SearchRequestSource source = resultStoreInfo.getSearchRequestSource();
        if (source != null && SourceType.DASHBOARD_UI.equals(source.getSourceType())) {
            final DocRef docRef = source.getOwnerDocRef();
            if (docRef != null) {
                // If the item isn't already open but we are forcing it open then,
                // create a new presenter and register it as open.
                final DashboardSuperPresenter presenter = dashboardSuperPresenterProvider.get();
                presenter.setResultStoreInfo(resultStoreInfo);

                // Load the document and show the tab.
                final CloseContentEvent.Handler closeHandler = event -> {
                    // Tell the presenter we are closing.
                    presenter.onClose();
                    // Actually close the tab.
                    event.getCallback().closeTab(true);
                };
                showDocument(docRef,
                        presenter,
                        closeHandler,
                        presenter,
                        false,
                        new DefaultTaskMonitorFactory(this));
            }
        }
    }


    @Override
    protected DocumentEditPresenter<?, ?> createEditor() {
        return dashboardSuperPresenterProvider.get();
    }

    @Override
    public void load(final DocRef docRef,
                     final Consumer<DashboardDoc> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(DASHBOARD_RESOURCE)
                .method(res -> res.fetch(docRef.getUuid()))
                .onSuccess(resultConsumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    @Override
    public void save(final DocRef docRef,
                     final DashboardDoc document,
                     final Consumer<DashboardDoc> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(DASHBOARD_RESOURCE)
                .method(res -> res.update(document.getUuid(), document))
                .onSuccess(resultConsumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    @Override
    public String getType() {
        return DashboardDoc.TYPE;
    }

    @Override
    protected DocRef getDocRef(final DashboardDoc document) {
        return DocRefUtil.create(document);
    }
}
