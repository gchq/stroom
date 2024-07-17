package stroom.query.client.view;

import stroom.query.api.v2.TimeRange;
import stroom.widget.popup.client.view.HideRequest;
import stroom.widget.popup.client.view.HideRequestUiHandlers;
import stroom.widget.popup.client.view.OkCancelContent;
import stroom.widget.popup.client.view.SimplePopupLayout;

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
        label.setTitle("Time range to apply to all queries in this dashboard.\n" +
                "The selected time range applies to the time field applicable to each query's data source.");

        this.popup = new PopupPanel(false);

        final HideRequestUiHandlers hideRequestUiHandlers = new HideRequestUiHandlers() {
            @Override
            public void hideRequest(final HideRequest request) {
                if (request.isOk()) {
                    setValue(timeRangePopup.getValue(), true);
                }
                popup.hide();
                request.getCancelHandler().run();
            }
        };
        final OkCancelContent okCancelContent = new OkCancelContent(hideRequestUiHandlers);
        okCancelContent.setContent(timeRangePopup.asWidget());

        final SimplePopupLayout simplePopupLayout = new SimplePopupLayout();
        simplePopupLayout.setContent(okCancelContent);

        popup.addAutoHidePartner(label.getElement());
        popup.setWidget(simplePopupLayout);
        popup.setStyleName("timeRange-popup");

        label.addClickHandler(event -> {
            timeRangePopup.setValue(value, false);
            popup.showRelativeTo(label);
        });

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
    public TimeRange getValue() {
        return value;
    }

    @Override
    public void setValue(final TimeRange value) {
        setValue(value, false);
    }

    @Override
    public void setValue(TimeRange value, final boolean fireEvents) {
        if (value == null) {
            value = TimeRanges.ALL_TIME;
        }

        this.value = value;
        label.setText(value.getName());
        if (fireEvents) {
            ValueChangeEvent.fire(this, value);
        }
    }

    @Override
    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<TimeRange> handler) {
        return addHandler(handler, ValueChangeEvent.getType());
    }
}
