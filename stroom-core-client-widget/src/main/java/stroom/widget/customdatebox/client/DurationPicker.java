package stroom.widget.customdatebox.client;

import stroom.item.client.ItemListBox;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HasValue;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.ArrayList;
import java.util.List;

public class DurationPicker extends Composite implements HasValue<SimpleDuration> {

    private final ValueSpinner time;
    private final ItemListBox<TimeUnit> timeUnit;
    private final List<HandlerRegistration> handlerRegistrations = new ArrayList<>();

    public DurationPicker() {
        time = new ValueSpinner();
        time.setMin(1);
        time.setMax(1000000);
        timeUnit = new ItemListBox<>();
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

    @Override
    protected void onAttach() {
        super.onAttach();
        handlerRegistrations.add(time.getTextBox().addKeyDownHandler(event ->
                ValueChangeEvent.fire(this, getValue())));
        handlerRegistrations.add(time.getSpinner().addSpinnerHandler(event ->
                ValueChangeEvent.fire(this, getValue())));
        handlerRegistrations.add(timeUnit.addSelectionHandler(event ->
                ValueChangeEvent.fire(this, getValue())));
    }

    @Override
    protected void onDetach() {
        super.onDetach();
        for (final HandlerRegistration handlerRegistration : handlerRegistrations) {
            handlerRegistration.removeHandler();
        }
        handlerRegistrations.clear();
    }

    @Override
    public SimpleDuration getValue() {
        return new SimpleDuration(time.getValue(), timeUnit.getSelectedItem());
    }

    @Override
    public void setValue(final SimpleDuration value) {
        if (value != null) {
            time.setValue(value.getTime());
            timeUnit.setSelectedItem(value.getTimeUnit());
        } else {
            time.setValue(1);
            timeUnit.setSelectedItem(TimeUnit.DAYS);
        }
    }

    @Override
    public void setValue(final SimpleDuration value, final boolean fireEvents) {
        time.setValue(value.getTime());
        timeUnit.setSelectedItem(value.getTimeUnit());
    }

    @Override
    public com.google.gwt.event.shared.HandlerRegistration addValueChangeHandler(
            final ValueChangeHandler<SimpleDuration> handler) {
        return addHandler(handler, ValueChangeEvent.getType());
    }
}
