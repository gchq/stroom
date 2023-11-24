package stroom.item.client;

import stroom.svg.client.SvgIconBox;
import stroom.svg.shared.SvgImage;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.TextBox;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.ArrayList;
import java.util.List;

public class BaseSelectionBox<T, I extends SelectionItem>
        extends Composite
        implements SelectionBoxView<T, I>, Focus, HasValueChangeHandlers<T> {

    private final TextBox textBox;
    private final SvgIconBox svgIconBox;
    private SelectionListModel<T, I> model;
    private final SelectionPopup<T, I> popup;
    private T value;
    private boolean showing;

    private final EventBinder eventBinder = new EventBinder() {
        @Override
        protected void onBind() {
            registerHandler(textBox.addClickHandler(event -> showPopup()));
            registerHandler(svgIconBox.addClickHandler(event -> showPopup()));
            registerHandler(textBox.addKeyDownHandler(event -> {
                int keyCode = event.getNativeKeyCode();
                if (KeyCodes.KEY_ENTER == keyCode || KeyCodes.KEY_SPACE == keyCode) {
                    showPopup();
                }
            }));
        }
    };

    public BaseSelectionBox() {
        textBox = new TextBox();
        textBox.setReadOnly(true);
        textBox.addStyleName("SelectionBox-textBox stroom-control allow-focus");

        svgIconBox = new SvgIconBox();
        svgIconBox.addStyleName("SelectionBox");
        svgIconBox.setWidget(textBox, SvgImage.DROP_DOWN);

        popup = new SelectionPopup<>();
        popup.addAutoHidePartner(textBox.getElement());

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
    public void setModel(final SelectionListModel<T, I> model) {
        this.model = model;
        popup.setModel(model);
    }

    private void showPopup() {
        if (showing) {
            GWT.log("Hiding popup");
            hidePopup();

        } else {
            final I selectionItem = model.wrap(value);
            if (selectionItem != null) {
                popup.getSelectionModel().setSelected(selectionItem, true);
            }
            final List<HandlerRegistration> handlerRegistrations = new ArrayList<>();
            handlerRegistrations.add(popup.addCloseHandler(event -> {
                focus();
                for (final HandlerRegistration handlerRegistration : handlerRegistrations) {
                    handlerRegistration.removeHandler();
                }
                handlerRegistrations.clear();
                showing = false;
            }));
            handlerRegistrations.add(popup.getSelectionModel().addSelectionHandler(e -> {
                final I selected = popup.getSelectionModel().getSelected();
                if (selected == null) {
                    setValue(null, true);
                } else {
                    setValue(model.unwrap(selected), true);
                }
                hidePopup();
            }));

            popup.show(textBox);
            showing = true;
        }
    }

    private void hidePopup() {
        popup.hide();
        showing = false;
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

    public T getValue() {
        return value;
    }

    public void setValue(final T value) {
        setValue(value, false);
    }

    public void setValue(final T value, final boolean fireEvents) {
        this.value = value;
        if (value != null) {
            textBox.setValue(model.wrap(value).getLabel());
        } else {
            textBox.setValue("");
        }

        if (fireEvents) {
            ValueChangeEvent.fire(this, value);
        }
    }

    @Override
    public com.google.gwt.event.shared.HandlerRegistration addValueChangeHandler(final ValueChangeHandler<T> handler) {
        return addHandler(handler, ValueChangeEvent.getType());
    }
}
