package stroom.config.global.client.view;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;
import stroom.config.global.client.presenter.ConfigPropertyClusterValuesPresenter;
import stroom.config.global.client.presenter.ConfigPropertyClusterValuesUiHandlers;
import stroom.widget.layout.client.view.ResizeSimplePanel;

public class ConfigPropertyClusterValuesViewImpl
    extends ViewWithUiHandlers<ConfigPropertyClusterValuesUiHandlers>
    implements ConfigPropertyClusterValuesPresenter.ConfigPropertyClusterValuesView {

    private final Widget widget;

    @UiField
    ResizeSimplePanel dataGrid;

    @Inject
    ConfigPropertyClusterValuesViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setList(final Widget widget) {
        dataGrid.setWidget(widget);
    }

    @Override
    public void removeFromSlot(final Object slot, final Widget content) {

    }

    @Override
    public void setInSlot(final Object slot, final Widget content) {
        if (ConfigPropertyClusterValuesPresenter.LIST.equals(slot)) {
            dataGrid.setWidget(content);
        }
    }

    public interface Binder extends UiBinder<Widget, ConfigPropertyClusterValuesViewImpl> {
    }
}
