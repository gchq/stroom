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

import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.InlineSvgButton;
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
            .bold("Quick Filter")
            .br()
            .append("Field values containing the characters input will be included.")
            .br()
            .toSafeHtml();

    private final TextBox textBox = new TextBox();
    private final InlineSvgButton clearButton;
    private final InlineSvgButton helpButton;
    private final HandlerManager handlerManager = new HandlerManager(this);
    private Supplier<SafeHtml> popupTextSupplier;
    private String lastInput = "";
    private boolean updateOnValueChange = true;
    private HelpPopup helpPopup = null;
    private String filterError = null;

    private final Timer filterRefreshTimer = new Timer() {
        @Override
        public void run() {
            updateValue(true);
        }
    };

    public QuickFilter() {
        setStyleName("quickFilter");
        textBox.addStyleName("quickFilter-textBox");
        textBox.addStyleName("allow-focus");
        textBox.getElement().setAttribute("placeholder", "Quick Filter");

        clearButton = new InlineSvgButton();
        clearButton.setSvg(SvgImage.CLEAR);
        clearButton.setTitle("Clear Filter");
        clearButton.addStyleName("clear");

        helpButton = new InlineSvgButton();
        helpButton.setSvg(SvgImage.HELP_OUTLINE);
        helpButton.setTitle("Quick Filter Syntax Help");
        helpButton.addStyleName("help-button info");

        add(textBox);
        add(clearButton);
        add(helpButton);

        textBox.addValueChangeHandler(event -> onValueChange());
        textBox.addKeyDownHandler(this::onKeyDown);
        helpButton.addClickHandler(event -> showHelpPopup());
        clearButton.addClickHandler(event -> clear());

        enableButtons();
    }

    private void showHelpPopup() {
        if (helpPopup != null) {
            helpPopup.hide();
            helpPopup = null;
        } else {
            final SafeHtml syntaxHelp = Optional.ofNullable(popupTextSupplier)
                    .map(Supplier::get)
                    .filter(safeHtml -> !safeHtml.asString().isEmpty())
                    .orElse(DEFAULT_POPUP_TEXT);

            final HelpPopup popup = new HelpPopup(withFilterError(syntaxHelp));
            popup.setStyleName("quickFilter-tooltip");
            popup.setPopupPositionAndShow((offsetWidth, offsetHeight) -> {

                // Position it below the filter
                popup.setPopupPosition(
                        getAbsoluteLeft() + 4,
                        getAbsoluteTop() + 26);
            });
            this.helpPopup = popup;
        }
    }

    /**
     * Show why the server rejected this filter, or clear it when passed null.
     * <p>
     * A rejected filter and a filter that matched nothing both come back as an empty grid, so
     * without this the user has no way to tell "your syntax is not supported here" from "there is
     * nothing to find". The message travels back on the response - see
     * {@code ResultPage.filterError} - and this is what puts it in front of the user.
     * <p>
     * Deliberately not positional. The diagnostic carries the offending token's location, but a
     * TextBox cannot underline a substring, so the location is unused for now; see
     * docs/query-filter-surface-syntax-spec.md §10.5 for the options if that becomes worth doing.
     */
    public void setFilterError(final String filterError) {
        if (Objects.equals(this.filterError, filterError)) {
            return;
        }
        this.filterError = filterError;

        // Reuse the app-wide invalid styling rather than inventing one - it is already themed for
        // light and dark, and already handles the focused state.
        if (filterError == null) {
            textBox.removeStyleName("invalid");
            textBox.getElement().removeAttribute("title");
        } else {
            textBox.addStyleName("invalid");
            // Native title so it is discoverable on hover without opening the help popup.
            textBox.getElement().setAttribute("title", filterError);
        }
        // If the popup is already open, reopen it so it picks up the change.
        if (helpPopup != null) {
            helpPopup.hide();
            helpPopup = null;
            showHelpPopup();
        }
    }

    private SafeHtml withFilterError(final SafeHtml syntaxHelp) {
        if (filterError == null) {
            return syntaxHelp;
        }
        return new HtmlBuilder()
                .bold("This filter was not applied")
                .br()
                .append(filterError)
                .br()
                .br()
                .append(syntaxHelp)
                .toSafeHtml();
    }

    private void onValueChange() {
        enableButtons();
        if (updateOnValueChange) {
            // Add in a slight delay to give the user a chance to type a few chars before we fire off
            // a rest call. This helps to reduce the logging too
            filterRefreshTimer.cancel();
            filterRefreshTimer.schedule(DEBOUNCE_DELAY_MS);
        }
    }

    private void onChange(final boolean fireEvents) {
        enableButtons();
        updateValue(fireEvents);
    }

    private void updateValue(final boolean fireEvents) {
        final String text = textBox.getText();
        if (!Objects.equals(text, lastInput)) {
            lastInput = text;
            // Cancel an update timer if one is running.
            filterRefreshTimer.cancel();
            if (fireEvents) {
                // Fire the event to update the data based on the filter
                ValueChangeEvent.fire(QuickFilter.this, text);
            }
        }
    }

    private void enableButtons() {
        final String text = textBox.getText();
        final boolean isNotEmpty = !text.isEmpty();
        clearButton.setEnabled(isNotEmpty);
        clearButton.setVisible(isNotEmpty);
    }

    protected void onKeyDown(final KeyDownEvent event) {
        // Clear the text box if ESC is pressed
        if (event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
            clear();
        } else if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            onChange(true);
        }
    }

    public void reset() {
        textBox.setText("");
    }

    @Override
    public void clear() {
        textBox.setText("");
        setFilterError(null);
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
