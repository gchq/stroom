/*
 * Copyright 2022 Crown Copyright
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
import stroom.entity.client.component.MarkdownEditController;
import stroom.entity.client.presenter.ControllerTabProvider;
import stroom.entity.client.presenter.DocTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownTabProvider;
import stroom.query.client.component.QueryDocEditController;
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
        extends DocTabPresenter<LinkTabPanelView, QueryDoc> {

    private static final TabData QUERY = new TabDataImpl("Query");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private final QueryDocEditController queryDocEditPresenter;
    private Runnable saveInterceptor;

    @Inject
    public QueryDocPresenter(final EventBus eventBus,
                             final LinkTabPanelView view,
                             final QueryDocEditController queryDocEditPresenter,
                             final Provider<MarkdownEditController> markdownEditProvider,
                             final DocumentUserPermissionsTabProvider<QueryDoc>
                                     documentUserPermissionsTabProvider) {
        super(eventBus, view);
        own(queryDocEditPresenter);
        this.queryDocEditPresenter = queryDocEditPresenter;

        addTab(QUERY, ControllerTabProvider.ofOwned(eventBus, queryDocEditPresenter,
                QueryDocEditController::read,
                QueryDocEditController::write));
        addTab(DOCUMENTATION, new MarkdownTabProvider<QueryDoc>(getEventBus(), markdownEditProvider::get) {
            @Override
            public void onRead(final MarkdownEditController markdownEdit,
                               final DocRef docRef,
                               final QueryDoc document) {
                markdownEdit.setText(document.getDescription());
                markdownEdit.setReadOnly(isReadOnly());
            }

            @Override
            public QueryDoc onWrite(final MarkdownEditController markdownEdit,
                                    final QueryDoc document) {
                return document.copy().description(markdownEdit.getText()).build();
            }
        });
        addTab(PERMISSIONS, documentUserPermissionsTabProvider);
        selectTab(QUERY);

        listen(ContentTabSelectionChangeEvent.getType(), e ->
                queryDocEditPresenter.onContentTabVisible(e.getTabData() == this));
    }

    @Override
    public boolean handleKeyAction(final Action action) {
        if (Action.OK == action
            && Objects.equals(getSelectedTab().getType(), QUERY.getType())) {
            queryDocEditPresenter.start();
            return true;
        } else if (Action.CLOSE == action
                   && Objects.equals(getSelectedTab().getType(), QUERY.getType())) {
            queryDocEditPresenter.stop();
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
