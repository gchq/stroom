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
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import javax.inject.Provider;

public class TextConverterPresenter extends DocumentEditTabPresenter<LinkTabPanelView, TextConverterDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData CONVERSION = new TabDataImpl("Conversion");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");

    private final TextConverterSettingsPresenter settingsPresenter;
    private final Provider<EditorPresenter> editorPresenterProvider;
    private final MarkdownEditPresenter markdownEditPresenter;

    private EditorPresenter codePresenter;

    @Inject
    public TextConverterPresenter(final EventBus eventBus,
                                  final LinkTabPanelView view,
                                  final TextConverterSettingsPresenter settingsPresenter,
                                  final Provider<EditorPresenter> editorPresenterProvider,
                                  final MarkdownEditPresenter markdownEditPresenter) {
        super(eventBus, view);
        this.settingsPresenter = settingsPresenter;
        this.editorPresenterProvider = editorPresenterProvider;
        this.markdownEditPresenter = markdownEditPresenter;

        addTab(CONVERSION);
        addTab(SETTINGS);
        addTab(DOCUMENTATION);
        selectTab(CONVERSION);
    }

    @Override
    protected void onBind() {
        super.onBind();
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
        } else if (CONVERSION.equals(tab)) {
            callback.onReady(getOrCreateCodePresenter());
        } else if (DOCUMENTATION.equals(tab)) {
            callback.onReady(markdownEditPresenter);
        } else {
            callback.onReady(null);
        }
    }

    @Override
    public void onRead(final DocRef docRef, final TextConverterDoc doc, final boolean readOnly) {
        super.onRead(docRef, doc, readOnly);
        settingsPresenter.read(docRef, doc, readOnly);
        if (codePresenter != null) {
            codePresenter.setText(doc.getData());
            codePresenter.setReadOnly(readOnly);
            codePresenter.getFormatAction().setAvailable(!readOnly);
        }
        markdownEditPresenter.setText(doc.getDescription());
        markdownEditPresenter.setReadOnly(readOnly);
    }

    @Override
    protected TextConverterDoc onWrite(TextConverterDoc doc) {
        doc = settingsPresenter.write(doc);
        if (codePresenter != null) {
            doc.setData(codePresenter.getText());
        }
        doc.setDescription(markdownEditPresenter.getText());
        return doc;
    }

    @Override
    public String getType() {
        return TextConverterDoc.DOCUMENT_TYPE;
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
