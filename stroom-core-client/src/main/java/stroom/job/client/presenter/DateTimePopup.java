package stroom.job.client.presenter;

import stroom.expression.api.UserTimeZone;
import stroom.job.client.presenter.DateTimePopup.DateTimeView;
import stroom.preferences.client.UserPreferencesManager;
import stroom.ui.config.shared.UserPreferences;
import stroom.util.client.ClientStringUtil;
import stroom.widget.datepicker.client.IntlDateTimeFormat;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.Day;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.Hour;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.Minute;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.Month;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.Second;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.TimeZoneName;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.Year;
import stroom.widget.datepicker.client.UTCDate;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;

public class DateTimePopup extends MyPresenterWidget<DateTimeView> {

    private final UserPreferencesManager userPreferencesManager;

    @Inject
    public DateTimePopup(final EventBus eventBus,
                         final DateTimeView view,
                         final UserPreferencesManager userPreferencesManager) {
        super(eventBus, view);
        this.userPreferencesManager = userPreferencesManager;
        view.setTimeZoneProvider(this::getTimeZone);
    }

    public void show(final Consumer<Long> consumer) {
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption("Set Date And Time")
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    // This method is overwritten so that we can validate the schedule
                    // before saving. Getting the scheduled times acts as validation.
                    if (e.isOk()) {
                        consumer.accept(getTime());
                        e.hide();
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    public long getTime() {
        return getView().getTime();
    }

    public void setTime(final long ms) {
        getView().setTime(ms);
    }

    public Long parse(final String dateTime) {
        if (dateTime != null && dateTime.trim().length() > 0) {
            try {
                // Deliberate wrap here to ensure
                return (long) UTCDate.create(dateTime).getTime();
            } catch (final RuntimeException e) {
                GWT.log(e.getMessage());
            }
        }
        return null;
    }

    public String format(final long ms) {
        final String timeZone = getTimeZone();
        final FormatOptions.Builder builder = FormatOptions
                .builder()
                .year(Year.NUMERIC)
                .month(Month.TWO_DIGIT)
                .day(Day.TWO_DIGIT)
                .hour(Hour.TWO_DIGIT)
                .minute(Minute.TWO_DIGIT)
                .second(Second.TWO_DIGIT)
                .fractionalSecondDigits(3)
                .timeZoneName(TimeZoneName.SHORT);
        if (timeZone != null) {
            builder.timeZone(timeZone);
        }
        return IntlDateTimeFormat
                .format(UTCDate.create(ms), IntlDateTimeFormat.DEFAULT_LOCALE, builder.build());
    }

    private String getTimeZone() {
        final UserPreferences userPreferences = userPreferencesManager.getCurrentUserPreferences();
        final UserTimeZone userTimeZone = userPreferences.getTimeZone();
        String timeZone = null;
        switch (userTimeZone.getUse()) {
            case UTC: {
                timeZone = "GMT";
                break;
            }
            case ID: {
                timeZone = userTimeZone.getId();
                break;
            }
            case OFFSET: {
                final String hours = ClientStringUtil.zeroPad(2, userTimeZone.getOffsetHours());
                final String minutes = ClientStringUtil.zeroPad(2, userTimeZone.getOffsetMinutes());
                String offset = hours + minutes;
                if (userTimeZone.getOffsetHours() >= 0 && userTimeZone.getOffsetMinutes() >= 0) {
                    offset = "+" + offset;
                } else {
                    offset = "-" + offset;
                }

                timeZone = "GMT" + offset;
                break;
            }
        }
        return timeZone;
    }

    public interface DateTimeView extends View, Focus {

        long getTime();

        void setTime(long time);

        void setTimeZoneProvider(Provider<String> timeZoneProvider);
    }
}
