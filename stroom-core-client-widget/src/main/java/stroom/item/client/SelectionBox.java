package stroom.item.client;

import stroom.svg.client.SvgIconBox;
import stroom.svg.shared.SvgImage;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.TextBox;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class SelectionBox<T>
        extends Composite
        implements SelectionBoxView<T>, Focus {

    private final TextBox textBox;
    private final SvgIconBox svgIconBox;
    private final SelectionBoxModel<T> model = new SelectionBoxModel<>();

    private SelectionPopup popup;

    private final EventBinder eventBinder = new EventBinder() {
        @Override
        protected void onBind() {
            registerHandler(textBox.addClickHandler(event -> showPopup()));
            registerHandler(svgIconBox.addClickHandler(event -> showPopup()));
            registerHandler(textBox.addKeyUpHandler(event -> {
                int keyCode = event.getNativeKeyCode();
                if (KeyCodes.KEY_ENTER == keyCode) {
                    showPopup();
                }
            }));
            registerHandler(model.addValueChangeHandler(event -> {
                textBox.setText(model.getText());
                hidePopup();
            }));
        }
    };

    public SelectionBox() {
        textBox = new TextBox();
        textBox.setReadOnly(true);
        textBox.addStyleName("SelectionBox-textBox stroom-control allow-focus");

        svgIconBox = new SvgIconBox();
        svgIconBox.addStyleName("SelectionBox");
        svgIconBox.setWidget(textBox, SvgImage.DROP_DOWN);

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

    @Override
    public void setNonSelectString(final String nonSelectString) {
        model.setNonSelectString(nonSelectString);
        updateTextBox();
    }

    @Override
    public void addItems(final Collection<T> items) {
        model.addItems(items);
        updateTextBox();
    }

    @Override
    public void addItems(final T[] items) {
        model.addItems(items);
        updateTextBox();
    }

    @Override
    public void addItem(final T item) {
        model.addItem(item);
        updateTextBox();
    }

    @Override
    public void clear() {
        model.clear();
    }

    @Override
    public T getValue() {
        return model.getValue();
    }

    @Override
    public void setValue(final T value) {
        model.setValue(value);
        updateTextBox();
    }

    @Override
    public void setValue(final T value, final boolean fireEvents) {
        model.setValue(value, fireEvents);
    }

    @Override
    public com.google.gwt.event.shared.HandlerRegistration addValueChangeHandler(final ValueChangeHandler<T> handler) {
        return model.addValueChangeHandler(handler);
    }

    private void showPopup() {
        if (popup != null) {
            hidePopup();
        } else {
            popup = new SelectionPopup();
            popup.addAutoHidePartner(textBox.getElement());
            popup.setModel(model);
            final List<HandlerRegistration> popupHandlerRegistrations = new ArrayList<>();
            popupHandlerRegistrations.add(popup.addCloseHandler(event -> {
                focus();
                for (final HandlerRegistration handlerRegistration : popupHandlerRegistrations) {
                    handlerRegistration.removeHandler();
                }
                popupHandlerRegistrations.clear();
            }));
            popup.show(textBox);
        }
    }

    private void hidePopup() {
        if (popup != null) {
            popup.hide();
            popup = null;
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

    /**
     * Update the text box size to accommodate the longest item
     */
    private void updateTextBox() {
        final Optional<String> longestItem = model
                .getStrings()
                .stream()
                .max(Comparator.comparingInt(String::length));
        longestItem.ifPresent(s -> {
            if (s.length() > 0) {
                textBox.setVisibleLength(s.length());
            }
        });
        textBox.setValue(model.getText());
    }
}
