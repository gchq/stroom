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

import stroom.dashboard.client.vis.ClearFunctionCacheEvent;
import stroom.dashboard.client.vis.ClearScriptCacheEvent;
import stroom.docref.DocRef;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.entity.client.component.MarkdownEditController;
import stroom.entity.client.presenter.AbstractTabProvider;
import stroom.entity.client.presenter.ControllerTabProvider;
import stroom.entity.client.presenter.DocTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownTabProvider;
import stroom.script.client.component.ScriptSettingsController;
import stroom.script.shared.ScriptDoc;
import stroom.security.client.presenter.DocumentUserPermissionsTabProvider;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import javax.inject.Provider;

public class ScriptPresenter extends DocTabPresenter<LinkTabPanelView, ScriptDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData SCRIPT = new TabDataImpl("Script");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private int loadCount;

    @Inject
    public ScriptPresenter(final EventBus eventBus,
                           final LinkTabPanelView view,
                           final Provider<ScriptSettingsController> settingsProvider,
                           final Provider<EditorPresenter> editorPresenterProvider,
                           final Provider<MarkdownEditController> markdownEditProvider,
                           final DocumentUserPermissionsTabProvider<ScriptDoc> documentUserPermissionsTabProvider) {
        super(eventBus, view);

        addTab(SCRIPT, new AbstractTabProvider<ScriptDoc, EditorPresenter>() {
            @Override
            protected EditorPresenter createPresenter() {
                final EditorPresenter editorPresenter = editorPresenterProvider.get();
                editorPresenter.setMode(AceEditorMode.JAVASCRIPT);
                own(editorPresenter.addValueChangeHandler(event -> onChange()));
                own(editorPresenter.addFormatHandler(event -> onChange()));
                return editorPresenter;
            }

            @Override
            public void onRead(final EditorPresenter presenter,
                               final DocRef docRef,
                               final ScriptDoc document) {
                presenter.setText(document.getData());
                presenter.setReadOnly(isReadOnly());
                presenter.getFormatAction().setAvailable(!isReadOnly());

                loadCount++;
                if (loadCount > 1) {
                    // Remove the script function from the cache so dashboards reload
                    // it.
                    ClearScriptCacheEvent.fire(ScriptPresenter.this, docRef);

                    // This script might be used by any visualisation so clear the vis
                    // function cache so that scripts are requested again if needed.
                    ClearFunctionCacheEvent.fire(ScriptPresenter.this);
                }
            }

            @Override
            public ScriptDoc onWrite(final EditorPresenter presenter, final ScriptDoc document) {
                return document.copy().data(presenter.getText()).build();
            }
        });
        addTab(SETTINGS, new ControllerTabProvider<>(eventBus, settingsProvider::get,
                ScriptSettingsController::read,
                ScriptSettingsController::write));
        addTab(DOCUMENTATION, new MarkdownTabProvider<ScriptDoc>(getEventBus(), markdownEditProvider::get) {
            @Override
            public void onRead(final MarkdownEditController markdownEdit,
                               final DocRef docRef,
                               final ScriptDoc document) {
                markdownEdit.setText(document.getDescription());
                markdownEdit.setReadOnly(isReadOnly());
            }

            @Override
            public ScriptDoc onWrite(final MarkdownEditController markdownEdit,
                                     final ScriptDoc document) {
                return document.copy().description(markdownEdit.getText()).build();
            }
        });
        addTab(PERMISSIONS, documentUserPermissionsTabProvider);
        selectTab(SCRIPT);
    }

    @Override
    public String getType() {
        return ScriptDoc.TYPE;
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
