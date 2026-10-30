/*
 * Copyright 2026 Crown Copyright
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

package stroom.data.client.component;

import stroom.util.shared.Count;
import stroom.util.shared.HasCharacterData;
import stroom.util.shared.HasCharacterData.NavigationMode;
import stroom.widget.button.client.LinkButton;
import stroom.widget.button.client.RefreshButton;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.Optional;
import java.util.function.Consumer;

/// The source viewer's character navigator: the lines and characters shown (e.g. "Chars 1 to 1,000
/// of 5,000"), as links that ask to set the range shown, and Show Beginning, Advance Backwards,
/// Advance Forwards and Refresh buttons. The links and the moving buttons are hidden while there is
/// nothing to move through; the refresh button stays, disabled until there is a range. A
/// button is disabled once pressed, until the range shown next arrives ([#show(HasCharacterData)]).
/// Its markup is `CharacterNavigator.ui.xml`; [CharacterNavigatorController] fills it.
public class CharacterNavigator extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String LINES_TITLE = "Lines";
    private static final String CHARACTERS_TITLE = "Chars";
    // What pressing a range link does, which it is named for as well as the range it shows
    private static final String SET_RANGE = "Set Source Range";
    private static final String UNKNOWN_VALUE = "?";
    private static final NumberFormat NUMBER_FORMATTER = NumberFormat.getFormat("#,###");

    @UiField
    LinkButton lblLines;
    @UiField
    LinkButton lblCharacters;
    @UiField
    ToolbarButton showHeadCharactersBtn;
    @UiField
    ToolbarButton advanceCharactersForwardBtn;
    @UiField
    ToolbarButton advanceCharactersBackwardBtn;
    @UiField
    RefreshButton refresh;

    private Consumer<NavigatorAction> onAction;

    /// Makes a navigator with nothing to show.
    public CharacterNavigator() {
        initWidget(BINDER.createAndBindUi(this));
        lblLines.setOnClick(() -> act(NavigatorAction.SELECT));
        lblCharacters.setOnClick(() -> act(NavigatorAction.SELECT));
        showHeadCharactersBtn.setOnClick(() -> act(showHeadCharactersBtn, NavigatorAction.FIRST));
        advanceCharactersBackwardBtn.setOnClick(() -> act(advanceCharactersBackwardBtn, NavigatorAction.PREVIOUS));
        advanceCharactersForwardBtn.setOnClick(() -> act(advanceCharactersForwardBtn, NavigatorAction.NEXT));
        refresh.setOnClick(() -> act(refresh, NavigatorAction.REFRESH));
    }

    /// @param onAction Given what the user asks for: [NavigatorAction#FIRST] (show the
    ///                 beginning), [NavigatorAction#PREVIOUS] and [NavigatorAction#NEXT] (advance the
    ///                 range), [NavigatorAction#REFRESH] or [NavigatorAction#SELECT] (set the range).
    public void setOnAction(final Consumer<NavigatorAction> onAction) {
        this.onAction = onAction;
    }

    /// Shows the range shown and enables the buttons that can move it, or hides the navigator if
    /// there is nothing to move through.
    ///
    /// @param display The source, or null for none.
    public void show(final HasCharacterData display) {
        if (display == null || !display.areNavigationControlsVisible()) {
            setControlVisibility(display, false);
            return;
        }
        if (NavigationMode.CHARS.equals(display.getNavigationMode())
            && display.getCharOffsetFrom().isPresent()
            && display.getCharOffsetTo().isPresent()) {
            // Standard char based data
            setRangeText(lblLines, display.getLineFrom().isPresent() && display.getLineTo().isPresent()
                    ? LINES_TITLE + " " + format(display.getLineFrom()) + " to " + format(display.getLineTo())
                    : "");
            setRangeText(lblCharacters, CHARACTERS_TITLE
                                        + " " + format(display.getCharOffsetFrom().map(offset -> offset + 1))
                                        + " to " + format(display.getCharOffsetTo().map(offset -> offset + 1))
                                        + " of " + format(display.getTotalChars()));
            final long charFrom = display.getCharOffsetFrom().get();
            final long charTo = display.getCharOffsetTo().get();
            showHeadCharactersBtn.setEnabled(charFrom > 0);
            advanceCharactersBackwardBtn.setEnabled(charFrom > 0);
            advanceCharactersForwardBtn.setEnabled(charTo < display.getTotalChars()
                    .asOptional()
                    .map(total -> total - 1)
                    .orElse(Long.MAX_VALUE));
            refresh.setEnabled(true);
        } else if (NavigationMode.BYTES.equals(display.getNavigationMode())
                   && display.getByteOffsetFrom().isPresent()
                   && display.getByteOffsetTo().isPresent()) {
            // Hex dump data
            setRangeText(lblLines, "");
            final long byteFrom = display.getByteOffsetFrom().get();
            final long byteTo = display.getByteOffsetTo().get();
            showHeadCharactersBtn.setEnabled(byteFrom > 0);
            advanceCharactersBackwardBtn.setEnabled(byteFrom > 0);
            advanceCharactersForwardBtn.setEnabled(byteTo < display.getTotalBytes()
                    .map(total -> total - 1)
                    .orElse(Long.MAX_VALUE));
            refresh.setEnabled(true);
        } else {
            // No char range, must be an error
            setRangeText(lblLines, LINES_TITLE + " 0 to 0");
            setRangeText(lblCharacters, CHARACTERS_TITLE + " 0 to 0 of " + format(display.getTotalChars()));
            showHeadCharactersBtn.setEnabled(false);
            advanceCharactersBackwardBtn.setEnabled(false);
            advanceCharactersForwardBtn.setEnabled(false);
            refresh.setEnabled(false);
        }
        setControlVisibility(display, true);
    }

    private void setControlVisibility(final HasCharacterData display, final boolean visible) {
        final boolean showCharLabels = display != null
                                       && NavigationMode.CHARS.equals(display.getNavigationMode());
        // A range with no text (no line numbers) isn't shown, rather than being an empty link
        lblLines.setVisible(visible && showCharLabels && !lblLines.getText().isEmpty());
        lblCharacters.setVisible(visible && showCharLabels && !lblCharacters.getText().isEmpty());
        showHeadCharactersBtn.setVisible(visible);
        advanceCharactersBackwardBtn.setVisible(visible);
        advanceCharactersForwardBtn.setVisible(visible);
    }

    private static void setRangeText(final LinkButton rangeButton, final String text) {
        rangeButton.setText(text);
        rangeButton.setAriaLabel(text.isEmpty()
                ? null
                : SET_RANGE + " (" + text + ")");
    }

    // Disables the button, so it can't be pressed again while the range changes; the range shown
    // next enables it again if it applies
    private void act(final ToolbarButton button, final NavigatorAction action) {
        button.setEnabled(false);
        act(action);
    }

    private void act(final NavigatorAction action) {
        if (onAction != null) {
            onAction.accept(action);
        }
    }

    private static String format(final Optional<? extends Number> value) {
        return value
                .map(NUMBER_FORMATTER::format)
                .orElse(UNKNOWN_VALUE);
    }

    private static String format(final Count<Long> value) {
        if (value != null && value.getCount() != null) {
            final String str = NUMBER_FORMATTER.format(value.getCount());
            return value.isExact()
                    ? str
                    : "~" + str;
        }
        return UNKNOWN_VALUE;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, CharacterNavigator> {

    }
}
