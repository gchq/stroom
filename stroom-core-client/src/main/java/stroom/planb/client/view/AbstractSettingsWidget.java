package stroom.planb.client.view;

import stroom.planb.client.presenter.PlanBSettingsUiHandlers;

import com.google.gwt.user.client.ui.Widget;

public abstract class AbstractSettingsWidget {

    private PlanBSettingsUiHandlers uiHandlers;

    public void setUiHandlers(final PlanBSettingsUiHandlers uiHandlers) {
        this.uiHandlers = uiHandlers;
    }

    public PlanBSettingsUiHandlers getUiHandlers() {
        return uiHandlers;
    }

    abstract Widget asWidget();
}
