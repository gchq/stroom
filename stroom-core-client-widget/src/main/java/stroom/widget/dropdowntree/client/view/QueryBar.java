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
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.InlineSvgButton;
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
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FocusUtil;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.TextBox;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public class QueryBar extends FlowPanel
        implements HasText, HasValueChangeHandlers<String> {

    private static final SafeHtml DEFAULT_POPUP_TEXT = new HtmlBuilder()
            .bold(hb -> hb.append("Quick Filter"))
            .br()
            .append("Field values containing the characters input will be included.")
            .br()
            .toSafeHtml();

    private final TextBox textBox = new TextBox();
    private final SvgButton clearButton;
    private final SvgButton helpButton;
    private final SvgButton searchButton;
    private final InlineSvgButton dropDownButton;
    private final HandlerManager handlerManager = new HandlerManager(this);
    private Supplier<SafeHtml> popupTextSupplier;
    private String lastInput = "";

    public QueryBar() {
        setStyleName("queryBar");
        textBox.addStyleName("queryBar-textBox");
        textBox.addStyleName("allow-focus");
//        textBox.getElement().setAttribute("placeholder", "Quick Filter");

        final ButtonPanel buttonPanel = new ButtonPanel();
        buttonPanel.addStyleName("queryBar-buttonPanel");
        clearButton = SvgButton.create(SvgPresets.CLEAR.title("Clear"));
        clearButton.addStyleName("clear");
        helpButton = SvgButton.create(SvgPresets.HELP.title("Help"));
        helpButton.addStyleName("info");
        searchButton = SvgButton.create(SvgPresets.FIND.title("Search"));
        searchButton.addStyleName("search");
//        dropDownButton = SvgButton.create(SvgPresets.EXPAND_DOWN.title("More"));
//        dropDownButton.addStyleName("more");



        searchButton = new InlineSvgButton();
        searchButton.setSvg(SvgImage.FIND);
        searchButton.getElement().addClassName("queryBar-buttonPanel-search");
        searchButton.setTitle("search");

        dropDownButton = new InlineSvgButton();
        dropDownButton.setSvg(SvgImage.ARROW_DOWN);
        dropDownButton.getElement().addClassName("queryBar-buttonPanel-more");
        dropDownButton.setTitle("More");
//        dropDownButton.setEnabled(true);

        buttonPanel.addButton(clearButton);
        buttonPanel.addButton(dropDownButton);
        buttonPanel.addButton(helpButton);
        buttonPanel.addButton(searchButton);

        add(textBox);
        add(buttonPanel);

        textBox.addValueChangeHandler(event -> onChange());
        textBox.addKeyDownHandler(this::onKeyDown);
        searchButton.addClickHandler(event -> search());
        helpButton.addClickHandler(event -> showHelpPopup());
        clearButton.addClickHandler(event -> clear());
        dropDownButton.addClickHandler(event -> showDropDown());

        onChange();
    }

    private void showHelpPopup() {
        final SafeHtml popupText = Optional.ofNullable(popupTextSupplier)
                .map(Supplier::get)
                .filter(safeHtml -> !safeHtml.asString().isEmpty())
                .orElse(DEFAULT_POPUP_TEXT);

        final HelpPopup popup = new HelpPopup(popupText);
        popup.setStyleName("queryBar-tooltip");
        popup.setPopupPositionAndShow((offsetWidth, offsetHeight) -> {

            // Position it below the filter
            popup.setPopupPosition(
                    getAbsoluteLeft() + 4,
                    getAbsoluteTop() + 26);
        });
    }

    private void showDropDown() {
//        final SafeHtml popupText = Optional.ofNullable(popupTextSupplier)
//                .map(Supplier::get)
//                .filter(safeHtml -> !safeHtml.asString().isEmpty())
//                .orElse(DEFAULT_POPUP_TEXT);
//
//        final HelpPopup popup = new HelpPopup(popupText);
//        popup.setStyleName("queryBar-tooltip");
//        popup.setPopupPositionAndShow((offsetWidth, offsetHeight) -> {
//
//            // Position it below the filter
//            popup.setPopupPosition(
//                    getAbsoluteLeft() + 4,
//                    getAbsoluteTop() + 26);
//        });
    }

    private void onChange() {
        final String text = textBox.getText();
        final boolean isNotEmpty = !text.isEmpty();
        clearButton.setEnabled(isNotEmpty);
        clearButton.setVisible(isNotEmpty);

//        if (!Objects.equals(text, lastInput)) {
//            lastInput = text;
//            if (handlerManager != null) {
//                // Add in a slight delay to give the user a chance to type a few chars before we fire off
//                // a rest call. This helps to reduce the logging too
//                if (!filterRefreshTimer.isRunning()) {
//                    filterRefreshTimer.schedule(DEBOUNCE_DELAY_MS);
//                }
//            }
//        }
    }

    protected void onKeyDown(final KeyDownEvent event) {
        // Clear the text box if ESC is pressed
        if (event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
            clear();
        } else if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            search();
        }
    }

    private void search() {
        final String text = textBox.getText();
        if (!Objects.equals(text, lastInput)) {
            lastInput = text;
            // Fire the event to update the data based on the filter
            ValueChangeEvent.fire(QueryBar.this, textBox.getText());
        }
    }

    public void reset() {
        textBox.setText("");
    }

    @Override
    public void clear() {
        textBox.setText("");
        onChange();
    }

    public void registerPopupTextProvider(final Supplier<SafeHtml> popupTextSupplier) {
        this.popupTextSupplier = popupTextSupplier;
    }

//    public void registerClickHandler(final Supplier<String> popupTextProvider) {
//        this.popupTextSupplier = popupTextSupplier;
//    }

    @Override
    public String getText() {
        return textBox.getText();
    }

    @Override
    public void setText(final String text) {
        textBox.setText(text);
        onChange();
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
