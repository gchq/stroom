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
import stroom.widget.tooltip.client.presenter.TooltipUtil;

import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.EventBus;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.event.shared.SimpleEventBus;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.TextBox;

import java.util.Optional;
import java.util.function.Supplier;

public class QuickFilter extends FlowPanel
        implements HasText, HasValueChangeHandlers<String> {

    private static final SafeHtml DEFAULT_POPUP_TEXT = TooltipUtil.builder()
            .addHeading("Quick Filter")
            .addLine("Field values containing the characters input will be included.")
            .build();

    private final TextBox textBox = new TextBox();
    private final SvgButton clearButton;
    private final SvgButton helpButton;
    private EventBus eventBus;
    private Supplier<SafeHtml> popupTextSupplier;

    public QuickFilter() {
        setStyleName("quickFilter");
        textBox.addStyleName("quickFilter-textBox");
        textBox.addStyleName("allow-focus");
        textBox.getElement().setAttribute("placeholder", "Quick Filter");

        clearButton = SvgButton.create(SvgPresets.CLEAR.title("Clear Filter"));
        clearButton.addStyleName("quickFilter-clear");

        helpButton = SvgButton.create(SvgPresets.HELP.title("Quick Filter Syntax Help"));
        helpButton.addStyleName("quickFilter-infoButton");

        add(textBox);
        add(clearButton);
        add(helpButton);

        textBox.addKeyUpHandler(event -> onChange());
        helpButton.addClickHandler(event -> showHelpPopup());
        clearButton.addClickHandler(event -> clear());

        onChange();
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

    public void focus() {
        textBox.setFocus(true);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }

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
