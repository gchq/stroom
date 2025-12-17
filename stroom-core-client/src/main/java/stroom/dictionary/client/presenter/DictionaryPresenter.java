/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.entity.client.presenter.AbstractTabProvider;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.DocumentEditTabProvider;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.entity.client.presenter.MarkdownTabProvider;
import stroom.security.client.presenter.DocumentUserPermissionsTabProvider;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.SvgButton;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import javax.inject.Provider;

public class DictionaryPresenter extends DocumentEditTabPresenter<LinkTabPanelView, DictionaryDoc> {

    private static final DictionaryResource DICTIONARY_RESOURCE = GWT.create(DictionaryResource.class);

    private static final TabData WORDS = TabDataImpl.builder()
            .withLabel("Words")
            .withTooltip("List of 'words' held in this Dictionary.")
            .build();
    private static final TabData IMPORTS = TabDataImpl.builder()
            .withLabel("Imports")
            .withTooltip("Other dictionaries from which words will be imported.")
            .build();
    private static final TabData EFFECTIVE_WORDS = TabDataImpl.builder()
            .withLabel("Effective Words")
            .withTooltip("The combined list of 'words' held in this Dictionary and its imports.")
            .build();
    private static final TabData DOCUMENTATION = TabDataImpl.builder()
            .withLabel("Documentation")
            .withTooltip(TabData.createDocumentationTooltip(DictionaryDoc.TYPE))
            .build();
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private final ButtonView downloadButton;
    private final RestFactory restFactory;
    private final LocationManager locationManager;

    private DocRef docRef;

    @Inject
    public DictionaryPresenter(final EventBus eventBus,
                               final LinkTabPanelView view,
                               final Provider<DictionarySettingsPresenter> dictionarySettingsPresenterProvider,
                               final Provider<EditorPresenter> editorPresenterProvider,
                               final Provider<MarkdownEditPresenter> markdownEditPresenterProvider,
                               final DocumentUserPermissionsTabProvider<DictionaryDoc>
                                       documentUserPermissionsTabProvider,
                               final Provider<WordListPresenter> wordListPresenterProvider,
                               final RestFactory restFactory,
                               final LocationManager locationManager) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.locationManager = locationManager;

        downloadButton = SvgButton.create(SvgPresets.DOWNLOAD.title("Download Dictionary words"));

        toolbar.addButton(downloadButton);

        addTab(WORDS, new AbstractTabProvider<DictionaryDoc, EditorPresenter>(eventBus) {
            @Override
            protected EditorPresenter createPresenter() {
                final EditorPresenter editorPresenter = editorPresenterProvider.get();
                editorPresenter.setMode(AceEditorMode.TEXT);
                // Text only, no styling or formatting
                editorPresenter.getStylesOption().setUnavailable();
                editorPresenter.getFormatAction().setUnavailable();

                registerHandler(editorPresenter.addValueChangeHandler(event -> setDirty(true)));
                registerHandler(editorPresenter.addFormatHandler(event -> setDirty(true)));
                return editorPresenter;
            }

            @Override
            public void onRead(final EditorPresenter presenter,
                               final DocRef docRef,
                               final DictionaryDoc document,
                               final boolean readOnly) {
                presenter.setText(document.getData());
                presenter.setReadOnly(readOnly);
            }

            @Override
            public DictionaryDoc onWrite(final EditorPresenter presenter, final DictionaryDoc document) {
                document.setData(presenter.getText());
                return document;
            }
        });
        addTab(IMPORTS, new DocumentEditTabProvider<>(dictionarySettingsPresenterProvider::get));
        addTab(EFFECTIVE_WORDS, createEffectiveWordsTabProvider(eventBus, wordListPresenterProvider));
        addTab(DOCUMENTATION, new MarkdownTabProvider<DictionaryDoc>(eventBus, markdownEditPresenterProvider) {
            @Override
            public void onRead(final MarkdownEditPresenter presenter,
                               final DocRef docRef,
                               final DictionaryDoc document,
                               final boolean readOnly) {
                presenter.setText(document.getDescription());
                presenter.setReadOnly(readOnly);
            }

            @Override
            public DictionaryDoc onWrite(final MarkdownEditPresenter presenter,
                                         final DictionaryDoc document) {
                document.setDescription(presenter.getText());
                return document;
            }
        });
        addTab(PERMISSIONS, documentUserPermissionsTabProvider);
        selectTab(WORDS);
    }

    private static AbstractTabProvider<DictionaryDoc, WordListPresenter> createEffectiveWordsTabProvider(
            final EventBus eventBus,
            final Provider<WordListPresenter> wordListPresenterProvider) {

        return new AbstractTabProvider<DictionaryDoc, WordListPresenter>(eventBus) {

            @Override
            protected WordListPresenter createPresenter() {
                return wordListPresenterProvider.get();
            }

            @Override
            public void onRead(final WordListPresenter wordListPresenter,
                               final DocRef docRef,
                               final DictionaryDoc document,
                               final boolean readOnly) {
                wordListPresenter.setDocRef(docRef);
            }
        };
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(downloadButton.addClickHandler(clickEvent -> {
            restFactory
                    .create(DICTIONARY_RESOURCE)
                    .method(res -> res.download(docRef))
                    .onSuccess(result -> ExportFileCompleteUtil.onSuccess(locationManager, this, result))
                    .taskMonitorFactory(this)
                    .exec();
        }));
    }

    @Override
    public void onRead(final DocRef docRef, final DictionaryDoc doc, final boolean readOnly) {
        super.onRead(docRef, doc, readOnly);
        this.docRef = docRef;
        downloadButton.setEnabled(true);
    }

    @Override
    public String getType() {
        return DictionaryDoc.TYPE;
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
