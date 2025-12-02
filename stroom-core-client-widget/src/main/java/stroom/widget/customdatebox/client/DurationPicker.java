package stroom.widget.customdatebox.client;

import stroom.item.client.EventBinder;
import stroom.item.client.SelectionBox;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.HasValue;

public class DurationPicker extends Composite implements HasValue<SimpleDuration>, Focus {

    private final ValueSpinner time;
    private final SelectionBox<TimeUnit> timeUnit;
    private final EventBinder eventBinder = new EventBinder() {
        @Override
        protected void onBind() {
            registerHandler(time.addValueChangeHandler(event ->
                    ValueChangeEvent.fire(DurationPicker.this, getValue())));
            registerHandler(timeUnit.addValueChangeHandler(event ->
                    ValueChangeEvent.fire(DurationPicker.this, getValue())));
        }
    };

    public DurationPicker() {
        time = new ValueSpinner();
        time.setMin(1);
        time.setMax(1000000);

        timeUnit = new SelectionBox<>();
        timeUnit.addItem(TimeUnit.SECONDS);
        timeUnit.addItem(TimeUnit.MINUTES);
        timeUnit.addItem(TimeUnit.HOURS);
        timeUnit.addItem(TimeUnit.DAYS);
        timeUnit.addItem(TimeUnit.WEEKS);
        timeUnit.addItem(TimeUnit.MONTHS);
        timeUnit.addItem(TimeUnit.YEARS);

        final FlowPanel flowPanel = new FlowPanel();
        flowPanel.setStyleName("duration-picker");
        flowPanel.add(time);
        flowPanel.add(timeUnit);
        initWidget(flowPanel);
    }

    public void smallTimeMode() {
        time.setMin(0);
        time.setMax(1000000);
        timeUnit.clear();
        timeUnit.addItem(TimeUnit.NANOSECONDS);
        timeUnit.addItem(TimeUnit.MILLISECONDS);
        timeUnit.addItem(TimeUnit.SECONDS);
        timeUnit.addItem(TimeUnit.MINUTES);
        timeUnit.addItem(TimeUnit.HOURS);
    }

    @Override
    public void focus() {
        time.focus();
    }

    @Override
    protected void onLoad() {
        eventBinder.bind();
    }

    @Override
    protected void onUnload() {
        eventBinder.unbind();
    }

    @Override
    public SimpleDuration getValue() {
        return SimpleDuration.builder().time(time.getValue()).timeUnit(timeUnit.getValue()).build();
    }

    @Override
    public void setValue(final SimpleDuration value) {
        if (value != null) {
            time.setValue(value.getTime());
            timeUnit.setValue(value.getTimeUnit());
        } else {
            time.setValue(1);
            timeUnit.setValue(TimeUnit.DAYS);
        }
    }

    @Override
    public void setValue(final SimpleDuration value, final boolean fireEvents) {
        time.setValue(value.getTime());
        timeUnit.setValue(value.getTimeUnit());
    }

    @Override
    public com.google.gwt.event.shared.HandlerRegistration addValueChangeHandler(
            final ValueChangeHandler<SimpleDuration> handler) {
        return addHandler(handler, ValueChangeEvent.getType());
    }

    public void setEnabled(final boolean enabled) {
        time.setEnabled(enabled);
        timeUnit.setEnabled(enabled);
    }
}
