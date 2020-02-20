package stroom.config.global.client.presenter;

import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.config.global.shared.ConfigProperty;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView;

import javax.inject.Inject;
import java.util.Map;
import java.util.Set;

public class ConfigPropertyClusterValuesPresenter
    extends MyPresenterWidget<DataGridView<?>>
    implements ConfigPropertyClusterValuesUiHandlers {

    private Map<String, Set<String>> effectiveValueToNodesMap;
    private ConfigProperty configProperty;
    private ConfigPropertyClusterValuesListPresenter listPresenter;

    @Inject
    public ConfigPropertyClusterValuesPresenter(final EventBus eventBus,
                                                final ConfigPropertyClusterValuesView view,
                                                final ConfigPropertyClusterValuesListPresenter listPresenter) {
        super(eventBus, new DataGridViewImpl<>(false));
        this.listPresenter = listPresenter;
        view.setList(listPresenter.getWidget());
    }

    void show(final ConfigProperty configProperty,
              final Map<String, Set<String>> effectiveValueToNodesMap,
              final PopupPosition popupPosition,
              final PopupUiHandlers popupUiHandlers) {

        this.effectiveValueToNodesMap = effectiveValueToNodesMap;
        this.configProperty = configProperty;
        this.listPresenter.setData(effectiveValueToNodesMap);

        final String caption = getEntityDisplayType() + " - " + configProperty.getName();
        final PopupView.PopupType popupType = PopupView.PopupType.CLOSE_DIALOG;

        final PopupUiHandlers internalPopupUiHandlers = new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                hide();
                popupUiHandlers.onHideRequest(autoClose, ok);
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
                popupUiHandlers.onHide(autoClose, ok);
            }
        };

        ShowPopupEvent.fire(
                ConfigPropertyClusterValuesPresenter.this,
                ConfigPropertyClusterValuesPresenter.this,
                popupType,
                getPopupSize(),
                popupPosition,
                caption,
                internalPopupUiHandlers);
    }

    protected void hide() {
        HidePopupEvent.fire(
            ConfigPropertyClusterValuesPresenter.this,
            ConfigPropertyClusterValuesPresenter.this);
    }

    protected PopupSize getPopupSize() {
        return new PopupSize(
            700, 513,
            700, 513,
            1024, 513,
            true);
    }

    private String getEntityDisplayType() {
        return "Cluster values";
    }

    public interface ConfigPropertyClusterValuesView
        extends View, HasUiHandlers<ConfigPropertyClusterValuesUiHandlers> {

        void setList(Widget widget);
    }
}
