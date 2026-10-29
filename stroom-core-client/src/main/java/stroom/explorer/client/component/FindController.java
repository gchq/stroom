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

package stroom.explorer.client.component;

import stroom.document.client.event.OpenDocumentEvent;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.explorer.shared.FindResult;
import stroom.quickfilter.client.QuickFilterContextHandlerFactory;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.popup.client.DialogAction;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.quickfilter.client.QuickFilterTooltipUtil;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// The Find dialog ([FindDocuments]): finds documents by name, and opens the one chosen. Made once by
/// the app and shown again, it keeps the last filter.
public class FindController extends Controller {

    // Read by a screen reader when the filter is focused; the syntax help has the full detail
    private static final String FILTER_DESCRIPTION = "Finds items whose name contains the text. "
                                                     + "Separate several terms with spaces. "
                                                     + "Use type:, uuid: or tag: to match other fields, "
                                                     + "e.g. type:Feed.";

    private final FindPanel view;
    private final FindDocResultListController findResultList;
    private boolean showing;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                         The application's event bus.
    /// @param content                          The content.
    /// @param findResultList                   Finds the documents, and lists them.
    /// @param quickFilterContextHandlerFactory Makes the filter's recent filters.
    /// @param uiConfigCache                    Gives the filter's help link.
    @Inject
    public FindController(final EventBus eventBus,
                          final FindDocuments content,
                          final FindDocResultListController findResultList,
                          final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory,
                          final UiConfigCache uiConfigCache) {
        this(eventBus, content, findResultList, quickFilterContextHandlerFactory);
        content.getFindPanel().setFilterGuidance("Find by name", FILTER_DESCRIPTION);
        // The same fields as the explorer tree's filter
        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null && !isDisposed()) {
                content.getFindPanel().registerPopupTextProvider(() -> QuickFilterTooltipUtil.createTooltip(
                        "Find Quick Filter",
                        ExplorerTreeFilter.FIELD_DEFINITIONS,
                        uiConfig.getHelpUrlQuickFilter()));
            }
        });
    }

    /// Made by a dialog that finds documents differently, e.g. Recent Items.
    ///
    /// @param eventBus                         The application's event bus.
    /// @param content                          The content.
    /// @param findResultList                   Finds the documents, and lists them.
    /// @param quickFilterContextHandlerFactory Makes the filter's recent filters.
    protected FindController(final EventBus eventBus,
                             final FindDocuments content,
                             final FindDocResultListController findResultList,
                             final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory) {
        super(eventBus);
        initWidget(content);
        this.view = content.getFindPanel();
        this.findResultList = own(findResultList).attach(content.getResultList());
        // The find dialog's text is parsed by NodeInclusionChecker against the explorer tree's
        // fields, so it shares the tree's history.
        view.setQuickFilterContextHandler(quickFilterContextHandlerFactory.create(
                ExplorerTreeFilter.QUICK_FILTER_CONTEXT, this, () -> taskMonitor().createTaskMonitor()));
        view.setOnFilterChange(this::changeQuickFilter);
        view.setOnFilterKeyDown(this::onFilterKeyDown);
        findResultList.setOnOpen(this::openDocument);
        findResultList.setOnFocusFilter(view::focus);
        // A rejected filter comes back as an empty page like any other, so surface the reason on
        // the filter box - and, through it, tell the history what was accepted.
        findResultList.setOnFilterError(view::setFilterError);
    }

    /// Shows the dialog, unless it is shown already, finding with the filter it last had.
    public void show() {
        if (!showing) {
            showing = true;

            // Make sure we are set to focus text next time we show.
            findResultList.setFocusText(true);

            final ExplorerTreeFilter.Builder explorerTreeFilterBuilder = findResultList.getExplorerTreeFilterBuilder();
            setUpFilter(explorerTreeFilterBuilder);
            explorerTreeFilterBuilder.setNameFilter(
                    explorerTreeFilterBuilder.build().getNameFilter(),
                    true);
            findResultList.refresh();

            ControllerDialog.show(this)
                    .popupType(PopupType.CLOSE_DIALOG)
                    .popupSize(getPopupSize())
                    .caption(getCaption())
                    .onShow(e -> view.focus())
                    .onHideRequest(HidePopupRequestEvent::hide)
                    .onHide(e -> showing = false)
                    .fire();
        }
    }

    /// Sets up what is found before the dialog is shown.
    ///
    /// @param explorerTreeFilterBuilder The filter, kept between showings.
    protected void setUpFilter(final ExplorerTreeFilter.Builder explorerTreeFilterBuilder) {
        // Don't want favourites in the results as they are effectively duplicates
        explorerTreeFilterBuilder.includedRootTypes(ExplorerConstants.SYSTEM_TYPE);
    }

    /// @return The dialog's caption.
    protected String getCaption() {
        return "Find";
    }

    /// @return The dialog's size.
    protected PopupSize getPopupSize() {
        return PopupSize.resizable(800, 600);
    }

    private void openDocument(final FindResult match) {
        if (match != null) {
            OpenDocumentEvent.fire(this, match.getDocRef(), true);
            ControllerDialog.pressAction(this, DialogAction.CLOSE);
        }
    }

    private void changeQuickFilter(final String name) {
        if (findResultList.getExplorerTreeFilterBuilder().setNameFilter(name)) {
            findResultList.refresh();
        }
    }

    private void onFilterKeyDown(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            openDocument(findResultList.getSelected());
        } else if (event.getNativeKeyCode() == KeyCodes.KEY_DOWN) {
            findResultList.setKeyboardSelectedRow(0, true);
        }
    }

}
