package stroom.config.global.client.presenter;

import stroom.config.global.shared.ConfigProperty;
import stroom.data.table.client.Refreshable;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.Map;
import java.util.Set;

public class ConfigPropertyClusterValuesPresenter
        extends MyPresenterWidget<ConfigPropertyClusterValuesPresenter.ConfigPropertyClusterValuesView>
        implements ConfigPropertyClusterValuesUiHandlers, Refreshable {

    public static final String LIST = "LIST";

    private final ConfigPropertyClusterValuesListPresenter listPresenter;

    @Inject
    public ConfigPropertyClusterValuesPresenter(final EventBus eventBus,
                                                final ConfigPropertyClusterValuesView view,
                                                final ConfigPropertyClusterValuesListPresenter listPresenter) {
        super(eventBus, view);
        this.listPresenter = listPresenter;
        view.setList(listPresenter.getWidget());
        view.setUiHandlers(this);
    }

    @Override
    public void refresh() {
        listPresenter.refresh();
    }

    void show(final ConfigProperty configProperty,
              final Map<String, Set<NodeSource>> effectiveValueToNodesMap,
              final PopupPosition popupPosition) {

        this.listPresenter.setData(effectiveValueToNodesMap);

        final String caption = getEntityDisplayType() + " - " + configProperty.getName();
        final PopupType popupType = PopupType.CLOSE_DIALOG;

        ShowPopupEvent.builder(this)
                .popupType(popupType)
                .popupSize(getPopupSize())
                .popupPosition(popupPosition)
                .caption(caption)
                .onShow(e -> listPresenter.focus())
                .fire();
    }

    protected PopupSize getPopupSize() {
        return PopupSize.resizable(900, 700);
    }

    private String getEntityDisplayType() {
        return "Cluster values";
    }

    public interface ConfigPropertyClusterValuesView
            extends View, HasUiHandlers<ConfigPropertyClusterValuesUiHandlers> {

        void setList(Widget widget);
    }
}
