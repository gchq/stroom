package stroom.job.client.presenter;

import stroom.expression.api.UserTimeZone;
import stroom.job.client.presenter.DateTimePopup.DateTimeView;
import stroom.job.client.view.DateTimeModel;
import stroom.preferences.client.UserPreferencesManager;
import stroom.ui.config.shared.UserPreferences;
import stroom.util.client.ClientStringUtil;
import stroom.widget.datepicker.client.IntlDateTimeFormat;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.DateStyle;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.TimeStyle;
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

    private final DateTimeModel dateTimeModel;

    @Inject
    public DateTimePopup(final EventBus eventBus,
                         final DateTimeView view,
                         final DateTimeModel dateTimeModel) {
        super(eventBus, view);
        this.dateTimeModel = dateTimeModel;
        view.setDateTimeModel(dateTimeModel);
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
                final Double d = UTCDate.parse(dateTime);
                if (d != null) {
                    return d.longValue();
                }
            } catch (final RuntimeException e) {
                GWT.log(e.getMessage());
            }
        }
        return null;
    }

    public String format(final long ms) {
        String str1 = format(ms, DateStyle.SHORT, TimeStyle.FULL);
        String str2 = format(ms, DateStyle.FULL, TimeStyle.FULL);
        String str3 = format(ms, DateStyle.LONG, TimeStyle.FULL);
        String str4 = format(ms, DateStyle.MEDIUM, TimeStyle.FULL);
        String str5 = format(ms, DateStyle.SHORT, TimeStyle.SHORT);
        String str6 = format(ms, DateStyle.SHORT, TimeStyle.MEDIUM);
        String str7 = format(ms, DateStyle.SHORT, TimeStyle.LONG);
        String str8 = UTCDate.create(ms).toISOString();
        String str9 = UTCDate.create(ms).toUTCString();
        String str10 = UTCDate.create(ms).toString();
        return str1;
    }

    private String format(final long ms, final DateStyle dateStyle, final TimeStyle timeStyle) {
        final String timeZone = dateTimeModel.getTimeZone();
        final FormatOptions.Builder builder = FormatOptions
                .builder()
                .dateStyle(dateStyle)
                .timeStyle(timeStyle);
        if (timeZone != null) {
            builder.timeZone(timeZone);
        }
        return IntlDateTimeFormat
                .format(UTCDate.create(ms), IntlDateTimeFormat.DEFAULT_LOCALE, builder.build());
    }

    public interface DateTimeView extends View, Focus {

        long getTime();

        void setTime(long time);

        void setDateTimeModel(DateTimeModel dateTimeModel);
    }
}
