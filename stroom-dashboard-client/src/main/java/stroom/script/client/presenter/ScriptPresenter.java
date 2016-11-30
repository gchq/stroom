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

package stroom.script.client.presenter;

import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.core.client.event.DirtyKeyDownHander;
import stroom.dashboard.client.vis.ClearFunctionCacheEvent;
import stroom.dashboard.client.vis.ClearScriptCacheEvent;
import stroom.dispatch.client.AsyncCallbackAdaptor;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.event.DirtyEvent;
import stroom.entity.client.event.DirtyEvent.DirtyHandler;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.EntityEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.shared.DocRef;
import stroom.entity.shared.EntityServiceLoadAction;
import stroom.entity.shared.Res;
import stroom.script.shared.Script;
import stroom.security.client.ClientSecurityContext;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;
import stroom.xmleditor.client.event.FormatEvent;
import stroom.xmleditor.client.event.FormatEvent.FormatHandler;
import stroom.xmleditor.client.presenter.BaseXMLEditorPresenter;
import stroom.xmleditor.client.presenter.ReadOnlyXMLEditorPresenter;
import stroom.xmleditor.client.presenter.XMLEditorPresenter;
import stroom.xmleditor.client.view.XMLEditorMenuPresenter;

import java.util.HashSet;
import java.util.Set;

public class ScriptPresenter extends EntityEditTabPresenter<LinkTabPanelView, Script> {
    private static final TabData SETTINGS_TAB = new TabDataImpl("Settings");
    private static final TabData SCRIPT_TAB = new TabDataImpl("Script");

    private final ScriptSettingsPresenter settingsPresenter;
    private final XMLEditorMenuPresenter editorMenuPresenter;
    private final ClientSecurityContext securityContext;
    private final ClientDispatchAsync dispatcher;

    private BaseXMLEditorPresenter codePresenter;
    private boolean loadedResource;
    private Res resource;
    private Boolean readOnly;

    private int loadCount;

    @Inject
    public ScriptPresenter(final EventBus eventBus, final LinkTabPanelView view,
                           final ScriptSettingsPresenter settingsPresenter, final XMLEditorMenuPresenter editorMenuPresenter,
                           final ClientSecurityContext securityContext, final ClientDispatchAsync dispatcher) {
        super(eventBus, view, securityContext);
        this.settingsPresenter = settingsPresenter;
        this.editorMenuPresenter = editorMenuPresenter;
        this.securityContext = securityContext;
        this.dispatcher = dispatcher;

        settingsPresenter.addDirtyHandler(new DirtyHandler() {
            @Override
            public void onDirty(final DirtyEvent event) {
                if (event.isDirty()) {
                    setDirty(true);
                }
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
                    if (!readOnly) {
                        codePresenter = new XMLEditorPresenter(getEventBus(), editorMenuPresenter);
                    } else {
                        codePresenter = new ReadOnlyXMLEditorPresenter(getEventBus(), editorMenuPresenter);
                    }
                    codePresenter.getStylesOption().setOn(false);
                    codePresenter.getStylesOption().setAvailable(false);

                    registerHandler(codePresenter.addKeyDownHandler(new DirtyKeyDownHander() {
                        @Override
                        public void onDirty(final KeyDownEvent event) {
                            setDirty(true);
                        }
                    }));
                    registerHandler(codePresenter.addFormatHandler(new FormatHandler() {
                        @Override
                        public void onFormat(final FormatEvent event) {
                            setDirty(true);
                        }
                    }));

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
    public void onRead(final Script script) {
        loadCount++;
        settingsPresenter.read(script);

        // Reload the resource if we have loaded it before.
        if (codePresenter != null) {
            loadResource(codePresenter, null);
        }

        if (loadCount > 1) {
            // Remove the script function from the cache so dashboards reload
            // it.
            ClearScriptCacheEvent.fire(this, DocRef.create(script));

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
    protected void onPermissionsCheck(final boolean readOnly) {
        super.onPermissionsCheck(readOnly);
        this.readOnly = readOnly;
    }

    private void loadResource(final BaseXMLEditorPresenter codePresenter, final ContentCallback callback) {
        if (!loadedResource) {
            final Set<String> fetchSet = new HashSet<>();
            fetchSet.add(Script.FETCH_RESOURCE);
            final EntityServiceLoadAction<Script> action = new EntityServiceLoadAction<Script>(DocRef.create(getEntity()),
                    fetchSet);
            dispatcher.execute(action, new AsyncCallbackAdaptor<Script>() {
                @Override
                public void onSuccess(final Script script) {
                    resource = script.getResource();
                    if (resource != null) {
                        codePresenter.setText(resource.getData());
                    }

                    if (callback != null) {
                        callback.onReady(codePresenter);
                    }

                    loadedResource = true;
                }
            });
        }
    }

    @Override
    public String getType() {
        return Script.ENTITY_TYPE;
    }
}
