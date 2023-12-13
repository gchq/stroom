package stroom.security.client.view;

import stroom.security.client.presenter.ApiKeysListPresenter.ApiKeysListView;

import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewImpl;

public class ApiKeysListViewImpl
        extends ViewImpl
        implements ApiKeysListView {

    private final SimplePanel container;

    public ApiKeysListViewImpl() {
        container = new SimplePanel();
        container.addStyleName("max");
    }

    @Override
    public void setTable(final Widget widget) {
        container.setWidget(widget);
    }

    @Override
    public Widget asWidget() {
        return container;
    }
}
