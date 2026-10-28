/*
 * Copyright 2024 Crown Copyright
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

package stroom.editor.client.component;

import stroom.editor.client.Option;
import stroom.editor.client.ace.Editor;

import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.function.Consumer;

/// A code editor's options, each a setting the user can turn on or off from the editor's menu
/// (e.g. Line Numbers), applied to its [Editor] as they change, and the mode the editor's text is
/// in (e.g. XML), which it is highlighted for while Syntax Highlighting is on.
public class CodeEditorOptions {

    private static final boolean SHOW_INDICATORS_DEFAULT = false;

    private final Editor editor;
    private AceEditorMode mode = AceEditorMode.XML;
    private final Option stylesOption;
    private final Option lineNumbersOption;
    private final Option indicatorsOption;
    private final Option lineWrapOption;
    private final Option showIndentGuides;
    private final Option showInvisiblesOption;
    private final Option basicAutoCompletionOption;
    private final Option snippetsOption;
    private final Option highlightActiveLineOption;
    private final Option viewAsHexOption;
    private Option useVimBindingsOption;
    private Option liveAutoCompletionOption;

    /// Makes the options at their defaults, applying them to the editor.
    ///
    /// @param editor            The editor they apply to.
    /// @param onShowIndicators  Told whether the error and warning indicators are to be shown, as
    ///                          that option changes (and at once).
    public CodeEditorOptions(final Editor editor, final Consumer<Boolean> onShowIndicators) {
        this.editor = editor;
        // Don't forget to add any new options to CodeEditorController's menu
        stylesOption = new Option(
                "Syntax Highlighting", true, true, on -> applyMode(on));
        lineNumbersOption = new Option(
                "Line Numbers", true, true, editor::setShowGutter);
        indicatorsOption = new Option(
                "Error and Warning Indicators", SHOW_INDICATORS_DEFAULT, false, onShowIndicators::accept);
        lineWrapOption = new Option(
                "Wrap Lines", false, true, editor::setUseWrapMode);
        showIndentGuides = new Option(
                "Show Indent Guides", true, true, editor::setShowIndentGuides);
        showInvisiblesOption = new Option(
                "Show Hidden Characters", false, true, editor::setShowInvisibles);
        useVimBindingsOption = buildVimBindingsOption(false);
        basicAutoCompletionOption = new Option(
                "Basic Auto Completion", true, true, editor::setUseBasicAutoCompletion);
        liveAutoCompletionOption = buildLiveAutoCompleteOption(false);
        snippetsOption = new Option(
                "Snippets", true, true, editor::setUseSnippets);
        highlightActiveLineOption = new Option(
                "Highlight Active Line", true, true, editor::setHighlightActiveLine);
        viewAsHexOption = new Option("View as Hex", false, false, null);
    }

    /// @return The mode the editor's text is in.
    public AceEditorMode getMode() {
        return mode;
    }

    /// Sets the mode the editor's text is in, which it is highlighted for while Syntax Highlighting
    /// is on.
    ///
    /// @param mode The mode, e.g. XML.
    public void setMode(final AceEditorMode mode) {
        this.mode = mode;
        applyMode(stylesOption.isOn());
    }

    /// Sets whether the user prefers Vim key bindings, which the option starts as. The option
    /// overrides the preference, so that the user can turn them off for a while.
    ///
    /// @param useVimBindings Whether they do.
    public void setUserKeyBindingsPreference(final boolean useVimBindings) {
        useVimBindingsOption = buildVimBindingsOption(useVimBindings);
        editor.setUseVimBindings(useVimBindingsOption.isOn());
    }

    /// Sets whether the user prefers live auto completion, which the option starts as. The option
    /// overrides the preference.
    ///
    /// @param isOn Whether they do.
    public void setUserLiveAutoCompletePreference(final boolean isOn) {
        liveAutoCompletionOption = buildLiveAutoCompleteOption(isOn);
        editor.setUseLiveAutoCompletion(liveAutoCompletionOption.isOn());
    }

    /// Makes every option available or not, as it is by default, with Highlight Active Line at its
    /// default too.
    public void setToDefaultAvailability() {
        basicAutoCompletionOption.setToDefaultAvailability();
        highlightActiveLineOption.setToDefaultAvailability();
        highlightActiveLineOption.setToDefaultState();
        lineNumbersOption.setToDefaultAvailability();
        lineWrapOption.setToDefaultAvailability();
        liveAutoCompletionOption.setToDefaultAvailability();
        showIndentGuides.setToDefaultAvailability();
        showInvisiblesOption.setToDefaultAvailability();
        snippetsOption.setToDefaultAvailability();
        stylesOption.setToDefaultAvailability();
        useVimBindingsOption.setToDefaultAvailability();
        viewAsHexOption.setToDefaultAvailability();
    }

    /// @return Syntax Highlighting.
    public Option getStylesOption() {
        return stylesOption;
    }

    /// @return Line Numbers.
    public Option getLineNumbersOption() {
        return lineNumbersOption;
    }

    /// @return Error and Warning Indicators.
    public Option getIndicatorsOption() {
        return indicatorsOption;
    }

    /// @return Wrap Lines.
    public Option getLineWrapOption() {
        return lineWrapOption;
    }

    /// @return Show Indent Guides.
    public Option getShowIndentGuides() {
        return showIndentGuides;
    }

    /// @return Show Hidden Characters.
    public Option getShowInvisiblesOption() {
        return showInvisiblesOption;
    }

    /// @return Vim Key Bindings.
    public Option getUseVimBindingsOption() {
        return useVimBindingsOption;
    }

    /// @return Basic Auto Completion.
    public Option getBasicAutoCompletionOption() {
        return basicAutoCompletionOption;
    }

    /// @return Snippets.
    public Option getSnippetsOption() {
        return snippetsOption;
    }

    /// @return Live Auto Completion.
    public Option getLiveAutoCompletionOption() {
        return liveAutoCompletionOption;
    }

    /// @return Highlight Active Line.
    public Option getHighlightActiveLineOption() {
        return highlightActiveLineOption;
    }

    /// @return View as Hex.
    public Option getViewAsHexOption() {
        return viewAsHexOption;
    }

    private void applyMode(final boolean areStylesEnabled) {
        if (areStylesEnabled) {
            editor.setMode(mode);
        } else {
            editor.setMode(AceEditorMode.TEXT);
        }
    }

    private Option buildVimBindingsOption(final boolean useVimBindings) {
        return new Option(
                "Vim Key Bindings",
                useVimBindings,
                true,
                editor::setUseVimBindings);
    }

    private Option buildLiveAutoCompleteOption(final boolean isOn) {
        return new Option(
                "Live Auto Completion",
                isOn,
                true,
                editor::setUseLiveAutoCompletion);
    }
}
