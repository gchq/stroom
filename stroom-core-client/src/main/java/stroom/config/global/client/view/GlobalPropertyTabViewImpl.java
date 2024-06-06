package stroom.config.global.client.view;

import stroom.config.global.client.presenter.GlobalPropertyTabPresenter;
import stroom.config.global.client.presenter.ManageGlobalPropertyUiHandlers;
import stroom.widget.dropdowntree.client.view.QuickFilter;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.function.Supplier;

public class GlobalPropertyTabViewImpl
        extends ViewWithUiHandlers<ManageGlobalPropertyUiHandlers>
        implements GlobalPropertyTabPresenter.GlobalPropertyTabView {

    @UiField
    QuickFilter nameFilter;
    @UiField
    SimplePanel dataGrid;

    private final Widget widget;

    @Inject
    GlobalPropertyTabViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
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
        nameFilter.forceFocus();
    }

    @Override
    public void registerPopupTextProvider(final Supplier<SafeHtml> popupTextSupplier) {
        nameFilter.registerPopupTextProvider(popupTextSupplier);
    }

    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, GlobalPropertyTabViewImpl> {

    }
}
