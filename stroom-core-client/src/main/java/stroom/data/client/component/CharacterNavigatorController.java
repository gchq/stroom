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

/// Moves the source viewer through what it shows ([HasCharacterData]) with its
/// [CharacterNavigator], and opens the "Set Source Range" dialog when the user asks to set the range.
public class CharacterNavigatorController extends Controller {

    private final CharacterNavigator view;
    private final Provider<CharacterRangeSelectionController> characterRangeSelectionProvider;
    private HasCharacterData display;

    /// Made by GIN, with its navigator and what makes its range dialog.
    ///
    /// @param view                            Its component.
    /// @param characterRangeSelectionProvider Makes the character range selection.
    @Inject
    public CharacterNavigatorController(
            final CharacterNavigator view,
            final Provider<CharacterRangeSelectionController> characterRangeSelectionProvider) {
        this.view = view;
        this.characterRangeSelectionProvider = characterRangeSelectionProvider;
        initWidget(view);
        view.setOnAction(this::onAction);
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
