package stroom.security.client.view;

import stroom.security.client.presenter.UserTabPresenter.UserTabView;
import stroom.widget.tab.client.view.LinkTabBar;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.LayerContainer;
import com.gwtplatform.mvp.client.ViewImpl;

public class UserTabViewImpl extends ViewImpl implements UserTabView {

    private final Widget widget;

    @UiField
    LinkTabBar tabBar;
    @UiField
    LayerContainer layerContainer;

    @Inject
    public UserTabViewImpl(final Binder binder) {
        this.widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public LinkTabBar getTabBar() {
        return tabBar;
    }

    @Override
    public LayerContainer getLayerContainer() {
        return layerContainer;
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, UserTabViewImpl> {

    }
}
