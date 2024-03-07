package stroom.job.client.presenter;

import stroom.job.client.presenter.DateTimePopup.DateTimeView;
import stroom.preferences.client.DateTimeFormatter;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.presenter.Size;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;

public class DateTimePopup extends MyPresenterWidget<DateTimeView> {

    private final DateTimeFormatter dateTimeFormatter;

    @Inject
    public DateTimePopup(final EventBus eventBus,
                         final DateTimeView view,
                         final DateTimeFormatter dateTimeFormatter) {
        super(eventBus, view);
        this.dateTimeFormatter = dateTimeFormatter;
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
        return dateTimeFormatter.parse(dateTime);
    }

    public String format(final long ms) {
        return dateTimeFormatter.format(ms);
    }

    public interface DateTimeView extends View, Focus {

        long getTime();

        void setTime(long time);
    }
}
