package stroom.item.client;

import stroom.docref.HasDisplayValue;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public class SelectionBoxModel<T> implements SelectionBoxView<T> {

    private final List<T> items = new ArrayList<>();
    private final List<String> strings = new ArrayList<>();
    private T value;
    private final HandlerManager handlerManager = new HandlerManager(this);
    private Function<T, String> displayValueFunction = null;

    public SelectionBoxModel() {
    }

    @Override
    public void setDisplayValueFunction(final Function<T, String> displayValueFunction) {
        this.displayValueFunction = displayValueFunction;
    }

    @Override
    public void setNonSelectString(final String nonSelectString) {
        items.add(0, null);
        strings.add(nonSelectString);
    }

    @Override
    public void addItems(final Collection<T> items) {
        for (final T item : items) {
            addItem(item);
        }
    }

    @Override
    public void addItems(final T[] items) {
        for (final T item : items) {
            addItem(item);
        }
    }

    @Override
    public void addItem(final T item) {
        if (item != null) {
            items.add(item);
            final String displayValue;
            if (displayValueFunction != null) {
                displayValue = displayValueFunction.apply(item);
            } else if (item instanceof HasDisplayValue) {
                displayValue = ((HasDisplayValue) item).getDisplayValue();
                strings.add(((HasDisplayValue) item).getDisplayValue());
            } else if (item instanceof String) {
                displayValue = (String) item;
            } else {
                displayValue = item.toString();
            }
            strings.add(displayValue);
        }
    }

    @Override
    public void clear() {
        value = null;
        items.clear();
        strings.clear();
    }

    public List<String> getStrings() {
        return strings;
    }

    @Override
    public T getValue() {
        return value;
    }

    @Override
    public void setValue(final T value) {
        setValue(value, false);
    }

    @Override
    public void setValue(final T value, final boolean fireEvents) {
        this.value = value;
        if (fireEvents) {
            ValueChangeEvent.fire(this, value);
        }
    }

    public String getText() {
        if (value == null) {
            return "";
        }
        final int index = items.indexOf(value);
        if (index == -1) {
            return "";
        }
        return strings.get(index);
    }

    public void setSelectedIndex(int index) {
        if (index < 0) {
            setValue(null, true);
        } else {
            setValue(items.get(index), true);
        }
    }

    public int getSelectedIndex() {
        if (value == null) {
            return -1;
        }
        return items.indexOf(value);
    }

    @Override
    public com.google.gwt.event.shared.HandlerRegistration addValueChangeHandler(final ValueChangeHandler<T> handler) {
        return handlerManager.addHandler(ValueChangeEvent.getType(), handler);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        handlerManager.fireEvent(event);
    }
}
