package stroom.security.client.view;

import stroom.security.client.presenter.ApiKeyUiHandlers;
import stroom.security.client.presenter.ApiKeysPresenter.ApiKeysView;
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

public class ApiKeysViewImpl
        extends ViewWithUiHandlers<ApiKeyUiHandlers>
        implements ApiKeysView {

    private final Widget widget;

    @UiField
    QuickFilter quickFilter;
    @UiField
    SimplePanel listContainer;

    @Inject
    public ApiKeysViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        widget.addAttachHandler(event -> focus());
    }

    @Override
    public void registerPopupTextProvider(final Supplier<SafeHtml> popupTextSupplier) {
        quickFilter.registerPopupTextProvider(popupTextSupplier);
    }

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

    @UiHandler("quickFilter")
    void onFilterChange(final ValueChangeEvent<String> event) {
        getUiHandlers().changeQuickFilterInput(quickFilter.getText());
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, ApiKeysViewImpl> {

    }
}
