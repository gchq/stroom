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

import stroom.dashboard.client.vis.ClearFunctionCacheEvent;
import stroom.dashboard.client.vis.ClearScriptCacheEvent;
import stroom.docref.DocRef;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.script.shared.ScriptDoc;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

public class ScriptPresenter extends DocumentEditTabPresenter<LinkTabPanelView, ScriptDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData SCRIPT = new TabDataImpl("Script");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");

    private final ScriptSettingsPresenter settingsPresenter;
    private final EditorPresenter codePresenter;
    private final MarkdownEditPresenter markdownEditPresenter;

    private int loadCount;

    @Inject
    public ScriptPresenter(final EventBus eventBus,
                           final LinkTabPanelView view,
                           final ScriptSettingsPresenter settingsPresenter,
                           final EditorPresenter codePresenter,
                           final MarkdownEditPresenter markdownEditPresenter) {
        super(eventBus, view);
        this.settingsPresenter = settingsPresenter;
        this.codePresenter = codePresenter;
        this.markdownEditPresenter = markdownEditPresenter;

        codePresenter.setMode(AceEditorMode.JAVASCRIPT);

        addTab(SCRIPT);
        addTab(SETTINGS);
        addTab(DOCUMENTATION);
        selectTab(SCRIPT);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(codePresenter.addValueChangeHandler(event -> setDirty(true)));
        registerHandler(codePresenter.addFormatHandler(event -> setDirty(true)));
        registerHandler(settingsPresenter.addDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        }));
        registerHandler(markdownEditPresenter.addDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        }));
    }

    @Override
    protected void getContent(final TabData tab, final ContentCallback callback) {
        if (SETTINGS.equals(tab)) {
            callback.onReady(settingsPresenter);
        } else if (SCRIPT.equals(tab)) {
            callback.onReady(codePresenter);
        } else if (DOCUMENTATION.equals(tab)) {
            callback.onReady(markdownEditPresenter);
        } else {
            callback.onReady(null);
        }
    }

    @Override
    public void onRead(final DocRef docRef, final ScriptDoc doc, final boolean readOnly) {
        super.onRead(docRef, doc, readOnly);
        loadCount++;
        settingsPresenter.read(docRef, doc, readOnly);

        codePresenter.setText(doc.getData());
        codePresenter.setReadOnly(isReadOnly());
        codePresenter.getFormatAction().setAvailable(!isReadOnly());

        markdownEditPresenter.setText(doc.getDescription());
        markdownEditPresenter.setReadOnly(readOnly);

        if (loadCount > 1) {
            // Remove the script function from the cache so dashboards reload
            // it.
            ClearScriptCacheEvent.fire(this, docRef);

            // This script might be used by any visualisation so clear the vis
            // function cache so that scripts are requested again if needed.
            ClearFunctionCacheEvent.fire(this);
        }
    }

    @Override
    protected ScriptDoc onWrite(ScriptDoc doc) {
        doc = settingsPresenter.write(doc);
        doc.setData(codePresenter.getText());
        doc.setDescription(markdownEditPresenter.getText());
        return doc;
    }

    @Override
    public String getType() {
        return ScriptDoc.DOCUMENT_TYPE;
    }
}
