/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.widget.dropdowntree.client.view;

import com.google.gwt.core.shared.GWT;
import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.EventBus;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.event.shared.SimpleEventBus;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.resources.client.CssResource.ImportedWithPrefix;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.SvgButton;

public class QuickFilter extends FlowPanel
        implements HasText, HasValueChangeHandlers<String> {
    private static final Resources RESOURCES = GWT.create(Resources.class);
    private final Label label = new Label("Quick Filter", false);
    private final TextBox textBox = new TextBox();
    private final SvgButton clearButton;
    private EventBus eventBus;

    public QuickFilter() {
        RESOURCES.style().ensureInjected();

        setStyleName(RESOURCES.style().quickFilter() + " stroom-border stroom-content");
        textBox.setStyleName(RESOURCES.style().textBox());
        label.setStyleName(RESOURCES.style().label());

        clearButton = SvgButton.create(SvgPresets.CLEAR);
        clearButton.addStyleName(RESOURCES.style().clear());

        add(textBox);
        add(label);
        add(clearButton);

        label.addClickHandler(event -> {
            label.setVisible(false);
            textBox.setFocus(true);
        });

        textBox.addFocusHandler(event -> label.setVisible(false));
        textBox.addBlurHandler(event -> reset());
        textBox.addKeyUpHandler(event -> onChange());

        clearButton.addClickHandler(event -> clear());

        onChange();
    }

    private void onChange() {
        final String text = textBox.getText();
        final boolean enabled = text.length() > 0;
        clearButton.setEnabled(enabled);
        clearButton.setVisible(enabled);
        if (eventBus != null) {
            ValueChangeEvent.fire(this, text);
        }
    }

    @Override
    public void clear() {
        textBox.setText("");
        onChange();
        reset();
    }

    public void reset() {
        if (textBox.getText().length() == 0) {
            label.setVisible(true);
        }
    }

    @Override
    public String getText() {
        return textBox.getText();
    }

    @Override
    public void setText(final String text) {
        textBox.setText(text);
    }

    @Override
    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<String> handler) {
        return getEventBus().addHandler(ValueChangeEvent.getType(), handler);
    }

    private EventBus getEventBus() {
        if (eventBus == null) {
            eventBus = new SimpleEventBus();
        }
        return eventBus;
    }

//    @Override
//    public HandlerRegistration addKeyDownHandler(final KeyDownHandler handler) {
//        return textBox.addKeyDownHandler(handler);
//    }
//
//    @Override
//    public HandlerRegistration addKeyUpHandler(final KeyUpHandler handler) {
//        return textBox.addKeyUpHandler(handler);
//    }
//
//    @Override
//    public HandlerRegistration addKeyPressHandler(final KeyPressHandler handler) {
//        return textBox.addKeyPressHandler(handler);
//    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }

    @ImportedWithPrefix("stroom-quickfilter")
    public interface Style extends CssResource {
        String DEFAULT_CSS = "QuickFilter.css";

        String quickFilter();

        String textBox();

        String label();

        String clear();
    }

    public interface Resources extends ClientBundle {
        @Source(Style.DEFAULT_CSS)
        Style style();
    }
}
