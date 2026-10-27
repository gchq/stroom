/*
 * Copyright 2026 Crown Copyright
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

package stroom.dictionary.client.component;

import stroom.core.client.LocationManager;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.dictionary.shared.DictionaryResource;
import stroom.dispatch.client.ExportFileCompleteUtil;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.event.OpenDocumentEvent.CommonDocLinkTab;
import stroom.entity.client.component.DocumentEditor;
import stroom.entity.client.component.LinkTabPanel;
import stroom.entity.client.component.MarkdownEditController;
import stroom.security.client.component.DocumentUserPermissionsTabController;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Provider;

/// A dictionary's editor: its Words, Imports, Effective Words, Documentation and Permissions tabs,
/// with Download beside Save and Save As (see [DocumentEditor]).
public class DictionaryEditor extends DocumentEditor<DictionaryDoc> {

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

    private final RestFactory restFactory;
    private final LocationManager locationManager;
    private final ToolbarButton downloadButton;

    /// Makes the editor, with its Words, Imports, Effective Words, Documentation and Permissions
    /// tabs.
    @Inject
    public DictionaryEditor(final EventBus eventBus,
                            final LinkTabPanel view,
                            final Provider<WordsTabController> wordsTabProvider,
                            final Provider<ImportsTabController> importsTabProvider,
                            final Provider<EffectiveWordsListController> effectiveWordsListProvider,
                            final Provider<MarkdownEditController> markdownEditProvider,
                            final Provider<DocumentUserPermissionsTabController> permissionsProvider,
                            final RestFactory restFactory,
                            final LocationManager locationManager) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.locationManager = locationManager;
        downloadButton = getToolbar().button(SvgImage.DOWNLOAD, "Download Dictionary Words", this::download);
        downloadButton.setEnabled(false);

        addTab(WORDS,
                () -> {
                    final WordsTabController wordsTab = wordsTabProvider.get();
                    wordsTab.setOnChange(this::onChange);
                    return wordsTab;
                },
                (wordsTab, docRef, doc) -> wordsTab.setWords(doc.getData()),
                (wordsTab, doc) -> doc.copy().data(wordsTab.getWords()).build());
        addTab(IMPORTS,
                () -> {
                    final ImportsTabController importsTab = importsTabProvider.get();
                    importsTab.setOnChange(this::onChange);
                    return importsTab;
                },
                ImportsTabController::read,
                ImportsTabController::write);
        addTab(EFFECTIVE_WORDS,
                effectiveWordsListProvider::get,
                (effectiveWords, docRef, doc) -> effectiveWords.setDocRef(docRef));
        addTab(DOCUMENTATION,
                () -> {
                    // It tells the editor of changes itself (Contexts.DIRTY)
                    final MarkdownEditController documentation = markdownEditProvider.get();
                    documentation.followReadOnly();
                    return documentation;
                },
                (documentation, docRef, doc) -> documentation.setText(doc.getDescription()),
                (documentation, doc) -> doc.copy().description(documentation.getText()).build());
        addTab(PERMISSIONS,
                permissionsProvider::get,
                (permissions, docRef, doc) -> permissions.setDocRef(docRef));
        setCommonTab(CommonDocLinkTab.DOCUMENTATION, DOCUMENTATION);
        setCommonTab(CommonDocLinkTab.PERMISSIONS, PERMISSIONS);
        selectTab(WORDS);
    }

    @Override
    protected void onRead(final DocRef docRef, final DictionaryDoc doc) {
        downloadButton.setEnabled(true);
    }

    private void download() {
        final DocRef docRef = getDocRef();
        restFactory
                .create(DICTIONARY_RESOURCE)
                .method(resource -> resource.download(docRef))
                .onSuccess(result -> ExportFileCompleteUtil.onSuccess(
                        locationManager,
                        this,
                        result))
                .owner(this)
                .exec();
    }
}
