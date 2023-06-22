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
 */

package stroom.dictionary.client.presenter;

import stroom.core.client.LocationManager;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.dictionary.shared.DictionaryResource;
import stroom.dispatch.client.ExportFileCompleteUtil;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.ResourceGeneration;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.SvgButton;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

public class DictionaryPresenter extends DocumentEditTabPresenter<LinkTabPanelView, DictionaryDoc> {

    private static final DictionaryResource DICTIONARY_RESOURCE = GWT.create(DictionaryResource.class);

    private static final TabData IMPORTS = new TabDataImpl("Imports");
    private static final TabData WORDS = new TabDataImpl("Words");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private final ButtonView downloadButton;
    private final RestFactory restFactory;
    private final LocationManager locationManager;

    private DocRef docRef;

    private final DictionarySettingsPresenter settingsPresenter;
    private final EditorPresenter codePresenter;
    private final MarkdownEditPresenter markdownEditPresenter;

    @Inject
    public DictionaryPresenter(final EventBus eventBus,
                               final LinkTabPanelView view,
                               final DictionarySettingsPresenter settingsPresenter,
                               final EditorPresenter codePresenter,
                               final MarkdownEditPresenter markdownEditPresenter,
                               final RestFactory restFactory,
                               final LocationManager locationManager) {
        super(eventBus, view);
        this.settingsPresenter = settingsPresenter;
        this.codePresenter = codePresenter;
        this.markdownEditPresenter = markdownEditPresenter;
        this.restFactory = restFactory;
        this.locationManager = locationManager;

        codePresenter.setMode(AceEditorMode.TEXT);
        // Text only, no styling or formatting
        codePresenter.getStylesOption().setUnavailable();
        codePresenter.getFormatAction().setUnavailable();

        downloadButton = SvgButton.create(SvgPresets.DOWNLOAD);
        toolbar.addButton(downloadButton);

        addTab(WORDS);
        addTab(IMPORTS);
        addTab(DOCUMENTATION);
        selectTab(WORDS);
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
        registerHandler(downloadButton.addClickHandler(clickEvent -> {
            final Rest<ResourceGeneration> rest = restFactory.create();
            rest
                    .onSuccess(result -> ExportFileCompleteUtil.onSuccess(locationManager, this, result))
                    .call(DICTIONARY_RESOURCE)
                    .download(docRef);
        }));
    }

    @Override
    protected void getContent(final TabData tab, final ContentCallback callback) {
        if (IMPORTS.equals(tab)) {
            callback.onReady(settingsPresenter);
        } else if (WORDS.equals(tab)) {
            callback.onReady(codePresenter);
        } else if (DOCUMENTATION.equals(tab)) {
            callback.onReady(markdownEditPresenter);
        } else {
            callback.onReady(null);
        }
    }

    @Override
    public void onRead(final DocRef docRef, final DictionaryDoc doc, final boolean readOnly) {
        super.onRead(docRef, doc, readOnly);
        this.docRef = docRef;
        downloadButton.setEnabled(true);
        settingsPresenter.read(docRef, doc, readOnly);

        codePresenter.setReadOnly(readOnly);
        codePresenter.setText(doc.getData());

        markdownEditPresenter.setText(doc.getDescription());
        markdownEditPresenter.setReadOnly(readOnly);
    }

    @Override
    protected DictionaryDoc onWrite(DictionaryDoc doc) {
        doc = settingsPresenter.write(doc);
        doc.setData(codePresenter.getText());
        doc.setDescription(markdownEditPresenter.getText());
        return doc;
    }

    @Override
    public String getType() {
        return DictionaryDoc.DOCUMENT_TYPE;
    }
}
