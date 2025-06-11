package stroom.planb.client.view;

import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.planb.client.presenter.PlanBSettingsUiHandlers;
import stroom.planb.shared.RetentionSettings;

import com.gwtplatform.mvp.client.HasUiHandlers;

public interface RetentionSettingsView extends ReadOnlyChangeHandler, HasUiHandlers<PlanBSettingsUiHandlers> {

    RetentionSettings getRetention();

    void setRetention(RetentionSettings retention);
}
