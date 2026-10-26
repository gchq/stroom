/*
 * Copyright 2016 Crown Copyright
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

package stroom.importexport.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.core.client.LocationManager;
import stroom.dispatch.client.ExportFileCompleteUtil;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.client.component.EntityCheckTreeController;
import stroom.explorer.client.component.ExplorerTree;
import stroom.explorer.client.presenter.DocumentTypeCache;
import stroom.explorer.client.presenter.TypeFilterPresenter;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.importexport.client.event.ExportConfigEvent;
import stroom.importexport.shared.ContentResource;
import stroom.quickfilter.client.QuickFilterContextHandlerFactory;
import stroom.security.shared.DocumentPermission;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.DocRefs;
import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarToggleButton;
import stroom.widget.dropdowntree.client.view.QuickFilterContextHandler;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.presenter.client.HasUiHandlers;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class ExportConfigPresenter
        extends MyPresenterWidget<ExportConfigPresenter.ExportConfigView>
        implements ExportConfigUiHandlers, ExportConfigEvent.Handler {

    private static final ContentResource CONTENT_RESOURCE = GWT.create(ContentResource.class);

    private final LocationManager locationManager;
    private final EntityCheckTreeController tree;
    private final TypeFilterPresenter typeFilterPresenter;
    private final RestFactory restFactory;
    private final DocumentTypeCache documentTypeCache;

    private final ToolbarToggleButton filter;
    private boolean hasActiveFilter = false;

    @Inject
    public ExportConfigPresenter(final EventBus eventBus,
                                 final ExportConfigView view,
                                 final LocationManager locationManager,
                                 final Provider<EntityCheckTreeController> entityCheckTreeControllerProvider,
                                 final TypeFilterPresenter typeFilterPresenter,
                                 final RestFactory restFactory,
                                 final DocumentTypeCache documentTypeCache,
                                 final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory) {
        super(eventBus, view);
        own(typeFilterPresenter);
        this.locationManager = locationManager;
        this.tree = make(entityCheckTreeControllerProvider).attach(view.getTree());
        this.typeFilterPresenter = typeFilterPresenter;
        this.restFactory = restFactory;
        this.documentTypeCache = documentTypeCache;

        filter = getView().getToolbar().toggle(SvgImage.FILTER, "Filter Document Types", null);
        filter.setPressed(hasActiveFilter);

        tree.setFilterErrorConsumer(view::setFilterError);
        view.setQuickFilterContextHandler(quickFilterContextHandlerFactory.create(
                ExplorerTreeFilter.QUICK_FILTER_CONTEXT, this, this));
        view.setUiHandlers(this);

        // Only show the System node at the root for export
        tree.setIncludedRootTypes(ExplorerConstants.SYSTEM_TYPE);

        own(typeFilterPresenter.addSelectionListener(event -> tree.setIncludedTypeSet(
                typeFilterPresenter.getIncludedTypes().orElse(null))));

        own(filter.addClickHandler((e) ->
                showTypeFilter(filter.getElement())));
    }

    public void showTypeFilter(final Element target) {
        // Override the default behaviour of the toggle button as we only want
        // it to be ON if a filter has been set, not just when clicked
        filter.setState(hasActiveFilter);
        typeFilterPresenter.show(target, this::setFilterState);
    }

    private void setFilterState(final boolean hasActiveFilter) {
        this.hasActiveFilter = hasActiveFilter;
        filter.setState(hasActiveFilter);
    }

    @Override
    public void onExport(final ExportConfigEvent event) {
        if (event.getSelection() != null) {
            for (final ExplorerNode node : event.getSelection()) {
                tree.getTreeController().setEnsureVisible(new HashSet<>(event.getSelection()));
                tree.setSelected(node, true);
            }
        }

        show();
    }

    private void show() {
        final PopupSize popupSize = PopupSize.resizable(400, 600, 380, 480);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Export")
                .onShow(e -> onShow())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        export(e);
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    private void onShow() {
        documentTypeCache.clear();
        // Set the data for the type filter.
        documentTypeCache.fetch(typeFilterPresenter::setDocumentTypes, this);

        tree.setRequiredPermissions(DocumentPermission.VIEW);
        tree.refresh();

        getView().focus();
    }

    private void export(final HidePopupRequestEvent event) {
//        // Disable the popup ok/cancel buttons before we attempt export.
//        DisablePopupEvent.builder(this).fire();

        final Set<ExplorerNode> dataItems = tree.getSelectedSet();
        if (dataItems == null || dataItems.size() == 0) {
            // Let the user know that they didn't select anything to export.
            AlertEvent.fireWarn(this, "No folders have been selected for export", event::reset);
//            // Re-enable buttons on this popup.
//            EnablePopupEvent.builder(this).fire();

        } else {
            final Set<DocRef> docRefs = new HashSet<>();
            for (final ExplorerNode explorerNode : dataItems) {
                docRefs.add(explorerNode.getDocRef());
            }

            restFactory
                    .create(CONTENT_RESOURCE)
                    .method(res -> res.exportContent(new DocRefs(docRefs)))
                    .onSuccess(result -> {
                        ExportFileCompleteUtil.onSuccess(locationManager, this, result);
                        event.hide();
                    })
                    .onFailure(RestErrorHandler.forPopup(this, event))
                    .taskMonitorFactory(this)
                    .exec();
        }
    }

    @Override
    public void changeQuickFilter(final String name) {
        tree.changeNameFilter(name);
    }

    @Override
    public void showTypeFilter(final MouseDownEvent event,
                               final Consumer<Boolean> filterStateConsumer) {
        final Element target = event.getNativeEvent().getEventTarget().cast();
        typeFilterPresenter.show(target, filterStateConsumer);
    }

    // --------------------------------------------------------------------------------


    public interface ExportConfigView extends View, Focus, HasUiHandlers<ExportConfigUiHandlers> {

        /// @return The tree of items to tick, for the presenter to attach its controller to.
        ExplorerTree getTree();

        /**
         * Show why the server rejected the filter, or clear it when passed null.
         * See {@code FetchExplorerNodeResult.filterError}.
         */
        void setFilterError(String filterError);

        void setQuickFilterContextHandler(QuickFilterContextHandler contextHandler);

        /// @return The dialog's toolbar, for its buttons.
        Toolbar getToolbar();
    }


    // --------------------------------------------------------------------------------

}
