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

package stroom.script.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;
import stroom.dashboard.client.vis.ClearFunctionCacheEvent;
import stroom.dashboard.client.vis.ClearScriptCacheEvent;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.shared.DocRefUtil;
import stroom.query.api.v2.DocRef;
import stroom.script.shared.Script;
import stroom.security.client.ClientSecurityContext;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import javax.inject.Provider;

public class ScriptPresenter extends DocumentEditTabPresenter<LinkTabPanelView, Script> {
    private static final TabData SETTINGS_TAB = new TabDataImpl("Settings");
    private static final TabData SCRIPT_TAB = new TabDataImpl("Script");

    private final ScriptSettingsPresenter settingsPresenter;
    private final Provider<EditorPresenter> editorPresenterProvider;

    private EditorPresenter codePresenter;
    private boolean readOnly = true;

    private int loadCount;

    @Inject
    public ScriptPresenter(final EventBus eventBus, final LinkTabPanelView view,
                           final ScriptSettingsPresenter settingsPresenter, final ClientSecurityContext securityContext, final Provider<EditorPresenter> editorPresenterProvider) {
        super(eventBus, view, securityContext);
        this.settingsPresenter = settingsPresenter;
        this.editorPresenterProvider = editorPresenterProvider;

        settingsPresenter.addDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        });

        addTab(SCRIPT_TAB);
        addTab(SETTINGS_TAB);
        selectTab(SCRIPT_TAB);
    }

    @Override
    protected void getContent(final TabData tab, final ContentCallback callback) {
        if (SETTINGS_TAB.equals(tab)) {
            callback.onReady(settingsPresenter);
        } else if (SCRIPT_TAB.equals(tab)) {
            callback.onReady(getOrCreateCodePresenter());
        } else {
            callback.onReady(null);
        }
    }

    @Override
    public void onRead(final DocRef docRef, final Script script) {
        super.onRead(docRef, script);
        loadCount++;
        settingsPresenter.read(docRef, script);

        if (codePresenter != null && script.getResource() != null) {
            codePresenter.setText(script.getResource());
        }

        if (loadCount > 1) {
            // Remove the script function from the cache so dashboards reload
            // it.
            ClearScriptCacheEvent.fire(this, DocRefUtil.create(script));

            // This script might be used by any visualisation so clear the vis
            // function cache so that scripts are requested again if needed.
            ClearFunctionCacheEvent.fire(this);
        }
    }

    @Override
    protected void onWrite(final Script script) {
        settingsPresenter.write(script);
        if (codePresenter != null) {
            script.setResource(codePresenter.getText());
        }
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        super.onReadOnly(readOnly);
        this.readOnly = readOnly;
        settingsPresenter.onReadOnly(readOnly);
        if (codePresenter != null) {
            codePresenter.setReadOnly(readOnly);
            codePresenter.getContextMenu().setShowFormatOption(!readOnly);
        }
    }

    @Override
    public String getType() {
        return Script.ENTITY_TYPE;
    }

    private EditorPresenter getOrCreateCodePresenter() {
        if (codePresenter == null) {
            codePresenter = editorPresenterProvider.get();
            codePresenter.setMode(AceEditorMode.JAVASCRIPT);
            registerHandler(codePresenter.addValueChangeHandler(event -> setDirty(true)));
            registerHandler(codePresenter.addFormatHandler(event -> setDirty(true)));
            codePresenter.setReadOnly(readOnly);
            codePresenter.getContextMenu().setShowFormatOption(!readOnly);
            if (getEntity() != null && getEntity().getResource() != null) {
                codePresenter.setText(getEntity().getResource());
            }
        }
        return codePresenter;
    }
}
