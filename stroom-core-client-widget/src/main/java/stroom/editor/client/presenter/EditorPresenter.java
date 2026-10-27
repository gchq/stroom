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

import stroom.editor.client.component.CodeEditor;
import stroom.editor.client.component.CodeEditorController;
import stroom.editor.client.event.FormatEvent.FormatHandler;
import stroom.editor.client.event.HasFormatHandlers;
import stroom.editor.client.view.IndicatorLines;
import stroom.editor.client.view.Marker;
import stroom.ui.config.shared.AceEditorTheme;
import stroom.util.shared.TextRange;
import stroom.widget.presenter.client.MyPresenterWidget;

import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.HasText;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionProvider;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.List;
import java.util.function.Function;

/// A code editor as a presenter, for the screens that still take presenters: a
/// [CodeEditorController], which it owns, showing its [CodeEditor]. A screen split into a component
/// and a controller makes a [CodeEditorController] itself.
public class EditorPresenter
        extends MyPresenterWidget<CodeEditor>
        implements HasText, HasValueChangeHandlers<String>, HasFormatHandlers {

    private final CodeEditorController controller;

    /// Made by GIN, with the editor it shows and the controller it attaches to it.
    ///
    /// @param eventBus   The application's event bus.
    /// @param view       The editor.
    /// @param controller The editor's controller, which this owns.
    @Inject
    public EditorPresenter(final EventBus eventBus,
                           final CodeEditor view,
                           final CodeEditorController controller) {
        super(eventBus, view);
        this.controller = own(controller).attach(view);
    }

    /// @return The editor's controller, for a part that links to it, e.g. the query help.
    public CodeEditorController getController() {
        return controller;
    }

    /// @see CodeEditorController#getEditorId()
    public String getEditorId() {
        return controller.getEditorId();
    }

    /// @see CodeEditorController#focus()
    public void focus() {
        controller.focus();
    }

    @Override
    public String getText() {
        return controller.getText();
    }

    /// Sets the text for this control. If XML is supplied it will be turned into HTML for styling.
    @Override
    public void setText(final String text) {
        controller.setText(text);
    }

    /// @see CodeEditorController#setText(String, boolean)
    public void setText(final String text, final boolean format) {
        controller.setText(text, format);
    }

    /// Replaces the editor with some html showing the errorText and its title
    public void setErrorText(final String title, final String errorText) {
        controller.setErrorText(title, errorText);
    }

    /// @see CodeEditorController#isClean()
    public boolean isClean() {
        return controller.isClean();
    }

    /// @see CodeEditorController#markClean()
    public void markClean() {
        controller.markClean();
    }

    /// @see CodeEditorController#insertTextAtCursor(String)
    public void insertTextAtCursor(final String text) {
        controller.insertTextAtCursor(text);
    }

    /// @see CodeEditorController#replaceSelectedText(String)
    public void replaceSelectedText(final String text) {
        controller.replaceSelectedText(text);
    }

    /// @see CodeEditorController#insertSnippet(String)
    public void insertSnippet(final String snippet) {
        controller.insertSnippet(snippet);
    }

    /// @see CodeEditorController#setMode(AceEditorMode)
    public void setMode(final AceEditorMode mode) {
        controller.setMode(mode);
    }

    /// @see CodeEditorController#setTheme(AceEditorTheme)
    public void setTheme(final AceEditorTheme theme) {
        controller.setTheme(theme);
    }

    @Override
    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<String> handler) {
        return controller.addValueChangeHandler(handler);
    }

    /// @see CodeEditorController#registerCompletionProviders(AceEditorMode, AceCompletionProvider...)
    public void registerCompletionProviders(final AceEditorMode aceEditorMode,
                                            final AceCompletionProvider... completionProviders) {
        controller.registerCompletionProviders(aceEditorMode, completionProviders);
    }

    /// @see CodeEditorController#registerCompletionProviders(AceCompletionProvider...)
    public void registerCompletionProviders(final AceCompletionProvider... completionProviders) {
        controller.registerCompletionProviders(completionProviders);
    }

    /// @see CodeEditorController#deRegisterCompletionProviders()
    public void deRegisterCompletionProviders() {
        controller.deRegisterCompletionProviders();
    }

    /// @see CodeEditorController#getFormatAction()
    public Action getFormatAction() {
        return controller.getFormatAction();
    }

    public Option getStylesOption() {
        return controller.getOptions().getStylesOption();
    }

    public Option getLineNumbersOption() {
        return controller.getOptions().getLineNumbersOption();
    }

    public Option getIndicatorsOption() {
        return controller.getOptions().getIndicatorsOption();
    }

    public Option getLineWrapOption() {
        return controller.getOptions().getLineWrapOption();
    }

    public Option getShowIndentGuides() {
        return controller.getOptions().getShowIndentGuides();
    }

    public Option getShowInvisiblesOption() {
        return controller.getOptions().getShowInvisiblesOption();
    }

    public Option getUseVimBindingsOption() {
        return controller.getOptions().getUseVimBindingsOption();
    }

    public Option getBasicAutoCompletionOption() {
        return controller.getOptions().getBasicAutoCompletionOption();
    }

    public Option getSnippetsOption() {
        return controller.getOptions().getSnippetsOption();
    }

    public Option getLiveAutoCompletionOption() {
        return controller.getOptions().getLiveAutoCompletionOption();
    }

    public Option getHighlightActiveLineOption() {
        return controller.getOptions().getHighlightActiveLineOption();
    }

    public Option getViewAsHexOption() {
        return controller.getOptions().getViewAsHexOption();
    }

    /// @see CodeEditorController#setFirstLineNumber(int)
    public void setFirstLineNumber(final int firstLineNumber) {
        controller.setFirstLineNumber(firstLineNumber);
    }

    /// @see CodeEditorController#setIndicators(IndicatorLines)
    public void setIndicators(final IndicatorLines indicators) {
        controller.setIndicators(indicators);
    }

    /// @see CodeEditorController#setMarkers(List)
    public void setMarkers(final List<Marker> markers) {
        controller.setMarkers(markers);
    }

    /// @see CodeEditorController#setHighlights(List)
    public void setHighlights(final List<TextRange> highlights) {
        controller.setHighlights(highlights);
    }

    /// @see CodeEditorController#setFormattedHighlights(Function)
    public void setFormattedHighlights(final Function<String, List<TextRange>> highlightsFunction) {
        controller.setFormattedHighlights(highlightsFunction);
    }

    /// @see CodeEditorController#setControlsVisible(boolean)
    public void setControlsVisible(final boolean visible) {
        controller.setControlsVisible(visible);
    }

    /// @see CodeEditorController#setOptionsToDefaultAvailability()
    public void setOptionsToDefaultAvailability() {
        controller.setOptionsToDefaultAvailability();
    }

    @Override
    public HandlerRegistration addFormatHandler(final FormatHandler handler) {
        return controller.addFormatListener(handler::onFormat);
    }

    /// @see CodeEditorController#setReadOnly(boolean)
    public void setReadOnly(final boolean readOnly) {
        controller.setReadOnly(readOnly);
    }
}
