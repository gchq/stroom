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

public abstract class AbstractSelectionBox<T>
        extends Composite
        implements SelectionBoxView, Focus, HasValueChangeHandlers<T> {

    private final TextBox textBox;
    private final SvgIconBox svgIconBox;
    private SelectionListModel model;

    private SelectionPopup popup;
    private T value;

    private final EventBinder eventBinder = new EventBinder() {
        @Override
        protected void onBind() {
            registerHandler(textBox.addClickHandler(event -> showPopup()));
            registerHandler(svgIconBox.addClickHandler(event -> showPopup()));
            registerHandler(textBox.addKeyUpHandler(event -> {
                int keyCode = event.getNativeKeyCode();
                if (KeyCodes.KEY_ENTER == keyCode || KeyCodes.KEY_SPACE == keyCode) {
                    showPopup();
                }
            }));
        }
    };

    public AbstractSelectionBox() {
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
    public void setModel(final SelectionListModel model) {
        this.model = model;
//        model.setSelectionBox(this);
    }

    public TextBox getTextBox() {
        return textBox;
    }

    private void showPopup() {
        if (popup != null) {
            GWT.log("Hiding popup");
            hidePopup();
        } else {
            popup = new SelectionPopup();
            popup.addAutoHidePartner(textBox.getElement());
            popup.setModel(model);
            model.refresh();
            if (value != null) {
                popup.getSelectionModel().setSelected(wrap(value), true);
            }
            final List<HandlerRegistration> handlerRegistrations = new ArrayList<>();
            handlerRegistrations.add(popup.addCloseHandler(event -> {
                focus();
                for (final HandlerRegistration handlerRegistration : handlerRegistrations) {
                    handlerRegistration.removeHandler();
                }
                handlerRegistrations.clear();
                popup = null;
            }));
            handlerRegistrations.add(popup.getSelectionModel().addSelectionHandler(e -> {
                final SelectionItem selectionItem = popup.getSelectionModel().getSelected();
                if (selectionItem == null) {
                    setValue(null);
                } else {
                    setValue(unwrap(selectionItem));
                }
                hidePopup();
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

    public T getValue() {
        return value;
    }

    public void setValue(final T value) {
        this.value = value;
        if (value != null) {
            textBox.setValue(wrap(value).getLabel());
        } else {
            textBox.setValue("");
        }
    }

    @Override
    public com.google.gwt.event.shared.HandlerRegistration addValueChangeHandler(final ValueChangeHandler<T> handler) {
        return addHandler(handler, ValueChangeEvent.getType());
    }

    protected abstract T unwrap(SelectionItem selectionItem);

    protected abstract SelectionItem wrap(T item);
}
