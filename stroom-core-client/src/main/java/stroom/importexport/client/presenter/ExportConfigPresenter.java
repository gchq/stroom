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

package stroom.importexport.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.core.client.LocationManager;
import stroom.dispatch.client.ExportFileCompleteUtil;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.client.presenter.DocumentTypeCache;
import stroom.explorer.client.presenter.EntityCheckTreePresenter;
import stroom.explorer.client.presenter.TypeFilterPresenter;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.importexport.client.event.ExportConfigEvent;
import stroom.importexport.shared.ContentResource;
import stroom.security.shared.DocumentPermission;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.DocRefs;
import stroom.widget.button.client.InlineSvgToggleButton;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class ExportConfigPresenter
        extends MyPresenter<ExportConfigPresenter.ExportConfigView, ExportConfigPresenter.ExportProxy>
        implements ExportConfigUiHandlers, ExportConfigEvent.Handler {

    private static final ContentResource CONTENT_RESOURCE = GWT.create(ContentResource.class);

    private final LocationManager locationManager;
    private final EntityCheckTreePresenter treePresenter;
    private final TypeFilterPresenter typeFilterPresenter;
    private final RestFactory restFactory;
    private final DocumentTypeCache documentTypeCache;

    private final InlineSvgToggleButton filter;
    private boolean hasActiveFilter = false;

    @Inject
    public ExportConfigPresenter(final EventBus eventBus,
                                 final ExportConfigView view,
                                 final ExportProxy proxy,
                                 final LocationManager locationManager,
                                 final EntityCheckTreePresenter treePresenter,
                                 final TypeFilterPresenter typeFilterPresenter,
                                 final RestFactory restFactory,
                                 final DocumentTypeCache documentTypeCache) {
        super(eventBus, view, proxy);
        this.locationManager = locationManager;
        this.treePresenter = treePresenter;
        this.typeFilterPresenter = typeFilterPresenter;
        this.restFactory = restFactory;
        this.documentTypeCache = documentTypeCache;

        filter = new InlineSvgToggleButton();
        filter.setState(hasActiveFilter);
        filter.setSvg(SvgImage.FILTER);
        filter.getElement().addClassName("navigation-header-button filter");
        filter.setTitle("Filter Types");
        filter.setEnabled(true);

        final FlowPanel buttons = getView().getButtonContainer();
        buttons.add(filter);

        view.setTreeView(treePresenter.getView());
        view.setUiHandlers(this);

        // Only show the System node at the root for export
        treePresenter.setIncludedRootTypes(ExplorerConstants.SYSTEM_TYPE);
    }

    @Override
    protected void onBind() {
        registerHandler(typeFilterPresenter.addDataSelectionHandler(event -> treePresenter.setIncludedTypeSet(
                typeFilterPresenter.getIncludedTypes().orElse(null))));

        registerHandler(filter.addClickHandler((e) ->
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

    @ProxyEvent
    @Override
    public void onExport(final ExportConfigEvent event) {
        if (event.getSelection() != null) {
            for (final ExplorerNode node : event.getSelection()) {
                treePresenter.getTreeModel().setEnsureVisible(new HashSet<>(event.getSelection()));
                treePresenter.setSelected(node, true);
            }
        }

        forceReveal();
    }

    @Override
    protected void revealInParent() {
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

        treePresenter.setRequiredPermissions(DocumentPermission.VIEW);
        treePresenter.refresh();

        getView().focus();
    }

    private void export(final HidePopupRequestEvent event) {
//        // Disable the popup ok/cancel buttons before we attempt export.
//        DisablePopupEvent.builder(this).fire();

        final Set<ExplorerNode> dataItems = treePresenter.getSelectedSet();
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
        treePresenter.changeNameFilter(name);
    }

    @Override
    public void showTypeFilter(final MouseDownEvent event,
                               final Consumer<Boolean> filterStateConsumer) {
        final Element target = event.getNativeEvent().getEventTarget().cast();
        typeFilterPresenter.show(target, filterStateConsumer);
    }

    // --------------------------------------------------------------------------------


    public interface ExportConfigView extends View, Focus, HasUiHandlers<ExportConfigUiHandlers> {

        void setTreeView(View view);

        FlowPanel getButtonContainer();
    }


    // --------------------------------------------------------------------------------


    @ProxyCodeSplit
    public interface ExportProxy extends Proxy<ExportConfigPresenter> {

    }
}
