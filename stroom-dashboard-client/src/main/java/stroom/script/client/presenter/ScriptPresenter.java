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
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.Res;
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
    private final ClientSecurityContext securityContext;
    private final Provider<EditorPresenter> editorPresenterProvider;
    private final ClientDispatchAsync dispatcher;

    private EditorPresenter codePresenter;
    private boolean loadedResource;
    private Res resource;
    private Boolean readOnly;

    private int loadCount;

    @Inject
    public ScriptPresenter(final EventBus eventBus, final LinkTabPanelView view,
                           final ScriptSettingsPresenter settingsPresenter, final ClientSecurityContext securityContext, final Provider<EditorPresenter> editorPresenterProvider, final ClientDispatchAsync dispatcher) {
        super(eventBus, view, securityContext);
        this.settingsPresenter = settingsPresenter;
        this.securityContext = securityContext;
        this.editorPresenterProvider = editorPresenterProvider;
        this.dispatcher = dispatcher;

        settingsPresenter.addDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        });

        addTab(SETTINGS_TAB);
        addTab(SCRIPT_TAB);
        selectTab(SETTINGS_TAB);
    }

    @Override
    protected void getContent(final TabData tab, final ContentCallback callback) {
        if (SETTINGS_TAB.equals(tab)) {
            callback.onReady(settingsPresenter);
        } else if (SCRIPT_TAB.equals(tab)) {
            if (codePresenter == null) {
                if (readOnly != null) {
                    codePresenter = editorPresenterProvider.get();
                    codePresenter.setReadOnly(readOnly);
                    codePresenter.setMode(AceEditorMode.JAVASCRIPT);
                    codePresenter.getContextMenu().setShowFormatOption(!readOnly);
//                    codePresenter.getStylesOption().setOn(false);
//                    codePresenter.getStylesOption().setAvailable(false);

                    registerHandler(codePresenter.addValueChangeHandler(event -> setDirty(true)));
                    registerHandler(codePresenter.addFormatHandler(event -> setDirty(true)));

                    loadResource(codePresenter, callback);
                }
            } else {
                callback.onReady(codePresenter);
            }
        } else {
            callback.onReady(null);
        }
    }

    @Override
    public void onRead(final DocRef docRef, final Script script) {
        loadCount++;
        settingsPresenter.read(docRef, script);

        // Reload the resource if we have loaded it before.
        if (codePresenter != null) {
            loadResource(codePresenter, null);
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
        if (loadedResource) {
            if (resource == null) {
                resource = new Res();
            }
            resource.setData(codePresenter.getText());
            script.setResource(resource);
        }
        loadedResource = false;
    }

    @Override
    public void onPermissionsCheck(final boolean readOnly) {
        super.onPermissionsCheck(readOnly);
        this.readOnly = readOnly;
    }

    private void loadResource(final EditorPresenter codePresenter, final ContentCallback callback) {
        if (!loadedResource) {
//            final DocumentServiceReadAction<Script> action = new DocumentServiceReadAction<>(DocRefUtil.create(getEntity()));
//            dispatcher.exec(action).onSuccess(script -> {
            resource = getEntity().getResource();
                if (resource != null) {
                    codePresenter.setText(resource.getData());
                }

                if (callback != null) {
                    callback.onReady(codePresenter);
                }

                loadedResource = true;
//            });
        }
    }

    @Override
    public String getType() {
        return Script.ENTITY_TYPE;
    }
}
