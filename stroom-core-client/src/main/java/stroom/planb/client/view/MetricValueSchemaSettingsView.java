package stroom.planb.client.view;

import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.planb.client.presenter.PlanBSettingsUiHandlers;
import stroom.planb.shared.MetricValueSchema;

import com.gwtplatform.mvp.client.HasUiHandlers;

public interface MetricValueSchemaSettingsView extends ReadOnlyChangeHandler,
        HasUiHandlers<PlanBSettingsUiHandlers> {

    MetricValueSchema getValueSchema();

    void setValueSchema(MetricValueSchema valueSchema);
}
