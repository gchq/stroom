package stroom.importexport.client.view;

import stroom.importexport.client.presenter.DependenciesTabPresenter;
import stroom.importexport.client.presenter.DependenciesTabPresenter.DependenciesTabView;
import stroom.importexport.client.presenter.DependenciesUiHandlers;
import stroom.widget.dropdowntree.client.view.QuickFilter;
import stroom.widget.layout.client.view.ResizeSimplePanel;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class DependenciesTabViewImpl
        extends ViewWithUiHandlers<DependenciesUiHandlers>
        implements DependenciesTabView {

    @UiField
    QuickFilter quickFilter;
    @UiField
    ResizeSimplePanel dataGrid;

    private Widget widget;

    @Inject
    public DependenciesTabViewImpl(final EventBus eventBus, final Binder binder) {
        widget = binder.createAndBindUi(this);
        quickFilter.registerPopupTextProvider(() -> "This is my dependencies popup text");
    }

//    @Override
//    public void setUiHandlers(final DependenciesUiHandlers uiHandlers) {
//
//    }

    @Override
    public void addToSlot(final Object slot, final Widget content) {

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
        if (DependenciesTabPresenter.LIST.equals(slot)) {
            dataGrid.setWidget(content);
        }
    }

    @Override
    public void setHelpTooltipText(final String helpTooltipText) {
        quickFilter.registerPopupTextProvider(() -> helpTooltipText);
    }

    @UiHandler("quickFilter")
    void onFilterChange(final ValueChangeEvent<String> event) {
        getUiHandlers().changeQuickFilter(quickFilter.getText());
    }

    public interface Binder extends UiBinder<Widget, DependenciesTabViewImpl> {
    }
}
