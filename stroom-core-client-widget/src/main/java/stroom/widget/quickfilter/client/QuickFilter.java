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

package stroom.widget.quickfilter.client;

import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.form.client.FieldValidity;
import stroom.widget.form.client.TextBox;
import stroom.widget.help.client.HelpPopup;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.popup.client.PopupPosition.PopupLocation;
import stroom.widget.text.client.Label;
import stroom.widget.util.client.FieldEnd;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.Rect;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Element;
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
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FocusUtil;
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.Widget;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// A quick filter: a search box whose text filters a list, applied a moment after typing stops (or
/// on Enter), with buttons over its end that clear it, show the recent filters (when it has a
/// context) and show its syntax help. The BEM block `quick-filter` (`quick-filter.css`), its markup
/// `QuickFilter.ui.xml`.
public class QuickFilter extends Composite
        implements HasText, HasValueChangeHandlers<String> {

    private static final Binder BINDER = GWT.create(Binder.class);

    private static final int DEBOUNCE_DELAY_MS = 400;
    private static final String BLOCK = "quick-filter";
    private static final String HELP_NAME = "Quick Filter Syntax Help";
    private static final SafeHtml DEFAULT_POPUP_TEXT = new HtmlBuilder()
            .bold("Quick Filter")
            .br()
            .append("Field values containing the characters input will be included.")
            .br()
            .toSafeHtml();

    @UiField
    TextBox textBox;
    @UiField
    Toolbar toolbar;
    // A plain cross, so that Clear and the drop-down, which both act on the text, look alike, and
    // only help, which doesn't, has a ring
    @UiField
    ToolbarButton clearButton;
    // Between clear and help: clear and the drop-down both act on the text, like a combo box, and
    // help stays outermost as it is on every screen. Hidden until a context handler is set.
    @UiField
    ToolbarButton dropDownButton;
    @UiField
    ToolbarButton helpButton;
    // Hidden, the description of the text box, saying what to type, read when it is focused
    @UiField
    Label description;
    // Why the server rejected the filter, for screen readers only (sighted users have the box's
    // invalid styling and hover text). A live region, as the focus stays in the box while typing,
    // and the box's description, so it is read again when the box is focused. Not hidden
    // (display: none), which would hide it from screen readers too.
    @UiField
    DivElement errorMessage;
    // Keeps the text clear of the buttons, which come and go (Clear, while there is text)
    private final FieldEnd fieldEnd;
    private final HandlerManager handlerManager = new HandlerManager(this);
    private Supplier<SafeHtml> popupTextSupplier;
    private String lastInput = "";
    private boolean updateOnValueChange = true;
    private HelpPopup helpPopup = null;
    private String filterError = null;
    private QuickFilterContextHandler contextHandler = null;
    private Consumer<String> onChange;
    // What Down in the filter does, e.g. moves into the list below it
    private Runnable moveDown;
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
        initWidget(BINDER.createAndBindUi(this));
        clearButton.setOnClick(this::onClear);
        dropDownButton.setOnClick(this::showRecentFilters);
        helpButton.setOnClick(this::showHelpPopup);
        HelpPopup.markOpener(helpButton.getElement());

        description.getElement().setId(DOM.createUniqueId());
        errorMessage.setId(DOM.createUniqueId());
        fieldEnd = new FieldEnd(textBox.getElement(), toolbar.getElement());

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

            final HelpPopup popup = new HelpPopup();
            popup.setContent(withFilterError(syntaxHelp));
            popup.setAccessibleName(HELP_NAME);
            // A mousedown on the help button mustn't auto-hide the popup, so that its click can
            // toggle it shut
            popup.setOpener(helpButton.getElement());
            // Forget the popup however it is hidden (e.g. auto-hidden by a click elsewhere, or by
            // Escape), so that the next click on the help button shows it again
            popup.setOnClose(() -> {
                if (helpPopup == popup) {
                    helpPopup = null;
                }
            });
            // Below the filter, clear of the buttons in it whatever its height
            popup.show(new PopupPosition(new Rect(getElement()), PopupLocation.BELOW));
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
        // light and dark, and already handles the focused state. FieldValidity also marks the box
        // aria-invalid, so assistive technology is told too.
        final Element textBoxElement = textBox.getElement();
        final String errorMessageId = errorMessage.getId();
        if (filterError == null) {
            FieldValidity.setInvalid(textBoxElement, false);
            FieldValidity.removeDescribedBy(textBoxElement, errorMessageId);
            errorMessage.setInnerText("");
            textBoxElement.removeAttribute("title");
        } else {
            FieldValidity.setInvalid(textBoxElement, true);
            // Read before what may be typed (see setDescription)
            FieldValidity.addDescribedBy(textBoxElement, errorMessageId, true);
            errorMessage.setInnerText(filterError);
            // Native title so it is discoverable on hover without opening the help popup.
            textBoxElement.setAttribute("title", filterError);
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
        // Leaves room in the box for the recent filters' button as well
        setStyleName(BLOCK + "--recent", hasContext);
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
                if (onChange != null) {
                    onChange.accept(text);
                }
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
        // Escape clears the text box; an empty one leaves it to what holds the filter (e.g. a dialog,
        // which closes)
        if (event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
            if (!textBox.getText().isEmpty()) {
                event.preventDefault();
                clear();
            }
        } else if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            onChange(true);
            commit();
        } else if (event.getNativeKeyCode() == KeyCodes.KEY_DOWN && event.isAltKeyDown()) {
            // Combo-box convention for opening the list from the keyboard.
            event.preventDefault();
            showRecentFilters();
        } else if (event.getNativeKeyCode() == KeyCodes.KEY_DOWN && moveDown != null
                   && !event.isControlKeyDown() && !event.isShiftKeyDown() && !event.isMetaKeyDown()) {
            // Into the list below the filter
            event.preventDefault();
            moveDown.run();
        }
    }

    /// Sets what Down in the filter does, e.g. moves the focus into the list it filters. Alt+Down
    /// still shows the recent filters.
    ///
    /// @param moveDown What to do, or null for nothing.
    public void setMoveDown(final Runnable moveDown) {
        this.moveDown = moveDown;
    }

    /// Enables or disables the filter's text. Its syntax help stays available.
    ///
    /// @param enabled Whether the filter can be typed in.
    public void setEnabled(final boolean enabled) {
        textBox.setEnabled(enabled);
        enableButtons();
    }

    public void reset() {
        textBox.setText("");
    }

    /// Empties the filter, and applies that at once.
    public void clear() {
        textBox.setText("");
        // Not setFilterError(null): clearing is not a server verdict on the old text.
        historyTracker.cleared();
        applyFilterError(null);
        onChange(true);
    }

    /// Names the filter's text box for screen readers, in place of its placeholder, which is only a
    /// hint and is lost once something is typed.
    ///
    /// @param accessibleName What the filter is for, e.g. `Find by name`, or null or blank to name it
    ///                       by its placeholder.
    public void setAccessibleName(final String accessibleName) {
        textBox.setAriaLabel(accessibleName);
    }

    /// Says what may be typed in the filter, so a screen reader reads it when the text box is
    /// focused. The syntax help button gives the full detail.
    ///
    /// @param descriptionText What may be typed, or null or blank for nothing.
    public void setDescription(final String descriptionText) {
        final Element textBoxElement = textBox.getElement();
        final String id = description.getElement().getId();
        FieldValidity.removeDescribedBy(textBoxElement, id);
        if (descriptionText == null || descriptionText.isBlank()) {
            description.setText("");
        } else {
            description.setText(descriptionText);
            FieldValidity.addDescribedBy(textBoxElement, id, false);
        }
    }

    /// @param onChange Told the filter's text when it is applied (a moment after typing stops, on
    ///                 Enter, or when it is cleared or set), or null for nothing.
    public void setOnChange(final Consumer<String> onChange) {
        this.onChange = onChange;
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

    /// Puts the focus in the filter, trying again for a moment (e.g. while the popup it is in
    /// settles), but not once it has had the focus and it has moved on (e.g. the user has gone into
    /// the list below with Down), which would pull it back.
    public void forceFocus() {
        final boolean[] hadFocus = {false};
        FocusUtil.forceFocus(() -> {
            if (!hadFocus[0] && !hasFocus()) {
                focus();
            }
            // Once it has the focus, the later tries leave it wherever it goes
            hadFocus[0] = hadFocus[0] || hasFocus();
        });
    }

    private boolean hasFocus() {
        return isFocused(textBox.getElement());
    }

    private static native boolean isFocused(Element element) /*-{
        return element.ownerDocument.activeElement === element;
    }-*/;

    /// Adds typed text to the end of the filter, applies it at once, and puts the focus in the filter
    /// with the caret after it, so typing carries on there (e.g. a selection box passes on what was
    /// typed in it when it opened its list).
    ///
    /// @param text The text typed.
    public void type(final String text) {
        setText(getText() + text, true);
        textBox.setFocus(true);
        textBox.setCursorPos(textBox.getText().length());
    }

    public void focus() {
        textBox.selectAll();
        textBox.setFocus(true);
    }

    /// Gets the button that opens the recent filters' menu, for the menu's opener, which says
    /// whether the menu is open.
    ///
    /// @return The button's element.
    public Element getRecentFiltersButton() {
        return dropDownButton.getElement();
    }

    @Override
    protected void onLoad() {
        super.onLoad();
        fieldEnd.start();
    }

    @Override
    protected void onUnload() {
        fieldEnd.stop();
        super.onUnload();
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        handlerManager.fireEvent(event);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, QuickFilter> {

    }
}
