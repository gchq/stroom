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
 *
 */

package stroom.query.client.presenter;

import stroom.docref.DocRef;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.query.shared.QueryDoc;
import stroom.security.client.api.ClientSecurityContext;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import javax.inject.Provider;

public class QueryPresenter extends DocumentEditTabPresenter<LinkTabPanelView, QueryDoc> {

    private static final TabData SETTINGS_TAB = new TabDataImpl("Settings");
    private static final TabData QUERY_TAB = new TabDataImpl("Query");

    private final QuerySettingsPresenter settingsPresenter;
    private final Provider<EditorPresenter> editorPresenterProvider;

    private EditorPresenter codePresenter;
    private boolean readOnly = true;

    @Inject
    public QueryPresenter(final EventBus eventBus,
                          final LinkTabPanelView view,
                          final QuerySettingsPresenter settingsPresenter,
                          final ClientSecurityContext securityContext,
                          final Provider<EditorPresenter> editorPresenterProvider) {
        super(eventBus, view, securityContext);
        this.settingsPresenter = settingsPresenter;
        this.editorPresenterProvider = editorPresenterProvider;

        settingsPresenter.addDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        });

        addTab(QUERY_TAB);
        addTab(SETTINGS_TAB);
        selectTab(QUERY_TAB);
    }

    @Override
    protected void getContent(final TabData tab, final ContentCallback callback) {
        if (SETTINGS_TAB.equals(tab)) {
            callback.onReady(settingsPresenter);
        } else if (QUERY_TAB.equals(tab)) {
            callback.onReady(getOrCreateCodePresenter());
        } else {
            callback.onReady(null);
        }
    }

    @Override
    public void onRead(final DocRef docRef, final QueryDoc entity) {
        super.onRead(docRef, entity);
        settingsPresenter.read(docRef, entity);

        if (codePresenter != null && entity.getQuery() != null) {
            codePresenter.setText(entity.getQuery());
        }
    }

    @Override
    protected void onWrite(final QueryDoc entity) {
        settingsPresenter.write(entity);
        if (codePresenter != null) {
            entity.setQuery(codePresenter.getText());
        }
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        super.onReadOnly(readOnly);
        this.readOnly = readOnly;
        settingsPresenter.onReadOnly(readOnly);
        if (codePresenter != null) {
            codePresenter.setReadOnly(readOnly);
            codePresenter.getFormatAction().setAvailable(!readOnly);
        }
    }

    @Override
    public String getType() {
        return QueryDoc.DOCUMENT_TYPE;
    }

    private EditorPresenter getOrCreateCodePresenter() {
        if (codePresenter == null) {
            codePresenter = editorPresenterProvider.get();
            codePresenter.setMode(AceEditorMode.JAVASCRIPT);
            registerHandler(codePresenter.addValueChangeHandler(event -> setDirty(true)));
            registerHandler(codePresenter.addFormatHandler(event -> setDirty(true)));
            codePresenter.setReadOnly(readOnly);
            codePresenter.getFormatAction().setAvailable(!readOnly);
            if (getEntity() != null && getEntity().getQuery() != null) {
                codePresenter.setText(getEntity().getQuery());
            }
        }
        return codePresenter;
    }
}
