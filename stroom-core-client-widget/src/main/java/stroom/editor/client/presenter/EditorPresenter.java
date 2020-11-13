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

import stroom.editor.client.event.ChangeFilterEvent;
import stroom.editor.client.event.ChangeFilterEvent.ChangeFilterHandler;
import stroom.editor.client.event.FormatEvent.FormatHandler;
import stroom.editor.client.event.HasChangeFilterHandlers;
import stroom.editor.client.event.HasFormatHandlers;
import stroom.editor.client.model.XmlFormatter;
import stroom.editor.client.view.EditorMenuPresenter;
import stroom.editor.client.view.IndicatorLines;
import stroom.util.shared.TextRange;

import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.HasText;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionProvider;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorTheme;

import java.util.List;
import java.util.function.Function;

public class EditorPresenter
        extends MyPresenterWidget<EditorView>
        implements HasFormatHandlers, HasChangeFilterHandlers, HasText, EditorUiHandlers,
        HasValueChangeHandlers<String> {

    private final EditorMenuPresenter contextMenu;
    private final DelegatingAceCompleter delegatingAceCompleter;

    private boolean showFilterSettings;
    private boolean input;

    @Inject
    public EditorPresenter(final EventBus eventBus,
                           final EditorView view,
                           final EditorMenuPresenter contextMenu,
                           final DelegatingAceCompleter delegatingAceCompleter) {
        super(eventBus, view);
        this.contextMenu = contextMenu;
        this.delegatingAceCompleter = delegatingAceCompleter;
        view.setUiHandlers(this);

        registerHandler(view.addMouseDownHandler(event -> contextMenu.hide()));
        registerHandler(view.addContextMenuHandler(event ->
                contextMenu.show(EditorPresenter.this, event.getX(), event.getY())));
        registerHandler(view.addKeyDownHandler(event -> {
            if (event.isAltKeyDown() || event.isControlKeyDown()) {
                eventBus.fireEvent(event);
            }
        }));
    }

    public String getEditorId() {
        return getView().getEditorId();
    }

    public void focus() {
        getView().focus();
    }

    @Override
    public String getText() {
        return getView().getText();
    }

    /**
     * Sets the text for this control. If XML is supplied it will be turned into
     * HTML for styling.
     */
    @Override
    public void setText(final String text) {
        setText(text, false);
    }

    public void insertTextAtCursor(final String text) {
        getView().insertTextAtCursor(text);
    }

    public void replaceSelectedText(final String text) {
        getView().replaceSelectedText(text);
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

    public void setFirstLineNumber(final int firstLineNumber) {
        getView().setFirstLineNumber(firstLineNumber);
    }

    public void setIndicators(final IndicatorLines indicators) {
        getView().setIndicators(indicators);
    }

    public void setHighlights(final List<TextRange> highlights) {
        getView().setHighlights(highlights);
    }

    public void setFormattedHighlights(final Function<String, List<TextRange>> highlightsFunction) {
        getView().setFormattedHighlights(highlightsFunction);
    }

    @Override
    public void changeFilterSettings() {
        ChangeFilterEvent.fire(this);
    }

    public void setFilterActive(final boolean active) {
        getView().setFilterActive(active);
    }

    public void setControlsVisible(final boolean visible) {
        getView().setControlsVisible(visible);
    }

    public void setReadOnly(final boolean readOnly) {
        if (readOnly) {
            getFormatAction().setUnavailable();
            getBasicAutoCompletionOption().setOff();
            getBasicAutoCompletionOption().setUnavailable();
            getSnippetsOption().setOff();
            getSnippetsOption().setUnavailable();
            getLiveAutoCompletionOption().setOff();
            getLiveAutoCompletionOption().setUnavailable();
        } else {
            getFormatAction().setToDefaultAvailability();
            getBasicAutoCompletionOption().setToDefaultState();
            getBasicAutoCompletionOption().setToDefaultAvailability();
            getSnippetsOption().setToDefaultState();
            getSnippetsOption().setToDefaultAvailability();
            getLiveAutoCompletionOption().setToDefaultState();
            getLiveAutoCompletionOption().setToDefaultAvailability();
        }

        getView().setReadOnly(readOnly);
    }

    public void setMode(final AceEditorMode mode) {
        getView().setMode(mode);
    }

    public void setTheme(final AceEditorTheme theme) {
        getView().setTheme(theme);
    }

    public boolean isShowFilterSettings() {
        return showFilterSettings;
    }

    public void setShowFilterSettings(final boolean showFilterSettings) {
        this.showFilterSettings = showFilterSettings;
        getView().showFilterButton(showFilterSettings);
    }

    public boolean isInput() {
        return input;
    }

    public void setInput(final boolean input) {
        this.input = input;
    }

    public EditorMenuPresenter getContextMenu() {
        return contextMenu;
    }

    @Override
    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<String> handler) {
        return getView().addValueChangeHandler(handler);
    }

    @Override
    public HandlerRegistration addFormatHandler(final FormatHandler handler) {
        return getView().addFormatHandler(handler);
    }

    @Override
    public com.google.web.bindery.event.shared.HandlerRegistration addChangeFilterHandler(
            final ChangeFilterHandler handler) {
        return addHandlerToSource(ChangeFilterEvent.TYPE, handler);
    }

    /**
     * Registers completion providers specific to this editor instance and mode
     */
    public void registerCompletionProviders(final AceEditorMode aceEditorMode, final AceCompletionProvider... completionProviders) {
        delegatingAceCompleter.registerCompletionProviders(
                getEditorId(), aceEditorMode, completionProviders);
    }

    /**
     * Registers mode agnostic completion providers specific to this editor instance
     */
    public void registerCompletionProviders(final AceCompletionProvider... completionProviders) {
        delegatingAceCompleter.registerCompletionProviders(
                getEditorId(), completionProviders);
    }

    /**
     * Removes all completion providers specific to this editor instance
     */
    public void deRegisterCompletionProviders() {
        delegatingAceCompleter.deRegisterCompletionProviders(getEditorId());
    }
}
