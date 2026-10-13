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
import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.event.dom.client.BlurEvent;
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
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Event.NativePreviewEvent;
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
    private final ToolbarButton clearButton;
    private final ToolbarButton dropDownButton;
    private final ToolbarButton helpButton;
    private final HandlerManager handlerManager = new HandlerManager(this);
    private Supplier<SafeHtml> popupTextSupplier;
    private String lastInput = "";
    private boolean updateOnValueChange = true;
    private HelpPopup helpPopup = null;
    private String filterError = null;
    private QuickFilterContextHandler contextHandler = null;
    private final QuickFilterHistoryTracker historyTracker = new QuickFilterHistoryTracker(text -> {
        if (contextHandler != null) {
            contextHandler.recordUse(text);
        }
    });

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

        // The filter's buttons, at the end of its box
        final Toolbar toolbar = new Toolbar("Quick filter");
        toolbar.addStyleName("quickFilter-toolbar");
        // A plain cross, so that Clear and the drop-down, which both act on the text, look alike, and
        // only help, which doesn't, has a ring
        clearButton = toolbar.button(SvgImage.TAB_CLOSE, "Clear Filter", this::onClear);
        // Between clear and help: clear and the drop-down both act on the text, like a combo
        // box, and help stays outermost as it is on every screen. Hidden until a handler is set.
        dropDownButton = toolbar.button(SvgImage.DROP_DOWN, "Recent Filters", this::showRecentFilters);
        dropDownButton.setVisible(false);
        helpButton = toolbar.button(SvgImage.HELP_OUTLINE, "Quick Filter Syntax Help", this::showHelpPopup);

        add(textBox);
        add(toolbar);

        textBox.addValueChangeHandler(event -> onValueChange());
        textBox.addKeyDownHandler(this::onKeyDown);
        textBox.addBlurHandler(this::onBlur);

        enableButtons();
    }

    private void onClear() {
        clear();
        // The button hides itself once the text is empty, which would leave focus nowhere
        textBox.setFocus(true);
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
            // A mousedown on the help button mustn't auto-hide the popup, so that its click can
            // toggle it shut
            popup.addAutoHidePartner(helpButton.getElement());
            // Forget the popup however it is hidden (e.g. auto-hidden by a click elsewhere, or by
            // Escape), so that the next click on the help button shows it again
            popup.addCloseHandler(event -> {
                if (helpPopup == popup) {
                    helpPopup = null;
                }
            });
            popup.setPopupPositionAndShow((offsetWidth, offsetHeight) -> {

                // Position it below the filter, clear of the buttons in it whatever its height
                popup.setPopupPosition(
                        getAbsoluteLeft() + 4,
                        getAbsoluteTop() + getOffsetHeight());
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
        // Every result, accepted or not, comes through here, so this is where the widget learns
        // whether the text it sent was good. lastInput is the text the answered query was sent
        // with - near enough: if the user has typed on since, the next verdict overrides this.
        historyTracker.verdict(lastInput, filterError == null);
        applyFilterError(filterError);
    }

    private void applyFilterError(final String filterError) {
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

    /**
     * Give this quick filter a context: an arrow appears on the right, and the filters the user
     * commits here are remembered and offered back. See {@link QuickFilterContextHandler}.
     */
    public void setContextHandler(final QuickFilterContextHandler contextHandler) {
        this.contextHandler = contextHandler;
        final boolean hasContext = contextHandler != null;
        dropDownButton.setVisible(hasContext);
        if (hasContext) {
            addStyleName("hasDropDown");
        } else {
            removeStyleName("hasDropDown");
        }
    }

    private void showRecentFilters() {
        if (contextHandler != null && textBox.isEnabled()) {
            contextHandler.showRecentFilters(this, text -> {
                setText(text, true);
                historyTracker.chosen(text);
                focus();
            });
        }
    }

    /**
     * The user has committed the current text - Enter, or leaving the box. Whether that becomes
     * a history entry is {@link QuickFilterHistoryTracker}'s decision.
     */
    private void commit() {
        if (contextHandler != null) {
            historyTracker.commit(textBox.getText());
        }
    }

    private void onBlur(final BlurEvent event) {
        commit();
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
        final boolean isNotEmpty = !text.isEmpty() && textBox.isEnabled();
        clearButton.setEnabled(isNotEmpty);
        clearButton.setVisible(isNotEmpty);
        // Choosing a recent filter would write into a box that cannot be typed in
        dropDownButton.setEnabled(textBox.isEnabled());
    }

    protected void onKeyDown(final KeyDownEvent event) {
        // Clear the text box if ESC is pressed
        if (event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
            clear();
        } else if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            onChange(true);
            commit();
        } else if (event.getNativeKeyCode() == KeyCodes.KEY_DOWN && event.isAltKeyDown()) {
            // Combo-box convention for opening the list from the keyboard.
            event.preventDefault();
            showRecentFilters();
        }
    }

    /// Enables or disables the filter's text. Its syntax help stays available.
    ///
    /// @param enabled Whether the filter can be typed in.
    public void setEnabled(final boolean enabled) {
        textBox.setEnabled(enabled);
        setStyleName("disabled", !enabled);
        enableButtons();
    }

    public void reset() {
        textBox.setText("");
    }

    @Override
    public void clear() {
        textBox.setText("");
        // Not setFilterError(null): clearing is not a server verdict on the old text.
        historyTracker.cleared();
        applyFilterError(null);
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

        @Override
        protected void onPreviewNativeEvent(final NativePreviewEvent event) {
            super.onPreviewNativeEvent(event);
            // Escape closes it, as it does Stroom's other popups
            if (event.getTypeInt() == Event.ONKEYDOWN
                && KeyBinding.test(event.getNativeEvent()) == Action.CLOSE) {
                event.cancel();
                hide();
            }
        }
    }
}
