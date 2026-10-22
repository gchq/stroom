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

import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.ContextValue;
import stroom.widget.component.client.Contexts;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.client.ui.HTML;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.Consumer;

/// A document's editor: its tabs (see [TabbedComponent]), its toolbar with Save and Save As,
/// whether it is read only, and whether it has changes that aren't saved. A document type's editor
/// adds its tabs (`addTab`) and any buttons of its own ([#getToolbar()]).
///
/// Its owner reads the document in ([#read]), says whether the user may change it
/// ([#setReadOnly(boolean)]), which every tab follows through [Contexts#READ_ONLY], and saves it
/// when asked ([#setOnSave(Runnable)], [#write()]). In Stroom the owner is a
/// [DocumentEditorHost], as `DocumentPlugin` opens it. Tabs tell the editor of changes through
/// [#onChange()], and the editor works out whether the document is dirty by comparing it with what
/// the tabs write.
///
/// @param <D> The document's type, which must compare by value (`equals`).
public abstract class DocumentEditor<D> extends TabbedComponent<D> {

    // Why a document is read only: it is only ever opened read only for a user who can't edit it
    private static final String READ_ONLY_REASON = "You don't have permission to change this";

    private final ContextValue<Boolean> readOnly = ContextValue.of(true);
    private final ToolbarButton saveButton;
    private final ToolbarButton saveAsButton;
    // Says that the document is read only, at the far right of the tab bar; a polite status, so
    // screen readers announce it (and why) when a read-only document opens
    private final HTML readOnlyStatus = new HTML();

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
        super(eventBus, view);
        provide(Contexts.READ_ONLY, readOnly);

        saveButton = getToolbar().button(SvgImage.SAVE, "Save", this::save);
        saveButton.setEnabled(false);
        saveAsButton = getToolbar().button(SvgImage.SAVEAS, "Save As", this::saveAs);
        saveAsButton.setEnabled(false);

        readOnlyStatus.setStyleName("docTab-readOnlyStatus");
        readOnlyStatus.getElement().setAttribute("role", "status");
        view.setTabBarStatus(readOnlyStatus);

        watch(Contexts.READ_ONLY, this::showReadOnly);
    }

    /// @param readOnly Whether the user may not change the document. Every tab follows it.
    public final void setReadOnly(final boolean readOnly) {
        this.readOnly.set(readOnly);
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

    /// Called by a tab's content when the user changes what it shows, so the editor can tell
    /// whether the document is dirty.
    protected final void onChange() {
        if (getDocument() != null && !context(Contexts.READ_ONLY)) {
            setDirty(!Objects.equals(getDocument(), write()));
        }
    }

    @Override
    void afterRead() {
        saveAsButton.setEnabled(true);
        setDirty(false);
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
        if (getDocument() != null) {
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
        getTabView().getTabBar().refresh();
    }
}
