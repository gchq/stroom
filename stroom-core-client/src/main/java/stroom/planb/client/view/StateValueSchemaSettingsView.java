package stroom.planb.client.view;

import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.planb.client.presenter.PlanBSettingsUiHandlers;
import stroom.planb.shared.StateValueSchema;

import com.gwtplatform.mvp.client.HasUiHandlers;

public interface StateValueSchemaSettingsView extends ReadOnlyChangeHandler,
        HasUiHandlers<PlanBSettingsUiHandlers> {

    StateValueSchema getValueSchema();

    void setValueSchema(StateValueSchema valueSchema);
}
