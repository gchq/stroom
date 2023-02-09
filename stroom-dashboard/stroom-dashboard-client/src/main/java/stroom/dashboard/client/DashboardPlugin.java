/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.dashboard.client;

import stroom.alert.client.event.AlertEvent;
import stroom.core.client.ContentManager;
import stroom.core.client.event.CloseContentEvent;
import stroom.dashboard.client.event.ReopenResultStoreEvent;
import stroom.dashboard.client.main.DashboardPresenter;
import stroom.dashboard.client.main.DashboardSuperPresenter;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.DashboardResource;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.document.client.DocumentPlugin;
import stroom.document.client.DocumentPluginEventManager;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.hyperlink.client.ShowDashboardEvent;
import stroom.query.api.v2.ResultStoreInfo;
import stroom.query.api.v2.SearchRequestSource;
import stroom.query.api.v2.SearchRequestSource.SourceType;
import stroom.task.client.TaskStartEvent;

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
    private final Provider<DashboardPresenter> dashboardPresenterProvider;
    private final RestFactory restFactory;
    private String currentUuid;

    @Inject
    public DashboardPlugin(final EventBus eventBus,
                           final Provider<DashboardSuperPresenter> dashboardSuperPresenterProvider,
                           final Provider<DashboardPresenter> dashboardPresenterProvider,
                           final RestFactory restFactory,
                           final ContentManager contentManager,
                           final DocumentPluginEventManager entityPluginEventManager) {
        super(eventBus, contentManager, entityPluginEventManager);
        this.dashboardSuperPresenterProvider = dashboardSuperPresenterProvider;
        this.dashboardPresenterProvider = dashboardPresenterProvider;
        this.restFactory = restFactory;

        registerHandler(eventBus.addHandler(ShowDashboardEvent.getType(),
                event -> openParameterisedDashboard(event.getHref())));
        registerHandler(eventBus.addHandler(ReopenResultStoreEvent.getType(),
                event -> reopen(event.getResultStoreInfo())));
    }

    @Override
    public MyPresenterWidget<?> open(final DocRef docRef, final boolean forceOpen) {
        if (docRef.getType().equals(getType())) {
            currentUuid = docRef.getUuid();
        }
        return super.open(docRef, forceOpen);
    }

    private void openParameterisedDashboard(final String href) {
        final Map<String, String> map = buildListParamMap(href);
        final String title = map.get("title");
        String uuid = map.get("uuid");
        final String params = map.get("params");
        final boolean queryOnOpen = !Boolean.FALSE.toString().equalsIgnoreCase(map.get("queryOnOpen"));

        if (uuid == null || uuid.trim().length() == 0) {
            uuid = currentUuid;
        }

        if (uuid == null || uuid.trim().length() == 0) {
            AlertEvent.fireError(this, "No dashboard UUID has been provided for link", null);
        } else {
            final DocRef docRef = new DocRef(DashboardDoc.DOCUMENT_TYPE, uuid);

            // Start spinning.
            TaskStartEvent.fire(this, "Opening document");

            // If the item isn't already open but we are forcing it open then,
            // create a new presenter and register it as open.
            final DashboardPresenter presenter = dashboardPresenterProvider.get();
            presenter.setParams(params);
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
            showTab(docRef, presenter, closeHandler, presenter);
        }
    }

    private Map<String, String> buildListParamMap(String queryString) {
        final Map<String, String> out = new HashMap<>();
        if (queryString != null && queryString.length() > 1) {
            String qs = queryString.substring(1);

            for (String kvPair : qs.split("&")) {
                String[] kv = kvPair.split("=", 2);

                String key = kv[0];
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
            final DocRef docRef = new DocRef(DashboardDoc.DOCUMENT_TYPE, source.getOwnerDocUuid());

            // Start spinning.
            TaskStartEvent.fire(this, "Opening document");

            // If the item isn't already open but we are forcing it open then,
            // create a new presenter and register it as open.
            final DashboardPresenter presenter = dashboardPresenterProvider.get();
            presenter.setResultStoreInfo(resultStoreInfo);

            // Load the document and show the tab.
            final CloseContentEvent.Handler closeHandler = event -> {
                // Tell the presenter we are closing.
                presenter.onClose();
                // Actually close the tab.
                event.getCallback().closeTab(true);
            };
            showTab(docRef, presenter, closeHandler, presenter);
        }
    }


    @Override
    protected DocumentEditPresenter<?, ?> createEditor() {
        return dashboardSuperPresenterProvider.get();
    }

    @Override
    public void load(final DocRef docRef,
                     final Consumer<DashboardDoc> resultConsumer,
                     final Consumer<Throwable> errorConsumer) {
        final Rest<DashboardDoc> rest = restFactory.create();
        rest
                .onSuccess(resultConsumer)
                .onFailure(errorConsumer)
                .call(DASHBOARD_RESOURCE)
                .fetch(docRef.getUuid());
    }

    @Override
    public void save(final DocRef docRef,
                     final DashboardDoc document,
                     final Consumer<DashboardDoc> resultConsumer,
                     final Consumer<Throwable> errorConsumer) {
        final Rest<DashboardDoc> rest = restFactory.create();
        rest
                .onSuccess(resultConsumer)
                .onFailure(errorConsumer)
                .call(DASHBOARD_RESOURCE)
                .update(document.getUuid(), document);
    }

    @Override
    public String getType() {
        return DashboardDoc.DOCUMENT_TYPE;
    }

    @Override
    protected DocRef getDocRef(final DashboardDoc document) {
        return DocRefUtil.create(document);
    }
}
