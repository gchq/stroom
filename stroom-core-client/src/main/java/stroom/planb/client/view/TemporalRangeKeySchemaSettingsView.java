package stroom.planb.client.view;

import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.planb.client.presenter.PlanBSettingsUiHandlers;
import stroom.planb.shared.TemporalRangeKeySchema;

import com.gwtplatform.mvp.client.HasUiHandlers;

public interface TemporalRangeKeySchemaSettingsView
        extends ReadOnlyChangeHandler, HasUiHandlers<PlanBSettingsUiHandlers> {

    TemporalRangeKeySchema getKeySchema();

    void setKeySchema(TemporalRangeKeySchema keySchema);
}
