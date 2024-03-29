package stroom.config.global.client.view;

import stroom.config.global.client.presenter.GlobalPropertyTabPresenter;
import stroom.config.global.client.presenter.ManageGlobalPropertyUiHandlers;
import stroom.config.global.shared.GlobalConfigResource;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.dropdowntree.client.view.QuickFilter;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class GlobalPropertyTabViewImpl
        extends ViewWithUiHandlers<ManageGlobalPropertyUiHandlers>
        implements GlobalPropertyTabPresenter.GlobalPropertyTabView {

    @UiField
    QuickFilter nameFilter;
    @UiField
    SimplePanel dataGrid;

    private final Widget widget;

    @Inject
    GlobalPropertyTabViewImpl(final EventBus eventBus,
                              final Binder binder,
                              final UiConfigCache uiConfigCache) {
        widget = binder.createAndBindUi(this);

        uiConfigCache.get()
                .onSuccess(uiConfig ->
                        nameFilter.registerPopupTextProvider(() ->
                                QuickFilterTooltipUtil.createTooltip(
                                        "Properties Quick Filter",
                                        GlobalConfigResource.FIELD_DEFINITIONS,
                                        uiConfig.getHelpUrlQuickFilter())));
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void removeFromSlot(final Object slot, final Widget content) {

    }

    @Override
    public void setInSlot(final Object slot, final Widget content) {
        if (GlobalPropertyTabPresenter.LIST.equals(slot)) {
            dataGrid.setWidget(content);
        }
    }

    @UiHandler("nameFilter")
    void onFilterChange(final ValueChangeEvent<String> event) {
        getUiHandlers().changeNameFilter(nameFilter.getText());
    }

    @Override
    public void focusFilter() {
        nameFilter.focus();
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, GlobalPropertyTabViewImpl> {

    }
}
