package stroom.widget.datepicker.client;

import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.InlineSvgButton;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.HasValue;

public class ValueChooser
        extends Composite
        implements Focus, HasValue<Long> {

    private final ValueSpinner valueSpinner = new ValueSpinner();

    public ValueChooser() {
        valueSpinner.addStyleName("dock-max");

        final InlineSvgButton resetButton = new InlineSvgButton();
        resetButton.setSvg(SvgImage.TAB_CLOSE);
        resetButton.setTitle("Reset");
        resetButton.addStyleName("reset");
        resetButton.addClickHandler(event -> reset());

        final FlowPanel layout = new FlowPanel();
        layout.setStyleName("valueChooser");
        layout.add(valueSpinner);
        layout.add(resetButton);

        initWidget(layout);
    }

    private void reset() {
        valueSpinner.setValue(valueSpinner.getMin(), true);
    }

    @Override
    public void focus() {
        valueSpinner.focus();
    }

    public boolean isEnabled() {
        return valueSpinner.isEnabled();
    }

    public void setEnabled(final boolean enabled) {
        valueSpinner.setEnabled(enabled);
    }

    public void setMax(final long max) {
        valueSpinner.setMax(max);
    }

    public void setMaxStep(final int maxStep) {
        valueSpinner.setMaxStep(maxStep);
    }

    public void setMin(final long min) {
        valueSpinner.setMin(min);
    }

    public long getMin() {
        return valueSpinner.getMin();
    }

    public void setMinStep(final int minStep) {
        valueSpinner.setMinStep(minStep);
    }

    public int getIntValue() {
        return valueSpinner.getIntValue();
    }

    public void setValue(final Integer value) {
        valueSpinner.setValue(value);
    }

    @Override
    public Long getValue() {
        return valueSpinner.getValue();
    }

    @Override
    public void setValue(final Long value) {
        setValue(value, false);
    }

    @Override
    public void setValue(final Long value, final boolean fireEvents) {
        if (value != null) {
            valueSpinner.setValue(value, fireEvents);
        }
    }

    @Override
    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<Long> handler) {
        return valueSpinner.addValueChangeHandler(handler);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        valueSpinner.fireEvent(event);
    }
}
