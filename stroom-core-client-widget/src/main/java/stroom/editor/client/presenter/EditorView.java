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

package stroom.editor.client.presenter;

import stroom.editor.client.event.HasFormatHandlers;
import stroom.editor.client.view.IndicatorLines;
import stroom.editor.client.view.Marker;
import stroom.util.shared.TextRange;
import stroom.util.shared.diff.LineReplacement;
import stroom.widget.contextmenu.client.event.HasContextMenuHandlers;

import com.google.gwt.event.dom.client.HasMouseDownHandlers;

import java.util.List;
import java.util.function.Function;

public interface EditorView
        extends BaseEditorView, HasFormatHandlers, HasContextMenuHandlers, HasMouseDownHandlers {

    void setFirstLineNumber(int firstLineNumber);

    void setIndicators(IndicatorLines indicators);

    void setMarkers(List<Marker> markers);

    void setHighlights(List<TextRange> highlights);

    public void setErrorText(String title, String errorText);

    /**
     * If the text is being formatted by this view then you can provide a function to generate
     * highlights on the formatted text as the line/col positions in the formatted text may
     * differ to those in the original input. Should be called before setText is called.
     *
     * @param highlightsFunction A function to return a list of highlight ranges from the formatted text.
     */
    void setFormattedHighlights(final Function<String, List<TextRange>> highlightsFunction);

    void setText(final String text, final boolean format);

    Action getFormatAction();

    Option getStylesOption();

    Option getLineNumbersOption();

    Option getIndicatorsOption();

    Option getLineWrapOption();

    Option getShowIndentGuides();

    Option getShowInvisiblesOption();

    Option getUseVimBindingsOption();

    Option getBasicAutoCompletionOption();

    Option getSnippetsOption();

    Option getLiveAutoCompletionOption();

    Option getHighlightActiveLineOption();

    Option getViewAsHexOption();

    void setControlsVisible(boolean visible);

    void setOptionsToDefaultAvailability();

    /// Replaces ranges of whole lines while keeping the undo history and the user's cursor and
    /// selection. See [stroom.editor.client.view.Editor#applyLineReplacements(List)].
    ///
    /// @param replacements The line replacements, with line numbers relative to the current text.
    /// @return False if the editor has not started yet, so nothing was changed.
    boolean applyLineReplacements(List<LineReplacement> replacements);

    /// @param listener Called whenever the vertical scroll position changes.
    void addScrollListener(Runnable listener);

    /// @return The vertical scroll position in pixels, or -1 if the editor isn't attached.
    int getScrollTop();

    /// @param scrollTop The vertical scroll position in pixels.
    void setScrollTop(int scrollTop);

    /// @param labels The gutter label for each row in place of line numbers, or null for line numbers.
    void setLineNumberLabels(List<String> labels);

    /// Moves the cursor to a line, scrolling it into view.
    ///
    /// @param lineNo The one based line number.
    void gotoLine(int lineNo);
}
