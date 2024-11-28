package stroom.security.client.view;

import stroom.security.client.presenter.ApiKeysPresenter.ApiKeysView;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

public class ApiKeysViewImpl extends ViewImpl implements ApiKeysView {

    private final Widget widget;

    //    @UiField
//    QuickFilter quickFilter;
    @UiField
    SimplePanel listContainer;

    @Inject
    public ApiKeysViewImpl(final Binder binder) {
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
    public void setList(final View view) {
        listContainer.setWidget(view.asWidget());
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

//    @Override
//    public void setUiHandlers(final ApiKeyUiHandlers apiKeyUiHandlers) {
//
//    }

//    @UiHandler("quickFilter")
//    void onFilterChange(final ValueChangeEvent<String> event) {
//        getUiHandlers().changeQuickFilterInput(quickFilter.getText());
//    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, ApiKeysViewImpl> {

    }
}
