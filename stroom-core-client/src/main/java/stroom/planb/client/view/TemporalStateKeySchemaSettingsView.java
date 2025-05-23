package stroom.planb.client.view;

import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.planb.client.presenter.PlanBSettingsUiHandlers;
import stroom.planb.shared.TemporalStateKeySchema;

import com.gwtplatform.mvp.client.HasUiHandlers;

public interface TemporalStateKeySchemaSettingsView extends ReadOnlyChangeHandler,
        HasUiHandlers<PlanBSettingsUiHandlers> {

    TemporalStateKeySchema getKeySchema();

    void setKeySchema(TemporalStateKeySchema keySchema);
}
