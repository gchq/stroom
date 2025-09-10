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

import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.SvgButton;
import stroom.widget.util.client.HtmlBuilder;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerManager;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FocusUtil;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.TextBox;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public class QuickFilter extends FlowPanel
        implements HasText, HasValueChangeHandlers<String> {

    private static final int DEBOUNCE_DELAY_MS = 400;
    private static final SafeHtml DEFAULT_POPUP_TEXT = new HtmlBuilder()
            .bold(hb -> hb.append("Quick Filter"))
            .br()
            .append("Field values containing the characters input will be included.")
            .br()
            .toSafeHtml();

    private final TextBox textBox = new TextBox();
    private final SvgButton clearButton;
    private final SvgButton helpButton;
    private final HandlerManager handlerManager = new HandlerManager(this);
    private Supplier<SafeHtml> popupTextSupplier;
    private String lastInput = "";

    private final Timer filterRefreshTimer = new Timer() {
        @Override
        public void run() {
            // Fire the event to update the data based on the filter
            ValueChangeEvent.fire(QuickFilter.this, textBox.getText());
        }
    };

    public QuickFilter() {
        setStyleName("quickFilter");
        textBox.addStyleName("quickFilter-textBox");
        textBox.addStyleName("allow-focus");
        textBox.getElement().setAttribute("placeholder", "Quick Filter");

        clearButton = SvgButton.create(SvgPresets.CLEAR.title("Clear Filter"));
        clearButton.addStyleName("clear");

        helpButton = SvgButton.create(SvgPresets.HELP.title("Quick Filter Syntax Help"));
        helpButton.addStyleName("info");

        add(textBox);
        add(clearButton);
        add(helpButton);

        textBox.addValueChangeHandler(event -> onChange(true));
        textBox.addKeyDownHandler(this::onKeyDown);
        helpButton.addClickHandler(event -> showHelpPopup());
        clearButton.addClickHandler(event -> clear());

        onChange(true);
    }

    private void showHelpPopup() {
        final SafeHtml popupText = Optional.ofNullable(popupTextSupplier)
                .map(Supplier::get)
                .filter(safeHtml -> !safeHtml.asString().isEmpty())
                .orElse(DEFAULT_POPUP_TEXT);

        final HelpPopup popup = new HelpPopup(popupText);
        popup.setStyleName("quickFilter-tooltip");
        popup.setPopupPositionAndShow((offsetWidth, offsetHeight) -> {

            // Position it below the filter
            popup.setPopupPosition(
                    getAbsoluteLeft() + 4,
                    getAbsoluteTop() + 26);
        });
    }

    private void onChange(final boolean fireEvents) {
        final String text = textBox.getText();
        final boolean isNotEmpty = text.length() > 0;
        clearButton.setEnabled(isNotEmpty);
        clearButton.setVisible(isNotEmpty);

        if (!Objects.equals(text, lastInput)) {
            lastInput = text;
            if (handlerManager != null) {
                if (fireEvents) {
                    // Add in a slight delay to give the user a chance to type a few chars before we fire off
                    // a rest call. This helps to reduce the logging too
                    if (!filterRefreshTimer.isRunning()) {
                        filterRefreshTimer.schedule(DEBOUNCE_DELAY_MS);
                    }
                }
            }
        }
    }

    protected void onKeyDown(final KeyDownEvent event) {
        // Clear the text box if ESC is pressed
        if (event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
            textBox.setText("");
        }
    }

    public void reset() {
        textBox.setText("");
    }

    @Override
    public void clear() {
        textBox.setText("");
        onChange(true);
    }

    public void registerPopupTextProvider(final Supplier<SafeHtml> popupTextSupplier) {
        this.popupTextSupplier = popupTextSupplier;
    }

    @Override
    public String getText() {
        return textBox.getText();
    }

    @Override
    public void setText(final String text) {
        setText(text, true);
    }

    public void setText(final String text, final boolean fireEvents) {
        textBox.setValue(text, fireEvents);
        onChange(fireEvents);
    }

    @Override
    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<String> handler) {
        return handlerManager.addHandler(ValueChangeEvent.getType(), handler);
    }

    public HandlerRegistration addKeyDownHandler(final KeyDownHandler handler) {
        return textBox.addDomHandler(handler, KeyDownEvent.getType());
    }

    public void forceFocus() {
        FocusUtil.forceFocus(this::focus);
    }

    public void focus() {
        textBox.selectAll();
        textBox.setFocus(true);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        handlerManager.fireEvent(event);
    }


    // --------------------------------------------------------------------------------


    private static class HelpPopup extends PopupPanel {

        public HelpPopup(final SafeHtml content) {
//        public HelpPopup(final Supplier<String> popupTextSupplier) {
            // PopupPanel's constructor takes 'auto-hide' as its boolean parameter.
            // If this is set, the panel closes itself automatically when the user
            // clicks outside of it.
            super(true);

//            setWidget(new Label(popupTextSupplier.get(), true));
            setWidget(new HTMLPanel(content));
//            setWidget(new HTMLPanel(SafeHtmlUtils.fromTrustedString("<b>hello</b>")));
        }
    }
}
