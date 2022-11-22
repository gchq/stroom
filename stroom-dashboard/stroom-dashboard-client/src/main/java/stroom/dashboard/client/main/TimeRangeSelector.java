package stroom.dashboard.client.main;

import stroom.query.api.v2.TimeRange;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.HasValue;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PopupPanel;

public class TimeRangeSelector extends Composite implements HasValue<TimeRange>, Focus {

    private final Label label;
    private final PopupPanel popup;
    private final TimeRangePopup timeRangePopup;
    private TimeRange value = TimeRanges.ALL_TIME;

    public TimeRangeSelector() {
        timeRangePopup = GWT.create(TimeRangePopup.class);

        label = new Label(value.getName(), false);
        label.setStyleName("timeRange-selector");

        this.popup = new PopupPanel(true);

        popup.addAutoHidePartner(label.getElement());
        popup.setWidget(timeRangePopup.asWidget());
        popup.setStyleName("simplePopup-background timeRange-popup");
        popup.addCloseHandler(event -> {
            setValue(timeRangePopup.write(), true);
        });

        label.addClickHandler(event -> popup.showRelativeTo(label));

        initWidget(label);
    }

    @Override
    public void focus() {
        label.getElement().focus();
    }

    public void setUtc(final boolean utc) {
        timeRangePopup.setUtc(utc);
    }

    @Override
    public void setValue(final TimeRange value) {
        this.value = value;
    }

    @Override
    public void setValue(final TimeRange value, final boolean fireEvents) {
        this.value = value;
        label.setText(value.getName());
        if (fireEvents) {
            ValueChangeEvent.fire(this, value);
        }
    }

    @Override
    public TimeRange getValue() {
        return value;
    }

    @Override
    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<TimeRange> handler) {
        return addHandler(handler, ValueChangeEvent.getType());
    }
}
