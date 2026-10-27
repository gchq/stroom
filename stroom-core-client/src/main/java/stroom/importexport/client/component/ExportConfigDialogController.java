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

package stroom.importexport.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.core.client.LocationManager;
import stroom.dispatch.client.ExportFileCompleteUtil;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.client.component.EntityCheckTreeController;
import stroom.explorer.client.component.TypeFilterController;
import stroom.explorer.client.presenter.DocumentTypeCache;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.importexport.shared.ContentResource;
import stroom.quickfilter.client.QuickFilterContextHandlerFactory;
import stroom.security.shared.DocumentPermission;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.DocRefs;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/// The Export dialog ([ExportConfigDialog]), made for one use: the documents and folders ticked
/// are exported to a file the browser downloads.
public class ExportConfigDialogController extends Controller {

    private static final ContentResource CONTENT_RESOURCE = GWT.create(ContentResource.class);

    private final ExportConfigDialog view;
    private final LocationManager locationManager;
    private final EntityCheckTreeController tree;
    private final TypeFilterController typeFilter;
    private final RestFactory restFactory;
    private final DocumentTypeCache documentTypeCache;
    private boolean hasActiveFilter;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                          The application's event bus.
    /// @param view                              The content.
    /// @param locationManager                   Downloads the export.
    /// @param entityCheckTreeControllerProvider Makes the controller of the tree to tick.
    /// @param typeFilter                        Filters the tree by type.
    /// @param restFactory                       Exports the items ticked.
    /// @param documentTypeCache                 Gives the type filter its types.
    /// @param quickFilterContextHandlerFactory  Makes the filter's recent filters.
    @Inject
    public ExportConfigDialogController(final EventBus eventBus,
                                        final ExportConfigDialog view,
                                        final LocationManager locationManager,
                                        final Provider<EntityCheckTreeController> entityCheckTreeControllerProvider,
                                        final TypeFilterController typeFilter,
                                        final RestFactory restFactory,
                                        final DocumentTypeCache documentTypeCache,
                                        final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory) {
        super(eventBus);
        this.view = view;
        this.locationManager = locationManager;
        this.typeFilter = own(typeFilter);
        this.restFactory = restFactory;
        this.documentTypeCache = documentTypeCache;
        initWidget(view);
        tree = make(entityCheckTreeControllerProvider).attach(view.getTree());

        tree.setOnFilterError(view::setFilterError);
        view.setQuickFilterContextHandler(quickFilterContextHandlerFactory.create(
                ExplorerTreeFilter.QUICK_FILTER_CONTEXT, this, () -> taskMonitor().createTaskMonitor()));
        view.setOnFilterChange(tree::changeNameFilter);
        view.setOnFilterTypes(this::showTypeFilter);

        // Only show the System node at the root for export
        tree.setIncludedRootTypes(ExplorerConstants.SYSTEM_TYPE);
        own(typeFilter.addSelectionListener(includedTypes -> tree.setIncludedTypeSet(includedTypes.orElse(null))));
    }

    /// Shows the dialog, owned by `opener` until it is closed, with the items given ticked.
    ///
    /// @param opener    What shows it.
    /// @param selection The items to tick, or null for none.
    public void show(final HasControllerNode opener, final List<ExplorerNode> selection) {
        if (selection != null) {
            for (final ExplorerNode node : selection) {
                tree.getTreeController().setEnsureVisible(new HashSet<>(selection));
                tree.setSelected(node, true);
            }
        }

        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(400, 600, 380, 480))
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

    private void showTypeFilter() {
        // Override the default behaviour of the toggle button as we only want it to be ON if a
        // filter has been set, not just when clicked
        view.setTypesFiltered(hasActiveFilter);
        typeFilter.show(view.getFilterTypesElement(), filtered -> {
            hasActiveFilter = filtered;
            view.setTypesFiltered(filtered);
        });
    }

    private void onShow() {
        documentTypeCache.clear();
        // Set the data for the type filter.
        documentTypeCache.fetch(typeFilter::setDocumentTypes, taskMonitor());

        tree.setRequiredPermissions(DocumentPermission.VIEW);
        tree.refresh();

        view.focus();
    }

    private void export(final HidePopupRequestEvent event) {
        final Set<ExplorerNode> dataItems = tree.getSelectedSet();
        if (dataItems == null || dataItems.isEmpty()) {
            // Let the user know that they didn't select anything to export.
            AlertEvent.fireWarn(this, "No folders have been selected for export", event::reset);
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
                    .owner(this)
                    .exec();
        }
    }

    // The dialog's task monitor, else Stroom's own busy indicator
    private TaskMonitorFactory taskMonitor() {
        final TaskMonitorFactory provided = getProvidedTaskMonitor();
        return provided != null
                ? provided
                : new DefaultTaskMonitorFactory(this);
    }
}
