package stroom.planb.client.view;

import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.planb.client.presenter.PlanBSettingsUiHandlers;
import stroom.planb.shared.HistogramValueSchema;

import com.gwtplatform.mvp.client.HasUiHandlers;

public interface HistogramValueSchemaSettingsView extends ReadOnlyChangeHandler,
        HasUiHandlers<PlanBSettingsUiHandlers> {

    HistogramValueSchema getValueSchema();

    void setValueSchema(HistogramValueSchema valueSchema);
}
