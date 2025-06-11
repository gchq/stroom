package stroom.planb.client.view;

import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.planb.client.presenter.PlanBSettingsUiHandlers;
import stroom.planb.shared.MetricKeySchema;

import com.gwtplatform.mvp.client.HasUiHandlers;

public interface MetricKeySchemaSettingsView extends ReadOnlyChangeHandler,
        HasUiHandlers<PlanBSettingsUiHandlers> {

    MetricKeySchema getKeySchema();

    void setKeySchema(MetricKeySchema keySchema);
}
