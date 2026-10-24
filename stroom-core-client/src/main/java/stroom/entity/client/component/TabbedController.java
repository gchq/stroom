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
import stroom.document.client.event.OpenDocumentEvent.CommonDocLinkTab;
import stroom.entity.client.presenter.HasToolbar;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerLayer;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.component.client.PresenterAdapter;
import stroom.widget.tab.client.presenter.TabData;

import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/// Something shown in tabs, e.g. a document ([DocumentEditor]) or a folder: a tab bar, a row for
/// toolbars, and the tab showing. Each tab's content, a controller or a presenter, is made and owned
/// when the tab is first shown, is read into whenever this is ([#read(DocRef, Object)]), and may
/// write what it shows back ([#write()]).
///
/// The toolbar row shows this controller's toolbar ([#getToolbar()]) if it has any buttons, with
/// those of the tab showing if its content is a [HasToolbar], and the content's other toolbars.
///
/// @param <D> The type of what is shown, e.g. a document's, which must compare by value (`equals`)
///            if it is written.
public abstract class TabbedController<D> extends Controller {

    private final LinkTabPanelView view;
    private final Toolbar toolbar = new Toolbar("Document actions");
    // The buttons the tab showing added to the toolbar, removed when another tab is shown
    private final List<Widget> tabButtons = new ArrayList<>();
    private final Map<TabData, Tab<?>> tabs = new LinkedHashMap<>();
    // The tabs a link can open directly (its Documentation, its Permissions)
    private final Map<CommonDocLinkTab, TabData> commonTabs = new EnumMap<>(CommonDocLinkTab.class);

    private DocRef docRef;
    private D document;

    /// @param eventBus The application's event bus.
    /// @param view     The tab bar, toolbar row and tab content.
    protected TabbedController(final EventBus eventBus, final LinkTabPanelView view) {
        super(eventBus);
        this.view = view;
        view.getTabBar().addSelectionHandler(event -> selectTab(event.getSelectedItem()));
        initWidget(view.asWidget());
    }

    /// Reads what to show into this and its tabs.
    ///
    /// @param docRef   The reference of what is shown, e.g. the document's.
    /// @param document What is shown.
    public final void read(final DocRef docRef, final D document) {
        this.docRef = Objects.requireNonNull(docRef, "docRef");
        this.document = Objects.requireNonNull(document, "document");
        for (final Tab<?> tab : tabs.values()) {
            tab.read(docRef, document);
        }
        onRead(docRef, document);
        afterRead();
    }

    /// @return What is shown, as the tabs show it (e.g. to save it), or null before it is read.
    public final D write() {
        return write(document);
    }

    /// Writes what the tabs show into a document, e.g. a new one when saving a copy.
    ///
    /// @param document The document to write into, or null.
    /// @return A copy of it with what the tabs show, or null if it is null.
    public final D write(final D document) {
        if (document == null) {
            return null;
        }
        D written = document;
        for (final Tab<?> tab : tabs.values()) {
            written = tab.write(written);
        }
        return written;
    }

    /// @return What is shown, as it was read, or null before it is read.
    public final D getDocument() {
        return document;
    }

    /// @return The reference of what is shown, or null before it is read.
    public final DocRef getDocRef() {
        return docRef;
    }

    /// Shows one of the tabs a link can open directly, if this has it.
    ///
    /// @param commonDocLinkTab The tab, or null for none.
    public final void selectCommonTab(final CommonDocLinkTab commonDocLinkTab) {
        if (commonDocLinkTab != null) {
            final TabData tab = commonTabs.get(commonDocLinkTab);
            if (tab != null) {
                selectTab(tab);
            }
        }
    }

    /// Adds a tab that only shows what is read, e.g. a document's permissions.
    ///
    /// @param tab    The tab.
    /// @param make   Makes its content, a controller or a presenter, when it is first shown.
    /// @param reader Reads what is shown into it.
    /// @param <C>    Its content's type.
    protected final <C extends HasControllerNode & IsWidget> void addTab(final TabData tab,
                                                                      final Supplier<C> make,
                                                                      final TabReader<C, D> reader) {
        addTab(tab, make, reader, (content, document) -> document);
    }

    /// Adds a tab that shows and changes part of what is read.
    ///
    /// @param tab    The tab.
    /// @param make   Makes its content, a controller or a presenter, when it is first shown.
    /// @param reader Reads what is shown into it.
    /// @param writer Writes what it shows into a copy of what is shown.
    /// @param <C>    Its content's type.
    protected final <C extends HasControllerNode & IsWidget> void addTab(final TabData tab,
                                                                      final Supplier<C> make,
                                                                      final TabReader<C, D> reader,
                                                                      final BiFunction<C, D, D> writer) {
        tabs.put(tab, new Tab<>(make, reader, writer));
        view.getTabBar().addTab(tab);
    }

    /// Hides or shows a tab in the tab bar.
    ///
    /// @param tab    The tab.
    /// @param hidden Whether to hide it.
    protected final void setTabHidden(final TabData tab, final boolean hidden) {
        view.getTabBar().setTabHidden(tab, hidden);
    }

    /// Says which tab a link to the Documentation or Permissions opens.
    ///
    /// @param commonDocLinkTab What the link is to.
    /// @param tab              The tab, added with `addTab`.
    protected final void setCommonTab(final CommonDocLinkTab commonDocLinkTab, final TabData tab) {
        if (!tabs.containsKey(tab)) {
            throw new IllegalArgumentException("No tab " + tab.getLabel());
        }
        commonTabs.put(commonDocLinkTab, tab);
    }

    /// Shows a tab, making its content if it is the first time.
    ///
    /// @param tab The tab.
    protected final void selectTab(final TabData tab) {
        final Tab<?> selected = Objects.requireNonNull(tabs.get(tab), "tab");
        view.getLayerContainer().show(selected.getLayer());

        // The tab's own buttons join the toolbar, and its other toolbars follow it
        for (final Widget widget : tabButtons) {
            toolbar.remove(widget);
        }
        tabButtons.clear();
        view.clearToolbar();
        final HasToolbar hasToolbar = selected.getToolbar();
        if (hasToolbar != null) {
            for (final Widget widget : hasToolbar.getDocumentButtons()) {
                toolbar.add(widget);
                tabButtons.add(widget);
            }
        }
        if (toolbar.getWidgetCount() > 0) {
            view.addToolbar(toolbar);
        }
        if (hasToolbar != null) {
            for (final Widget widget : hasToolbar.getToolbars()) {
                view.addToolbar(widget);
            }
        }
        view.getTabBar().selectTab(tab);
    }

    /// @return The toolbar, at the start of the toolbar row, to add buttons to; the tab showing
    /// adds its own after them.
    protected final Toolbar getToolbar() {
        return toolbar;
    }

    /// @return The tab bar, toolbar row and tab content.
    protected final LinkTabPanelView getTabView() {
        return view;
    }

    /// Called once what is shown is read into the tabs, e.g. to enable buttons.
    ///
    /// @param docRef   The reference of what is shown.
    /// @param document What is shown.
    protected void onRead(final DocRef docRef, final D document) {
        // Nothing by default
    }

    // For DocumentEditor, after onRead
    void afterRead() {
        // Nothing by default
    }

    /// Reads what is shown into a tab's content.
    ///
    /// @param <C> The content's type.
    /// @param <D> The type of what is shown.
    @FunctionalInterface
    public interface TabReader<C, D> {

        /// @param content  The tab's content.
        /// @param docRef   The reference of what is shown.
        /// @param document What is shown.
        void read(C content, DocRef docRef, D document);
    }

    // A tab: how to make its content, read into it and write from it, and the content once made
    private final class Tab<C extends HasControllerNode & IsWidget> {

        private final Supplier<C> make;
        private final TabReader<C, D> reader;
        private final BiFunction<C, D, D> writer;
        private C content;
        private ControllerLayer layer;

        private Tab(final Supplier<C> make, final TabReader<C, D> reader, final BiFunction<C, D, D> writer) {
            this.make = make;
            this.reader = reader;
            this.writer = writer;
        }

        private ControllerLayer getLayer() {
            if (content == null) {
                content = own(make.get());
                layer = new ControllerLayer(content);
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
