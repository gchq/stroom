/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.importexport.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.core.client.LocationManager;
import stroom.dispatch.client.ExportFileCompleteUtil;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.explorer.shared.ExplorerNode;
import stroom.importexport.client.event.ExportConfigEvent;
import stroom.importexport.shared.ContentResource;
import stroom.importexport.shared.ExportContentRequest;
import stroom.importexport.shared.ExportSummary;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.tab.client.presenter.LinkTabsLayoutView;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.Layer;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class ExportConfigPresenter
        extends MyPresenter<LinkTabsLayoutView, ExportConfigPresenter.ExportProxy>
        implements ExportConfigEvent.Handler {

    private static final ContentResource CONTENT_RESOURCE = GWT.create(ContentResource.class);

    private final LocationManager locationManager;
    private final ExportConfigSelectionPresenter exportConfigSelectionPresenter;
    private final ExportConfigOptionsPresenter exportConfigOptionsPresenter;
    private final RestFactory restFactory;

    private final Map<TabData, Layer> tabViewMap = new HashMap<>();
    private TabData firstTab;
    private TabData selectedTab;

    @Inject
    public ExportConfigPresenter(final EventBus eventBus,
                                 final LinkTabsLayoutView linkTabsLayoutView,
                                 final ExportProxy proxy,
                                 final LocationManager locationManager,
                                 final ExportConfigSelectionPresenter exportConfigSelectionPresenter,
                                 final ExportConfigOptionsPresenter exportConfigOptionsPresenter,
                                 final RestFactory restFactory) {
        super(eventBus, linkTabsLayoutView, proxy);
        getWidget().getElement().addClassName("max overflow-hidden");

        this.exportConfigSelectionPresenter = exportConfigSelectionPresenter;
        this.locationManager = locationManager;
        this.exportConfigOptionsPresenter = exportConfigOptionsPresenter;
        this.restFactory = restFactory;

        addTab("Selection", exportConfigSelectionPresenter);
        addTab("Options", exportConfigOptionsPresenter);
    }

    public TabData addTab(final String text, final Layer layer) {
        final TabData tab = new TabDataImpl(text, false);
        tabViewMap.put(tab, layer);
        getView().getTabBar().addTab(tab);

        if (firstTab == null) {
            firstTab = tab;
        }

        return tab;
    }

    private void changeSelectedTab(final TabData tab) {
        if (selectedTab != tab) {
            selectedTab = tab;
            if (selectedTab != null) {
                final Layer layer = tabViewMap.get(selectedTab);
                if (layer != null) {
                    getView().getTabBar().selectTab(tab);
                    getView().getLayerContainer().show(layer);
                }
            }
        }
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(getView().getTabBar().addSelectionHandler(event -> {
            final TabData tab = event.getSelectedItem();
            if (tab != null && tab != selectedTab) {
                changeSelectedTab(tab);
            }
        }));
        registerHandler(getView().getTabBar().addShowMenuHandler(e ->
                getEventBus().fireEvent(e)));

        if (firstTab != null) {
            changeSelectedTab(firstTab);
        }
    }

    @ProxyEvent
    @Override
    public void onExportEvent(final ExportConfigEvent event) {
        GWT.log("onExportEvent: " + event.getSelection());
        exportConfigSelectionPresenter.onExportEvent(event);
        exportConfigOptionsPresenter.onExportEvent(event);
        forceReveal();
    }

    @Override
    protected void revealInParent() {
        final PopupSize popupSize = PopupSize.resizable(
                600,
                800,
                380,
                480);
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
//        exportConfigSelectionPresenter.reset();
//        getView().focus();
    }

    private void export(final HidePopupRequestEvent event) {
//        // Disable the popup ok/cancel buttons before we attempt export.
//        DisablePopupEvent.builder(this).fire();

        final Set<DocRef> selectedItems = getAllSelectedItems();
        if (NullSafe.isEmptyCollection(selectedItems)) {
            // Let the user know that they didn't select anything to export.
            AlertEvent.fireWarn(this, "No items have been selected for export", event::reset);
//            // Re-enable buttons on this popup.
//            EnablePopupEvent.builder(this).fire();

        } else {
            final ExportContentRequest request = new ExportContentRequest(
                    selectedItems,
                    exportConfigOptionsPresenter.isIncludeProcFilters());

            restFactory
                    .create(CONTENT_RESOURCE)
                    .method(resource ->
                            resource.fetchExportSummary(request))
                    .onSuccess(exportSummary -> {
                        showExportSummaryDialog(event, exportSummary, () -> {
                            restFactory
                                    .create(CONTENT_RESOURCE)
                                    .method(resource -> resource.exportContent(request))
                                    .onSuccess(resourceGeneration -> {
                                        ExportFileCompleteUtil.onSuccess(
                                                locationManager,
                                                this,
                                                resourceGeneration);
                                        event.hide();
                                    })
                                    .onFailure(RestErrorHandler.forPopup(this, event))
                                    .taskMonitorFactory(this)
                                    .exec();
                        });
                    })
                    .onFailure(RestErrorHandler.forPopup(this, event))
                    .taskMonitorFactory(this)
                    .exec();
        }
    }

    private void showExportSummaryDialog(final HidePopupRequestEvent event,
                                         final ExportSummary exportSummary,
                                         final Runnable onOKClicked) {
        final String msg = buildMultiItemMessage(exportSummary);
        ConfirmEvent.fire(this, msg, ok -> {
            if (ok) {
                onOKClicked.run();
            } else {
                event.reset();
            }
        });
    }

    private String buildMultiItemMessage(final ExportSummary exportSummary) {
        final int successTotal = exportSummary.getSuccessTotal();
        final String suffix = successTotal == 1
                ? ""
                : "s";
        final StringBuilder sb = new StringBuilder()
                .append("You are about to export ")
                .append(successTotal)
                .append(" item")
                .append(suffix)
                .append(". ")
                .append("The breakdown of item counts by item type is:\n\n");

        exportSummary.getSuccessCountsByType()
                .entrySet()
                .stream()
                .sorted(Entry.comparingByKey())
                .forEach(entry -> {
                    final String displayType = getDisplayType(entry.getKey());
                    final String countStr = ModelStringUtil.formatCsv(entry.getValue());
                    sb.append(displayType)
                            .append(": ")
                            .append(countStr)
                            .append("\n");
                });

        return sb.append("\nDo you wish to continue?")
                .toString();
    }

    private String getDisplayType(final String type) {
        return NullSafe.getOrElse(
                type,
                DocumentTypeRegistry::get,
                DocumentType::getDisplayType,
                type);
    }

    private Set<DocRef> getAllSelectedItems() {
        final Set<DocRef> docRefs = new HashSet<>();
        // Combine the explorer tree items with the non-explorer tree items
        NullSafe.stream(exportConfigSelectionPresenter.getSelectedSet())
                .map(ExplorerNode::getDocRef)
                .forEach(docRefs::add);
        NullSafe.addAll(docRefs, exportConfigOptionsPresenter.getSelectedDocRefs());
        return docRefs;
    }


    // --------------------------------------------------------------------------------


    @ProxyCodeSplit
    public interface ExportProxy extends Proxy<ExportConfigPresenter> {

    }
}
