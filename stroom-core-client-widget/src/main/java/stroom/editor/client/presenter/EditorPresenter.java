/*
 * Copyright 2017 Crown Copyright
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

import stroom.editor.client.event.FormatEvent.FormatHandler;
import stroom.editor.client.event.HasFormatHandlers;
import stroom.editor.client.model.XmlFormatter;
import stroom.editor.client.view.EditorMenuPresenter;
import stroom.editor.client.view.IndicatorLines;
import stroom.editor.client.view.Marker;
import stroom.util.shared.TextRange;
import stroom.util.shared.diff.LineReplacement;
import stroom.widget.util.client.GlobalKeyHandler;

import com.google.gwt.event.shared.HandlerRegistration;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.function.Function;

public class EditorPresenter
        extends AbstractEditorPresenter<EditorView> implements HasFormatHandlers {

    protected static final String VIM_KEY_BINDS_NAME = "VIM";
    private final EditorMenuPresenter contextMenu;

    @Inject
    public EditorPresenter(final EventBus eventBus,
                           final EditorView view,
                           final EditorMenuPresenter contextMenu,
                           final DelegatingAceCompleter delegatingAceCompleter,
                           final CurrentPreferences currentPreferences,
                           final GlobalKeyHandler globalKeyHandler) {
        super(eventBus, view, delegatingAceCompleter, currentPreferences, globalKeyHandler);
        this.contextMenu = contextMenu;

        registerHandler(view.addContextMenuHandler(event ->
                contextMenu.show(
                        EditorPresenter.this,
                        event.getPopupPosition())));
    }

    /**
     * Replaces the editor with some html showing the errorText and its title
     */
    public void setErrorText(final String title, final String errorText) {
        getView().setErrorText(title, errorText);
    }

    public void setText(final String text, final boolean format) {
        if (text == null) {
            getView().setText("");
        } else {
            if (format) {
                final String formatted = new XmlFormatter().format(text);
                getView().setText(formatted, true);
            } else {
                getView().setText(text);
            }
        }
    }

    public Action getFormatAction() {
        return getView().getFormatAction();
    }

    public Option getStylesOption() {
        return getView().getStylesOption();
    }

    public Option getLineNumbersOption() {
        return getView().getLineNumbersOption();
    }

    public Option getIndicatorsOption() {
        return getView().getIndicatorsOption();
    }

    public Option getLineWrapOption() {
        return getView().getLineWrapOption();
    }

    public Option getShowIndentGuides() {
        return getView().getShowIndentGuides();
    }

    public Option getShowInvisiblesOption() {
        return getView().getShowInvisiblesOption();
    }

    public Option getUseVimBindingsOption() {
        return getView().getUseVimBindingsOption();
    }

    public Option getBasicAutoCompletionOption() {
        return getView().getBasicAutoCompletionOption();
    }

    public Option getSnippetsOption() {
        return getView().getSnippetsOption();
    }

    public Option getLiveAutoCompletionOption() {
        return getView().getLiveAutoCompletionOption();
    }

    public Option getHighlightActiveLineOption() {
        return getView().getHighlightActiveLineOption();
    }

    public Option getViewAsHexOption() {
        return getView().getViewAsHexOption();
    }

    public void setFirstLineNumber(final int firstLineNumber) {
        getView().setFirstLineNumber(firstLineNumber);
    }

    public void setIndicators(final IndicatorLines indicators) {
        getView().setIndicators(indicators);
    }

    public void setMarkers(final List<Marker> markers) {
        getView().setMarkers(markers);
    }

    public void setHighlights(final List<TextRange> highlights) {
        getView().setHighlights(highlights);
    }

    public void setFormattedHighlights(final Function<String, List<TextRange>> highlightsFunction) {
        getView().setFormattedHighlights(highlightsFunction);
    }

    public void setControlsVisible(final boolean visible) {
        getView().setControlsVisible(visible);
    }

    public void setOptionsToDefaultAvailability() {
        getView().setOptionsToDefaultAvailability();
    }

    /// Replaces ranges of whole lines in the editor without resetting its undo history, so the user
    /// can undo the change with Ctrl+Z. Each range is selected and replaced, last first; the user's
    /// cursor and selection are then put back, moved to allow for lines added or removed above them.
    ///
    /// Use [stroom.util.shared.diff.LineDiffResult#getLineReplacements()] to get the replacements that
    /// turn the current text into another.
    ///
    /// @param replacements The line replacements, with zero based line numbers relative to the
    ///                     current text.
    /// @return False if the editor has not started yet (it has never been shown), so nothing was
    /// changed.
    /// @throws IllegalArgumentException If a replacement is outside the text or two of them overlap.
    public boolean applyLineReplacements(final List<LineReplacement> replacements) {
        return getView().applyLineReplacements(replacements);
    }

    /// Adds a listener for changes to the vertical scroll position, e.g. to scroll another editor
    /// with this one.
    ///
    /// @param listener Called whenever the vertical scroll position changes.
    public void addScrollListener(final Runnable listener) {
        getView().addScrollListener(listener);
    }

    /// @return The vertical scroll position in pixels, or -1 if the editor isn't attached.
    public int getScrollTop() {
        return getView().getScrollTop();
    }

    /// @param scrollTop The vertical scroll position in pixels.
    public void setScrollTop(final int scrollTop) {
        getView().setScrollTop(scrollTop);
    }

    /// Shows the given labels in the gutter in place of line numbers, one per row.
    ///
    /// @param labels The label for each row, or null to show line numbers again.
    public void setLineNumberLabels(final List<String> labels) {
        getView().setLineNumberLabels(labels);
    }

    /// Moves the cursor to a line, scrolling it into view.
    ///
    /// @param lineNo The one based line number.
    public void gotoLine(final int lineNo) {
        getView().gotoLine(lineNo);
    }

    public EditorMenuPresenter getContextMenu() {
        return contextMenu;
    }

    @Override
    public HandlerRegistration addFormatHandler(final FormatHandler handler) {
        return getView().addFormatHandler(handler);
    }

    @Override
    public void setReadOnly(final boolean readOnly) {
        if (readOnly) {
            getFormatAction().setUnavailable();
            getBasicAutoCompletionOption().setOff();
            getBasicAutoCompletionOption().setUnavailable();
            getSnippetsOption().setOff();
            getSnippetsOption().setUnavailable();
            getLiveAutoCompletionOption().setOff();
            getLiveAutoCompletionOption().setUnavailable();
            getHighlightActiveLineOption().setOff();
        } else {
            getFormatAction().setToDefaultAvailability();
            getBasicAutoCompletionOption().setToDefaultState();
            getBasicAutoCompletionOption().setToDefaultAvailability();
            getSnippetsOption().setToDefaultState();
            getSnippetsOption().setToDefaultAvailability();
            getLiveAutoCompletionOption().setToDefaultState();
            getLiveAutoCompletionOption().setToDefaultAvailability();
            getHighlightActiveLineOption().setToDefaultState();
        }

        getView().setReadOnly(readOnly);
    }
}
