package stroom.planb.client.view;

import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.planb.client.presenter.PlanBSettingsUiHandlers;

import com.gwtplatform.mvp.client.HasUiHandlers;

public interface GeneralSettingsView extends ReadOnlyChangeHandler, HasUiHandlers<PlanBSettingsUiHandlers> {

    Long getMaxStoreSize();

    void setMaxStoreSize(Long maxStoreSize);

    Boolean getSynchroniseMerge();

    void setSynchroniseMerge(Boolean synchroniseMerge);

    Boolean getOverwrite();

    void setOverwrite(Boolean overwrite);
}
