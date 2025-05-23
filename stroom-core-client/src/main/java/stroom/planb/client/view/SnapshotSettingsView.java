package stroom.planb.client.view;

import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.planb.client.presenter.PlanBSettingsUiHandlers;
import stroom.planb.shared.SnapshotSettings;

import com.gwtplatform.mvp.client.HasUiHandlers;

public interface SnapshotSettingsView extends ReadOnlyChangeHandler, HasUiHandlers<PlanBSettingsUiHandlers> {

    SnapshotSettings getSnapshotSettings();

    void setSnapshotSettings(SnapshotSettings snapshotSettings);
}
