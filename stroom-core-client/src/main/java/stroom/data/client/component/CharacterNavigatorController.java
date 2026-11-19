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

import stroom.util.shared.HasCharacterData;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Objects;

/// Moves the source viewer through what it shows ([HasCharacterData]) with its
/// [CharacterNavigator], and opens the "Set Source Range" dialog when the user asks to set the range.
public class CharacterNavigatorController extends Controller {

    private final Provider<CharacterRangeSelectionController> characterRangeSelectionProvider;
    private CharacterNavigator view;
    private HasCharacterData display;

    /// Made by GIN, with what makes its range dialog; [#attach(CharacterNavigator)] gives it its
    /// navigator.
    ///
    /// @param characterRangeSelectionProvider Makes the character range selection.
    @Inject
    public CharacterNavigatorController(
            final Provider<CharacterRangeSelectionController> characterRangeSelectionProvider) {
        this.characterRangeSelectionProvider = characterRangeSelectionProvider;
    }

    /// Attaches this to a navigator, e.g. one a template declares. The navigator is then this
    /// controller's widget.
    ///
    /// @param view The navigator.
    /// @return This, for its owner to keep.
    public CharacterNavigatorController attach(final CharacterNavigator view) {
        this.view = Objects.requireNonNull(view, "view");
        initWidget(view);
        view.setOnAction(this::onAction);
        return this;
    }

    /// @param display What the navigator moves through, or null for nothing; shown once
    ///                [#refreshNavigator()] is called.
    public void setDisplay(final HasCharacterData display) {
        this.display = display;
    }

    /// Shows the range now shown.
    public void refreshNavigator() {
        view.show(display);
    }

    private void onAction(final NavigatorAction action) {
        if (display == null) {
            return;
        }
        switch (action) {
            case FIRST:
                display.showHeadCharacters();
                break;
            case PREVIOUS:
                display.advanceCharactersBackwards();
                break;
            case NEXT:
                display.advanceCharactersForward();
                break;
            case REFRESH:
                display.refresh();
                break;
            case SELECT:
                final HasCharacterData source = display;
                characterRangeSelectionProvider.get().edit(this,
                        source.getDataRange(),
                        source.getTotalChars(),
                        source::setDataRange);
                break;
            default:
                break;
        }
    }
}
