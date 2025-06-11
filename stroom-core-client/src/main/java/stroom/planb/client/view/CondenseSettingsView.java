package stroom.planb.client.view;

import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.planb.client.presenter.PlanBSettingsUiHandlers;
import stroom.planb.shared.DurationSetting;

import com.gwtplatform.mvp.client.HasUiHandlers;

public interface CondenseSettingsView extends ReadOnlyChangeHandler, HasUiHandlers<PlanBSettingsUiHandlers> {

    DurationSetting getCondense();

    void setCondense(DurationSetting condense);
}
