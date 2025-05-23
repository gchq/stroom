package stroom.planb.client.view;

import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.planb.client.presenter.PlanBSettingsUiHandlers;
import stroom.planb.shared.StateKeySchema;

import com.gwtplatform.mvp.client.HasUiHandlers;

public interface StateKeySchemaSettingsView extends ReadOnlyChangeHandler, HasUiHandlers<PlanBSettingsUiHandlers> {

    StateKeySchema getKeySchema();

    void setKeySchema(StateKeySchema keySchema);
}
