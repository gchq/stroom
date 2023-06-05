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

package stroom.pipeline.client.presenter;

import stroom.docref.DocRef;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.pipeline.shared.XsltDoc;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import javax.inject.Provider;

public class XsltPresenter extends DocumentEditTabPresenter<LinkTabPanelView, XsltDoc> {

    private static final TabData SETTINGS_TAB = new TabDataImpl("Settings");
    private static final TabData XSLT_TAB = new TabDataImpl("XSLT");

    private final XsltSettingsPresenter settingsPresenter;
    private final Provider<EditorPresenter> editorPresenterProvider;

    private EditorPresenter codePresenter;

    @Inject
    public XsltPresenter(final EventBus eventBus,
                         final LinkTabPanelView view,
                         final XsltSettingsPresenter settingsPresenter,
                         final Provider<EditorPresenter> editorPresenterProvider) {
        super(eventBus, view);
        this.settingsPresenter = settingsPresenter;
        this.editorPresenterProvider = editorPresenterProvider;

        settingsPresenter.addDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        });

        addTab(XSLT_TAB);
        addTab(SETTINGS_TAB);
        selectTab(XSLT_TAB);
    }

    @Override
    protected void getContent(final TabData tab, final ContentCallback callback) {
        if (SETTINGS_TAB.equals(tab)) {
            callback.onReady(settingsPresenter);
        } else if (XSLT_TAB.equals(tab)) {
            callback.onReady(getOrCreateCodePresenter());
        } else {
            callback.onReady(null);
        }
    }

    @Override
    public void onRead(final DocRef docRef, final XsltDoc xslt, final boolean readOnly) {
        super.onRead(docRef, xslt, readOnly);
        settingsPresenter.read(docRef, xslt, readOnly);
        if (codePresenter != null) {
            codePresenter.setText(xslt.getData());
            codePresenter.setReadOnly(readOnly);
            codePresenter.getFormatAction().setAvailable(!readOnly);
        }
    }

    @Override
    protected XsltDoc onWrite(XsltDoc xslt) {
        xslt = settingsPresenter.write(xslt);
        if (codePresenter != null) {
            xslt.setData(codePresenter.getText());
        }
        return xslt;
    }

    @Override
    public String getType() {
        return XsltDoc.DOCUMENT_TYPE;
    }

    private EditorPresenter getOrCreateCodePresenter() {
        if (codePresenter == null) {
            codePresenter = editorPresenterProvider.get();
            codePresenter.setMode(AceEditorMode.XML);
            registerHandler(codePresenter.addValueChangeHandler(event -> setDirty(true)));
            registerHandler(codePresenter.addFormatHandler(event -> setDirty(true)));
            codePresenter.setReadOnly(isReadOnly());
            codePresenter.getFormatAction().setAvailable(!isReadOnly());
            if (getEntity() != null && getEntity().getData() != null) {
                codePresenter.setText(getEntity().getData());
            }
        }
        return codePresenter;
    }
}
