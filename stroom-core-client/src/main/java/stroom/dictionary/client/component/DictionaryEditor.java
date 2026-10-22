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
import stroom.entity.client.presenter.HasToolbar;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.security.client.presenter.DocumentUserPermissionsPresenter;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.component.client.ComponentLayer;
import stroom.widget.component.client.ContextValue;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.PresenterAdapter;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.Layer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.inject.Provider;

/// A dictionary's editor: the document, with its Words, Imports, Effective Words, Documentation
/// and Permissions tabs, and Save, Save As and Download.
///
/// Its owner reads the document in ([#read(DocRef, DictionaryDoc)]), says whether the user may
/// change it ([#setReadOnly(boolean)]), which every tab follows, and saves it when asked
/// ([#setOnSave(Runnable)], [#write()]). Each tab is made when it is first shown.
public class DictionaryEditor extends Component {

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

    // Why a document is read only: it is only ever opened read only for a user who can't edit it
    private static final String READ_ONLY_REASON = "You don't have permission to change this";

    private final LinkTabPanelView view;
    private final Provider<WordsTab> wordsTabProvider;
    private final Provider<ImportsTab> importsTabProvider;
    private final Provider<EffectiveWordsList> effectiveWordsListProvider;
    private final Provider<MarkdownEditPresenter> markdownEditPresenterProvider;
    private final Provider<DocumentUserPermissionsPresenter> permissionsPresenterProvider;
    private final RestFactory restFactory;
    private final LocationManager locationManager;
    // Fires app-wide events (the download's messages) on the bus
    private final HasHandlers bus;

    private final ContextValue<Boolean> readOnly = ContextValue.of(true);
    private final Toolbar toolbar = new Toolbar("Document actions");
    private final ToolbarButton saveButton;
    private final ToolbarButton saveAsButton;
    private final ToolbarButton downloadButton;
    // Says that the document is read only, at the far right of the tab bar; a polite status, so
    // screen readers announce it (and why) when a read-only document opens
    private final HTML readOnlyStatus = new HTML();
    // The buttons the tab showing added to the toolbar, removed when another tab is shown
    private final List<Widget> tabButtons = new ArrayList<>();
    // Each tab's body once it is made
    private final Map<TabData, Layer> layers = new HashMap<>();

    // The tabs, made when first shown
    private WordsTab wordsTab;
    private ImportsTab importsTab;
    private EffectiveWordsList effectiveWordsList;
    private PresenterAdapter<MarkdownEditPresenter> documentation;
    private PresenterAdapter<DocumentUserPermissionsPresenter> permissions;

    private DocRef docRef;
    private DictionaryDoc doc;
    private boolean dirty;
    private Consumer<Boolean> onDirtyChange = isDirty -> {
    };
    private Runnable onSave = () -> {
    };
    private Runnable onSaveAs = () -> {
    };

    @Inject
    public DictionaryEditor(final EventBus eventBus,
                            final LinkTabPanelView view,
                            final Provider<WordsTab> wordsTabProvider,
                            final Provider<ImportsTab> importsTabProvider,
                            final Provider<EffectiveWordsList> effectiveWordsListProvider,
                            final Provider<MarkdownEditPresenter> markdownEditPresenterProvider,
                            final Provider<DocumentUserPermissionsPresenter> permissionsPresenterProvider,
                            final RestFactory restFactory,
                            final LocationManager locationManager) {
        super(eventBus);
        this.view = view;
        this.wordsTabProvider = wordsTabProvider;
        this.importsTabProvider = importsTabProvider;
        this.effectiveWordsListProvider = effectiveWordsListProvider;
        this.markdownEditPresenterProvider = markdownEditPresenterProvider;
        this.permissionsPresenterProvider = permissionsPresenterProvider;
        this.restFactory = restFactory;
        this.locationManager = locationManager;
        this.bus = eventBus::fireEvent;

        provide(Contexts.READ_ONLY, readOnly);

        saveButton = toolbar.button(SvgImage.SAVE, "Save", this::save);
        saveButton.setEnabled(false);
        saveAsButton = toolbar.button(SvgImage.SAVEAS, "Save As", this::saveAs);
        saveAsButton.setEnabled(false);
        downloadButton = toolbar.button(SvgImage.DOWNLOAD, "Download Dictionary Words", this::download);
        downloadButton.setEnabled(false);

        readOnlyStatus.setStyleName("docTab-readOnlyStatus");
        readOnlyStatus.getElement().setAttribute("role", "status");
        view.setTabBarStatus(readOnlyStatus);
        view.getTabBar().addSelectionHandler(event -> showTab(event.getSelectedItem()));
        for (final TabData tab : new TabData[]{WORDS, IMPORTS, EFFECTIVE_WORDS, DOCUMENTATION, PERMISSIONS}) {
            view.getTabBar().addTab(tab);
        }
        initWidget(view.asWidget());

        watch(Contexts.READ_ONLY, this::showReadOnly);
        showTab(WORDS);
    }

    /// Reads a document into the editor and its tabs, which then show it as clean.
    ///
    /// @param docRef The document's reference.
    /// @param doc    The document.
    public void read(final DocRef docRef, final DictionaryDoc doc) {
        this.docRef = Objects.requireNonNull(docRef, "docRef");
        this.doc = Objects.requireNonNull(doc, "doc");
        if (wordsTab != null) {
            wordsTab.setWords(doc.getData());
        }
        if (importsTab != null) {
            importsTab.read(docRef, doc);
        }
        if (effectiveWordsList != null) {
            effectiveWordsList.setDocRef(docRef);
        }
        if (documentation != null) {
            documentation.getPresenter().setText(doc.getDescription());
        }
        if (permissions != null) {
            permissions.getPresenter().setDocRef(docRef);
        }
        saveAsButton.setEnabled(true);
        downloadButton.setEnabled(true);
        setDirty(false);
    }

    /// @param readOnly Whether the user may not change the document. Every tab follows it.
    public void setReadOnly(final boolean readOnly) {
        this.readOnly.set(readOnly);
    }

    /// @return The document as the tabs show it, e.g. to save it.
    public DictionaryDoc write() {
        DictionaryDoc written = doc;
        if (wordsTab != null) {
            written = written.copy().data(wordsTab.getWords()).build();
        }
        if (importsTab != null) {
            written = importsTab.write(written);
        }
        if (documentation != null) {
            written = written.copy().description(documentation.getPresenter().getText()).build();
        }
        return written;
    }

    /// @return The document's reference, or null before it is read.
    public DocRef getDocRef() {
        return docRef;
    }

    /// @return Whether the tabs show changes that aren't saved.
    public boolean isDirty() {
        return dirty;
    }

    /// @param onDirtyChange What to run when the document becomes dirty or clean, e.g. to mark
    ///                      its tab's label.
    public void setOnDirtyChange(final Consumer<Boolean> onDirtyChange) {
        this.onDirtyChange = onDirtyChange;
    }

    /// @param onSave What to run when the user asks to save the document.
    public void setOnSave(final Runnable onSave) {
        this.onSave = onSave;
    }

    /// @param onSaveAs What to run when the user asks to save a copy of the document.
    public void setOnSaveAs(final Runnable onSaveAs) {
        this.onSaveAs = onSaveAs;
    }

    private void showTab(final TabData tab) {
        final Layer layer = layers.computeIfAbsent(tab, this::makeTab);
        view.getLayerContainer().show(layer);

        // The tab's own buttons join the document's toolbar, and its other toolbars follow it
        for (final Widget widget : tabButtons) {
            toolbar.remove(widget);
        }
        tabButtons.clear();
        view.clearToolbar();
        view.addToolbar(toolbar);
        if (tab == DOCUMENTATION) {
            final HasToolbar hasToolbar = documentation.getPresenter();
            for (final Widget widget : hasToolbar.getDocumentButtons()) {
                toolbar.add(widget);
                tabButtons.add(widget);
            }
            for (final Widget widget : hasToolbar.getToolbars()) {
                view.addToolbar(widget);
            }
        }
        view.getTabBar().selectTab(tab);
    }

    private Layer makeTab(final TabData tab) {
        if (tab == WORDS) {
            wordsTab = own(wordsTabProvider.get());
            wordsTab.setOnChange(this::onChange);
            if (doc != null) {
                wordsTab.setWords(doc.getData());
            }
            return new ComponentLayer(wordsTab);
        } else if (tab == IMPORTS) {
            importsTab = own(importsTabProvider.get());
            importsTab.setOnChange(this::onChange);
            if (doc != null) {
                importsTab.read(docRef, doc);
            }
            return new ComponentLayer(importsTab);
        } else if (tab == EFFECTIVE_WORDS) {
            effectiveWordsList = own(effectiveWordsListProvider.get());
            effectiveWordsList.setDocRef(docRef);
            return new ComponentLayer(effectiveWordsList);
        } else if (tab == DOCUMENTATION) {
            documentation = own(PresenterAdapter.of(markdownEditPresenterProvider::get)
                    .onReadOnly(MarkdownEditPresenter::setReadOnly));
            own(documentation.getPresenter().addDirtyHandler(event -> onChange()));
            if (doc != null) {
                documentation.getPresenter().setText(doc.getDescription());
            }
            return documentation.getPresenter();
        } else if (tab == PERMISSIONS) {
            permissions = own(PresenterAdapter.of(permissionsPresenterProvider::get));
            if (docRef != null) {
                permissions.getPresenter().setDocRef(docRef);
            }
            return permissions.getPresenter();
        }
        throw new IllegalArgumentException("Unknown tab " + tab.getLabel());
    }

    private void onChange() {
        if (doc != null && !context(Contexts.READ_ONLY)) {
            setDirty(!Objects.equals(doc, write()));
        }
    }

    private void setDirty(final boolean dirty) {
        saveButton.setEnabled(dirty);
        if (this.dirty != dirty) {
            this.dirty = dirty;
            onDirtyChange.accept(dirty);
        }
    }

    private void save() {
        if (dirty) {
            onSave.run();
        }
    }

    private void saveAs() {
        if (doc != null) {
            onSaveAs.run();
        }
    }

    private void download() {
        final DocRef downloaded = docRef;
        restFactory
                .create(DICTIONARY_RESOURCE)
                .method(resource -> resource.download(downloaded))
                .onSuccess(guard(result -> ExportFileCompleteUtil.onSuccess(locationManager, bus, result)))
                .taskMonitorFactory(context(Contexts.TASK_MONITOR))
                .exec();
    }

    private void showReadOnly(final boolean readOnly) {
        saveButton.setTitle(readOnly
                ? "Save (read only)"
                : "Save");
        if (readOnly) {
            final SafeHtmlBuilder html = new SafeHtmlBuilder();
            html.appendHtmlConstant("<span class=\"docTab-readOnlyNote\">");
            html.appendHtmlConstant("<span class=\"docTab-readOnlyIcon\" aria-hidden=\"true\">");
            html.append(SafeHtmlUtil.getSafeHtmlFromSafeConstant(SvgImage.LOCKED.getSvg()));
            html.appendHtmlConstant("</span>");
            html.appendEscaped("Read only");
            html.appendHtmlConstant("</span>");
            // The reason, which a tooltip doesn't give screen readers
            html.appendHtmlConstant("<span class=\"sr-only\">");
            html.appendEscaped(". " + READ_ONLY_REASON + ".");
            html.appendHtmlConstant("</span>");
            readOnlyStatus.setHTML(html.toSafeHtml());
            readOnlyStatus.setTitle(READ_ONLY_REASON);
        } else {
            readOnlyStatus.setHTML("");
            readOnlyStatus.setTitle(null);
        }
        // The status takes room from the tab bar, so it lays its tabs out again
        view.getTabBar().refresh();
    }
}
