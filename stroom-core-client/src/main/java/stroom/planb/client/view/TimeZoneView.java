package stroom.planb.client.view;

import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.planb.client.presenter.PlanBSettingsUiHandlers;
import stroom.query.api.UserTimeZone;

import com.gwtplatform.mvp.client.HasUiHandlers;

public interface TimeZoneView extends ReadOnlyChangeHandler,
        HasUiHandlers<PlanBSettingsUiHandlers> {

    UserTimeZone.Use getTimeZoneUse();

    void setTimeZoneUse(UserTimeZone.Use use);

    String getTimeZoneId();

    void setTimeZoneId(String timeZoneId);

    Integer getTimeZoneOffsetHours();

    void setTimeZoneOffsetHours(Integer timeZoneOffsetHours);

    Integer getTimeZoneOffsetMinutes();

    void setTimeZoneOffsetMinutes(Integer timeZoneOffsetMinutes);
}
