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

package stroom.entity.client.component;

import stroom.docref.DocRef;
import stroom.entity.client.presenter.HasToolbar;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.component.client.ComponentLayer;
import stroom.widget.component.client.ContextValue;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.PresenterAdapter;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// A document's editor: its tabs, its toolbar with Save and Save As, whether it is read only, and
/// whether it has changes that aren't saved. A document type's editor adds its tabs
/// ([#addTab(TabData, Supplier, TabReader, BiFunction)]) and any buttons of its own
/// ([#getToolbar()]).
///
/// Its owner reads the document in ([#read(DocRef, Object)]), says whether the user may change it
/// ([#setReadOnly(boolean)]), which every tab follows through [Contexts#READ_ONLY], and saves it
/// when asked ([#setOnSave(Runnable)], [#write()]). Each tab is made, and owned, when it is first
/// shown; tabs tell the editor of changes through [#onChange()], and the editor works out whether
/// the document is dirty by comparing it with what the tabs write.
///
/// @param <D> The document's type, which must compare by value (`equals`).
public abstract class DocumentEditor<D> extends Component {

    // Why a document is read only: it is only ever opened read only for a user who can't edit it
    private static final String READ_ONLY_REASON = "You don't have permission to change this";

    private final LinkTabPanelView view;
    private final ContextValue<Boolean> readOnly = ContextValue.of(true);
    private final Toolbar toolbar = new Toolbar("Document actions");
    private final ToolbarButton saveButton;
    private final ToolbarButton saveAsButton;
    // Says that the document is read only, at the far right of the tab bar; a polite status, so
    // screen readers announce it (and why) when a read-only document opens
    private final HTML readOnlyStatus = new HTML();
    // The buttons the tab showing added to the toolbar, removed when another tab is shown
    private final List<Widget> tabButtons = new ArrayList<>();
    private final Map<TabData, Tab<?>> tabs = new LinkedHashMap<>();

    private DocRef docRef;
    private D document;
    private boolean dirty;
    private Consumer<Boolean> onDirtyChange = isDirty -> {
    };
    private Runnable onSave = () -> {
    };
    private Runnable onSaveAs = () -> {
    };

    /// @param eventBus The application's event bus.
    /// @param view     The tab bar, toolbar row and tab content.
    protected DocumentEditor(final EventBus eventBus, final LinkTabPanelView view) {
        super(eventBus);
        this.view = view;
        provide(Contexts.READ_ONLY, readOnly);

        saveButton = toolbar.button(SvgImage.SAVE, "Save", this::save);
        saveButton.setEnabled(false);
        saveAsButton = toolbar.button(SvgImage.SAVEAS, "Save As", this::saveAs);
        saveAsButton.setEnabled(false);

        readOnlyStatus.setStyleName("docTab-readOnlyStatus");
        readOnlyStatus.getElement().setAttribute("role", "status");
        view.setTabBarStatus(readOnlyStatus);
        view.getTabBar().addSelectionHandler(event -> selectTab(event.getSelectedItem()));
        initWidget(view.asWidget());

        watch(Contexts.READ_ONLY, this::showReadOnly);
    }

    /// Reads a document into the editor and its tabs, which then show it as clean.
    ///
    /// @param docRef   The document's reference.
    /// @param document The document.
    public final void read(final DocRef docRef, final D document) {
        this.docRef = Objects.requireNonNull(docRef, "docRef");
        this.document = Objects.requireNonNull(document, "document");
        for (final Tab<?> tab : tabs.values()) {
            tab.read(docRef, document);
        }
        saveAsButton.setEnabled(true);
        onRead(docRef, document);
        setDirty(false);
    }

    /// @param readOnly Whether the user may not change the document. Every tab follows it.
    public final void setReadOnly(final boolean readOnly) {
        this.readOnly.set(readOnly);
    }

    /// @return The document as the tabs show it, e.g. to save it, or null before it is read.
    public final D write() {
        D written = document;
        for (final Tab<?> tab : tabs.values()) {
            written = tab.write(written);
        }
        return written;
    }

    /// @return The document's reference, or null before it is read.
    public final DocRef getDocRef() {
        return docRef;
    }

    /// @return Whether the tabs show changes that aren't saved.
    public final boolean isDirty() {
        return dirty;
    }

    /// @param onDirtyChange What to run when the document becomes dirty or clean, e.g. to mark
    ///                      its tab's label.
    public final void setOnDirtyChange(final Consumer<Boolean> onDirtyChange) {
        this.onDirtyChange = Objects.requireNonNull(onDirtyChange, "onDirtyChange");
    }

    /// @param onSave What to run when the user asks to save the document.
    public final void setOnSave(final Runnable onSave) {
        this.onSave = Objects.requireNonNull(onSave, "onSave");
    }

    /// @param onSaveAs What to run when the user asks to save a copy of the document.
    public final void setOnSaveAs(final Runnable onSaveAs) {
        this.onSaveAs = Objects.requireNonNull(onSaveAs, "onSaveAs");
    }

    /// Adds a tab that only shows the document, e.g. its permissions.
    ///
    /// @param tab    The tab.
    /// @param make   Makes its content, when it is first shown.
    /// @param reader Reads the document into it.
    /// @param <C>    Its content's type.
    protected final <C extends Component> void addTab(final TabData tab,
                                                      final Supplier<C> make,
                                                      final TabReader<C, D> reader) {
        addTab(tab, make, reader, (content, document) -> document);
    }

    /// Adds a tab that shows and changes part of the document. Its content tells the editor of
    /// changes by calling [#onChange()].
    ///
    /// @param tab    The tab.
    /// @param make   Makes its content, when it is first shown.
    /// @param reader Reads the document into it.
    /// @param writer Writes what it shows into a copy of the document.
    /// @param <C>    Its content's type.
    protected final <C extends Component> void addTab(final TabData tab,
                                                      final Supplier<C> make,
                                                      final TabReader<C, D> reader,
                                                      final BiFunction<C, D, D> writer) {
        tabs.put(tab, new Tab<>(make, reader, writer));
        view.getTabBar().addTab(tab);
    }

    /// Shows a tab, making its content if it is the first time.
    ///
    /// @param tab The tab.
    protected final void selectTab(final TabData tab) {
        final Tab<?> selected = Objects.requireNonNull(tabs.get(tab), "tab");
        view.getLayerContainer().show(selected.getLayer());

        // The tab's own buttons join the document's toolbar, and its other toolbars follow it
        for (final Widget widget : tabButtons) {
            toolbar.remove(widget);
        }
        tabButtons.clear();
        view.clearToolbar();
        view.addToolbar(toolbar);
        final HasToolbar hasToolbar = selected.getToolbar();
        if (hasToolbar != null) {
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

    /// Called by a tab's content when the user changes what it shows, so the editor can tell
    /// whether the document is dirty.
    protected final void onChange() {
        if (document != null && !context(Contexts.READ_ONLY)) {
            setDirty(!Objects.equals(document, write()));
        }
    }

    /// @return The document's toolbar, to add the document type's own buttons to after Save and
    /// Save As.
    protected final Toolbar getToolbar() {
        return toolbar;
    }

    /// Called once a document is read into the tabs, e.g. to enable the type's own buttons.
    ///
    /// @param docRef   The document's reference.
    /// @param document The document.
    protected void onRead(final DocRef docRef, final D document) {
        // Nothing by default
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
        if (document != null) {
            onSaveAs.run();
        }
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

    /// Reads a document into a tab's content.
    ///
    /// @param <C> The content's type.
    /// @param <D> The document's type.
    @FunctionalInterface
    public interface TabReader<C, D> {

        /// @param content  The tab's content.
        /// @param docRef   The document's reference.
        /// @param document The document.
        void read(C content, DocRef docRef, D document);
    }

    // A tab: how to make its content, read into it and write from it, and the content once made
    private final class Tab<C extends Component> {

        private final Supplier<C> make;
        private final TabReader<C, D> reader;
        private final BiFunction<C, D, D> writer;
        private C content;
        private ComponentLayer layer;

        private Tab(final Supplier<C> make, final TabReader<C, D> reader, final BiFunction<C, D, D> writer) {
            this.make = make;
            this.reader = reader;
            this.writer = writer;
        }

        private ComponentLayer getLayer() {
            if (content == null) {
                content = own(make.get());
                layer = new ComponentLayer(content);
                if (document != null) {
                    reader.read(content, docRef, document);
                }
            }
            return layer;
        }

        private void read(final DocRef docRef, final D document) {
            if (content != null) {
                reader.read(content, docRef, document);
            }
        }

        private D write(final D document) {
            return content == null
                    ? document
                    : writer.apply(content, document);
        }

        // What adds to the toolbar row: the content, or the presenter an adapter shows
        private HasToolbar getToolbar() {
            if (content instanceof final HasToolbar hasToolbar) {
                return hasToolbar;
            } else if (content instanceof final PresenterAdapter<?> adapter
                       && adapter.getPresenter() instanceof final HasToolbar hasToolbar) {
                return hasToolbar;
            }
            return null;
        }
    }
}
