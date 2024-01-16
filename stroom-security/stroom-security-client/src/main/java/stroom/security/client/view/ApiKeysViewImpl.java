package stroom.security.client.view;

import stroom.security.client.presenter.ApiKeyUiHandlers;
import stroom.security.client.presenter.ApiKeysPresenter.ApiKeysView;
import stroom.security.shared.FindApiKeyCriteria;
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
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class ApiKeysViewImpl
        extends ViewWithUiHandlers<ApiKeyUiHandlers>
        implements ApiKeysView {

    private final Widget widget;

    @UiField
    QuickFilter quickFilter;
    @UiField
    SimplePanel listContainer;

    @Inject
    public ApiKeysViewImpl(final Binder binder, final UiConfigCache uiConfigCache) {
        widget = binder.createAndBindUi(this);
        widget.addAttachHandler(event -> focus());

        uiConfigCache.get()
                .onSuccess(uiConfig ->
                        quickFilter.registerPopupTextProvider(() -> QuickFilterTooltipUtil.createTooltip(
                                "API Keys Quick Filter",
                                FindApiKeyCriteria.FILTER_FIELD_DEFINITIONS,
                                uiConfig.getHelpUrlQuickFilter())));
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
