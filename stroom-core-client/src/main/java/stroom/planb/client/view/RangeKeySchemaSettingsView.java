package stroom.planb.client.view;

import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.planb.client.presenter.PlanBSettingsUiHandlers;
import stroom.planb.shared.RangeKeySchema;

import com.gwtplatform.mvp.client.HasUiHandlers;

public interface RangeKeySchemaSettingsView extends ReadOnlyChangeHandler, HasUiHandlers<PlanBSettingsUiHandlers> {

    RangeKeySchema getKeySchema();

    void setKeySchema(RangeKeySchema keySchema);
}
