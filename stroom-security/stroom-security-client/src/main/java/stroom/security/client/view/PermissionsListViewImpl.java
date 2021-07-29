package stroom.security.client.view;

import stroom.security.client.presenter.PermissionsListPresenter.PermissionsListView;

import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewImpl;

public class PermissionsListViewImpl extends ViewImpl implements PermissionsListView {

    private final SimplePanel container;

    public PermissionsListViewImpl() {
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
