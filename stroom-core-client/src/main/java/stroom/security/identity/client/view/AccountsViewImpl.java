package stroom.security.identity.client.view;

import stroom.security.identity.client.presenter.AccountsPresenter.AccountsView;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class AccountsViewImpl
        extends ViewImpl
        implements AccountsView {

    private final Widget widget;

    //    @UiField
//    QuickFilter quickFilter;
    @UiField
    SimplePanel listContainer;

    @Inject
    public AccountsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        widget.addAttachHandler(event -> focus());
    }

//    @Override
//    public void registerPopupTextProvider(final Supplier<SafeHtml> popupTextSupplier) {
//        quickFilter.registerPopupTextProvider(popupTextSupplier);
//    }

    @Override
    public void focus() {

    }

    @Override
    public void setList(final Widget widget) {
        listContainer.setWidget(widget);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

//    @UiHandler("quickFilter")
//    void onFilterChange(final ValueChangeEvent<String> event) {
//        getUiHandlers().changeQuickFilterInput(quickFilter.getText());
//    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, AccountsViewImpl> {

    }
}
