package stroom.job.client.presenter;

import stroom.item.client.EventBinder;
import stroom.svg.client.SvgIconBox;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.GwtNullSafe;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.TextBox;
import com.google.inject.Provider;

import java.util.Objects;

public class DateTimeBox
        extends Composite
        implements Focus, HasValueChangeHandlers<String> {

    private Provider<DateTimePopup> popupProvider;
    private final TextBox textBox;
    private final SvgIconBox svgIconBox;
    private String stringValue;
    private Long longValue;
    private DateTimePopup popup;

    private final EventBinder eventBinder = new EventBinder() {
        @Override
        protected void onBind() {
            registerHandler(svgIconBox.addClickHandler(event -> showPopup()));
            registerHandler(textBox.addKeyDownHandler(event -> {
                int keyCode = event.getNativeKeyCode();
                if (KeyCodes.KEY_ENTER == keyCode) {
                    showPopup();
                }
            }));
            registerHandler(textBox.addKeyUpHandler(event -> {
                final String newStringValue = GwtNullSafe.isBlankString(textBox.getValue())
                        ? null
                        : textBox.getValue();
                if (!Objects.equals(newStringValue, stringValue)) {
                    stringValue = newStringValue;
                    ValueChangeEvent.fire(DateTimeBox.this, stringValue);
                }
            }));
            registerHandler(textBox.addBlurHandler(event -> onBlur()));
            registerHandler(textBox.addFocusHandler(event -> onFocus()));
        }
    };

    private DateTimePopup getPopup() {
        if (popup == null && popupProvider != null) {
            popup = popupProvider.get();
            popup.setTime(System.currentTimeMillis());
        }
        return popup;
    }

    public DateTimeBox() {
        textBox = new TextBox();
        textBox.addStyleName("ScheduleBox-textBox stroom-control allow-focus");

        svgIconBox = new SvgIconBox();
        svgIconBox.addStyleName("ScheduleBox");
        svgIconBox.setWidget(textBox, SvgImage.CALENDAR);

        initWidget(svgIconBox);
    }

    @Override
    protected void onLoad() {
        eventBinder.bind();
    }

    @Override
    protected void onUnload() {
        eventBinder.unbind();
    }

    private void showPopup() {
        final DateTimePopup popup = getPopup();
        if (popup != null) {
            popup.setTime(longValue);
            popup.show(newValue -> {
                if (!Objects.equals(longValue, newValue)) {
                    setLongValue(newValue, true);
                }
            });
        }
    }

    @Override
    public void focus() {
        textBox.setFocus(true);
    }

    public void setName(final String name) {
        textBox.setName(name);
    }

    public void setEnabled(final boolean enabled) {
        textBox.setEnabled(enabled);
    }

    public String getStringValue() {
        return stringValue;
    }

    public void setStringValue(final String value) {
        setStringValue(value, false);
    }

    public void setStringValue(final String value, final boolean fireEvents) {
        this.longValue = getPopup().parse(value);
        this.stringValue = value;
        textBox.setValue(stringValue);
        textBox.getElement().removeClassName("invalid");
        if (fireEvents) {
            ValueChangeEvent.fire(this, value);
        }
    }

    public Long getLongValue() {
        return getPopup().parse(textBox.getValue());
    }

    public void setLongValue(final Long value) {
        setLongValue(value, false);
    }

    public void setLongValue(final Long value, final boolean fireEvents) {
        if (value != null) {
            this.longValue = value;
            this.stringValue = getPopup().format(value);
        } else {
            this.longValue = null;
            this.stringValue = null;
        }
        textBox.setValue(stringValue);
        textBox.getElement().removeClassName("invalid");
        if (fireEvents) {
            ValueChangeEvent.fire(this, null);
        }
    }

    public boolean isValid() {
        if (stringValue == null) {
            return true;
        }
        final Long ms = getPopup().parse(stringValue);
        return ms != null;
    }

    private void onFocus() {
        textBox.getElement().removeClassName("invalid");
    }

    private void onBlur() {
        if (isValid()) {
            textBox.getElement().removeClassName("invalid");
        } else {
            textBox.getElement().addClassName("invalid");
        }
    }

    @Override
    public com.google.gwt.event.shared.HandlerRegistration addValueChangeHandler(
            final ValueChangeHandler<String> handler) {
        return addHandler(handler, ValueChangeEvent.getType());
    }

    public void setPopupProvider(final Provider<DateTimePopup> popupProvider) {
        this.popupProvider = popupProvider;
    }
}
