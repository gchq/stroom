package stroom.planb.client.view;

import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.planb.client.presenter.PlanBSettingsUiHandlers;
import stroom.planb.shared.SessionKeySchema;

import com.gwtplatform.mvp.client.HasUiHandlers;

public interface SessionKeySchemaSettingsView extends ReadOnlyChangeHandler,
        HasUiHandlers<PlanBSettingsUiHandlers> {

    SessionKeySchema getKeySchema();

    void setKeySchema(SessionKeySchema keySchema);
}
