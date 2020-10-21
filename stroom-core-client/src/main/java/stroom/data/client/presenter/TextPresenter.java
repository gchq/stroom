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

package stroom.data.client.presenter;

import stroom.editor.client.event.FormatEvent.FormatHandler;
import stroom.editor.client.presenter.Action;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.editor.client.presenter.Option;
import stroom.editor.client.view.EditorMenuPresenter;
import stroom.editor.client.view.IndicatorLines;
import stroom.util.shared.TextRange;

import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorTheme;

import java.util.List;

public class TextPresenter extends MyPresenterWidget<TextPresenter.TextView> {
    private final EditorPresenter editorPresenter;

    @Inject
    public TextPresenter(final EventBus eventBus,
                         final TextView view,
                         final EditorPresenter editorPresenter) {
        super(eventBus, view);
        this.editorPresenter = editorPresenter;

        editorPresenter.getCodeCompletionOption().setAvailable(false);
        editorPresenter.getIndicatorsOption().setAvailable(false);
        editorPresenter.getIndicatorsOption().setOn(false);

        // Previewing data in formatted form so line numbers are meaningless
        editorPresenter.getLineNumbersOption().setAvailable(false);
        editorPresenter.getLineNumbersOption().setOn(false);

        editorPresenter.getLineWrapOption().setAvailable(true);
        editorPresenter.getLineWrapOption().setOn(true);
        editorPresenter.getShowInvisiblesOption().setAvailable(true);
        editorPresenter.getUseVimBindingsOption().setAvailable(true);

        editorPresenter.setReadOnly(true);

        // Should not be formatting read-only content
        editorPresenter.getFormatAction().setAvailable(false);

        view.setTextView(editorPresenter.getView());
    }

    public void setUiHandlers(final TextUiHandlers uiHandlers) {
        getView().setUiHandlers(uiHandlers);
    }

    public void setText(final String text, final boolean format) {
        editorPresenter.setText(text, format);
    }

    public String getText() {
        return editorPresenter.getText();
    }

    public void setText(final String text) {
        editorPresenter.setText(text);
    }

    public Action getFormatAction() {
        return editorPresenter.getFormatAction();
    }

    public Option getStylesOption() {
        return editorPresenter.getStylesOption();
    }

    public Option getLineNumbersOption() {
        return editorPresenter.getLineNumbersOption();
    }

    public Option getIndicatorsOption() {
        return editorPresenter.getIndicatorsOption();
    }

    public Option getLineWrapOption() {
        return editorPresenter.getLineWrapOption();
    }

    public Option getShowInvisiblesOption() {
        return editorPresenter.getShowInvisiblesOption();
    }

    public Option getUseVimBindingsOption() {
        return editorPresenter.getUseVimBindingsOption();
    }

    public Option getCodeCompletionOption() {
        return editorPresenter.getCodeCompletionOption();
    }

    public void setIndicators(final IndicatorLines indicators) {
        editorPresenter.setIndicators(indicators);
    }

    public void changeFilterSettings() {
        editorPresenter.changeFilterSettings();
    }

    public void setFilterActive(final boolean active) {
        editorPresenter.setFilterActive(active);
    }

    public void setReadOnly(final boolean readOnly) {
        editorPresenter.setReadOnly(readOnly);
    }

    public void setTheme(final AceEditorTheme theme) {
        editorPresenter.setTheme(theme);
    }

    public boolean isShowFilterSettings() {
        return editorPresenter.isShowFilterSettings();
    }

    public void setShowFilterSettings(final boolean showFilterSettings) {
        editorPresenter.setShowFilterSettings(showFilterSettings);
    }

    public boolean isInput() {
        return editorPresenter.isInput();
    }

    public void setInput(final boolean input) {
        editorPresenter.setInput(input);
    }

    public EditorMenuPresenter getContextMenu() {
        return editorPresenter.getContextMenu();
    }

    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<String> handler) {
        return editorPresenter.addValueChangeHandler(handler);
    }

    public HandlerRegistration addFormatHandler(final FormatHandler handler) {
        return editorPresenter.addFormatHandler(handler);
    }

    public void setFirstLineNumber(final int firstLineNumber) {
        editorPresenter.setFirstLineNumber(firstLineNumber);
    }

    public void setHighlights(final List<TextRange> highlights) {
        editorPresenter.setHighlights(highlights);
    }

    public void setControlsVisible(final boolean controlsVisible) {
        getView().setPlayVisible(controlsVisible);
        editorPresenter.setControlsVisible(controlsVisible);
    }

    public void setWrapLines(final boolean isWrapped) {
        editorPresenter.getLineWrapOption().setOn(isWrapped);
    }

    public void setMode(final AceEditorMode mode) {
        editorPresenter.setMode(mode);
    }

    public interface TextView extends View, HasUiHandlers<TextUiHandlers> {
        void setTextView(View view);

        void setPlayVisible(boolean visible);
    }
}
