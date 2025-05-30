package stroom.planb.client.view;

import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.planb.client.presenter.PlanBSettingsUiHandlers;
import stroom.planb.shared.HistogramKeySchema;

import com.gwtplatform.mvp.client.HasUiHandlers;

public interface HistogramKeySchemaSettingsView extends ReadOnlyChangeHandler,
        HasUiHandlers<PlanBSettingsUiHandlers> {

    HistogramKeySchema getKeySchema();

    void setKeySchema(HistogramKeySchema keySchema);
}
