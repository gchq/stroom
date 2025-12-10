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

package stroom.query.client.presenter;

import stroom.content.client.event.ContentTabSelectionChangeEvent;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.DocumentEditTabProvider;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.entity.client.presenter.MarkdownTabProvider;
import stroom.query.shared.QueryDoc;
import stroom.security.client.presenter.DocumentUserPermissionsTabProvider;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import javax.inject.Provider;

public class QueryDocPresenter
        extends DocumentEditTabPresenter<LinkTabPanelView, QueryDoc> {

    private static final TabData QUERY = new TabDataImpl("Query");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private final QueryDocEditPresenter queryDocEditPresenter;
    private final DocumentEditTabProvider<QueryDoc> queryDocDocumentEditTabProvider;
    private Runnable saveInterceptor;

    @Inject
    public QueryDocPresenter(final EventBus eventBus,
                             final LinkTabPanelView view,
                             final QueryDocEditPresenter queryDocEditPresenter,
                             final Provider<MarkdownEditPresenter> markdownEditPresenterProvider,
                             final DocumentUserPermissionsTabProvider<QueryDoc>
                                     documentUserPermissionsTabProvider) {
        super(eventBus, view);
        this.queryDocEditPresenter = queryDocEditPresenter;

        queryDocEditPresenter.setTaskMonitorFactory(this);
        queryDocDocumentEditTabProvider = new DocumentEditTabProvider<>(
                () -> queryDocEditPresenter);

        addTab(QUERY, queryDocDocumentEditTabProvider);
        addTab(DOCUMENTATION, new MarkdownTabProvider<QueryDoc>(eventBus, markdownEditPresenterProvider) {
            @Override
            public void onRead(final MarkdownEditPresenter presenter,
                               final DocRef docRef,
                               final QueryDoc document,
                               final boolean readOnly) {
                presenter.setText(document.getDescription());
                presenter.setReadOnly(readOnly);
            }

            @Override
            public QueryDoc onWrite(final MarkdownEditPresenter presenter,
                                    final QueryDoc document) {
                document.setDescription(presenter.getText());
                return document;
            }
        });
        addTab(PERMISSIONS, documentUserPermissionsTabProvider);
        selectTab(QUERY);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(getEventBus().addHandler(ContentTabSelectionChangeEvent.getType(), e ->
                queryDocEditPresenter.onContentTabVisible(e.getTabData() == this)));
    }

    @Override
    public boolean handleKeyAction(final Action action) {
        if (Action.OK == action
            && Objects.equals(getSelectedTab().getType(), QUERY.getType())) {

            final DocumentEditPresenter<?, QueryDoc> presenter = queryDocDocumentEditTabProvider.getPresenter();
            if (presenter instanceof QueryDocEditPresenter) {
                ((QueryDocEditPresenter) presenter).start();
            }
            return true;
        } else if (Action.CLOSE == action
                   && Objects.equals(getSelectedTab().getType(), QUERY.getType())) {

            final DocumentEditPresenter<?, QueryDoc> presenter = queryDocDocumentEditTabProvider.getPresenter();
            if (presenter instanceof QueryDocEditPresenter) {
                ((QueryDocEditPresenter) presenter).stop();
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void save() {
        if (saveInterceptor == null) {
            super.save();
        } else {
            saveInterceptor.run();
        }
    }

    public void setSaveInterceptor(final Runnable saveInterceptor) {
        this.saveInterceptor = saveInterceptor;
    }

    @Override
    public String getType() {
        return QueryDoc.TYPE;
    }

    @Override
    protected TabData getPermissionsTab() {
        return PERMISSIONS;
    }

    @Override
    protected TabData getDocumentationTab() {
        return DOCUMENTATION;
    }
}
