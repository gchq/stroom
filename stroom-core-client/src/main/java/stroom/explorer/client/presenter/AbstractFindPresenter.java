/*
 * Copyright 2023 Crown Copyright
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

package stroom.explorer.client.presenter;

import stroom.document.client.event.OpenDocumentEvent;
import stroom.explorer.client.component.FindDocResultListController;
import stroom.explorer.client.presenter.AbstractFindPresenter.FindView;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.explorer.shared.FindResult;
import stroom.quickfilter.client.QuickFilterContextHandlerFactory;
import stroom.widget.dropdowntree.client.view.QuickFilterContextHandler;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.presenter.client.HasUiHandlers;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Supplier;

public abstract class AbstractFindPresenter
        extends MyPresenterWidget<FindView>
        implements FindUiHandlers {

    private final FindDocResultListController findResultList;

    public AbstractFindPresenter(final EventBus eventBus,
                                 final FindView view,
                                 final FindDocResultListController findResultList,
                                 final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory) {
        super(eventBus, view);
        own(findResultList);
        this.findResultList = findResultList;
        // The find dialog's text is parsed by NodeInclusionChecker against the explorer tree's
        // fields, so it shares the tree's history.
        view.setQuickFilterContextHandler(quickFilterContextHandlerFactory.create(
                ExplorerTreeFilter.QUICK_FILTER_CONTEXT, this, this));

        view.setResultView(findResultList);
        view.setUiHandlers(this);
        findResultList.setOnOpen(this::openDocument);
        findResultList.setOnFocusFilter(this::focus);
        // A rejected filter comes back as an empty page like any other, so surface the reason on
        // the filter box - and, through it, tell the history what was accepted.
        findResultList.setOnFilterError(view::setFilterError);
    }

    /// Opens a document found and closes the dialog.
    ///
    /// @param match The document, or `null` for none.
    public void openDocument(final FindResult match) {
        if (match != null) {
            OpenDocumentEvent.fire(this, match.getDocRef(), true);
            hide();
        }
    }

    /// Puts the focus on the filter.
    public void focus() {
        getView().focus();
    }

    @Override
    public void changeQuickFilter(final String name) {
        if (findResultList.getExplorerTreeFilterBuilder().setNameFilter(name)) {
            refresh();
        }
    }

    @Override
    public void onFilterKeyDown(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            openDocument(findResultList.getSelected());
        } else if (event.getNativeKeyCode() == KeyCodes.KEY_DOWN) {
            findResultList.setKeyboardSelectedRow(0, true);
        }
    }

    public void refresh() {
        findResultList.refresh();
    }

    private void hide() {
        HidePopupRequestEvent.builder(this).fire();
    }

    public FindDocResultListController getFindResultList() {
        return findResultList;
    }

    public interface FindView extends View, Focus, HasUiHandlers<FindUiHandlers> {

        /**
         * Show why the server rejected the filter, or clear it when passed null.
         * See {@code ResultPage.filterError}.
         */
        void setFilterError(String filterError);

        void setQuickFilterContextHandler(QuickFilterContextHandler contextHandler);

        void setResultView(IsWidget view);

        void setDialogMode(boolean dialog);

        /// Says what the filter is for and what may be typed in it, for screen readers.
        ///
        /// @param accessibleName What the filter is for, e.g. `Find by name`.
        /// @param description    What may be typed, or null for nothing.
        void setFilterGuidance(String accessibleName, String description);

        /// Sets the help shown by the filter's syntax help button.
        ///
        /// @param popupTextSupplier Supplies the help.
        void registerPopupTextProvider(Supplier<SafeHtml> popupTextSupplier);
    }
}
