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

import com.google.gwt.event.dom.client.HasKeyDownHandlers;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.HasText;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorTheme;
import stroom.util.shared.Highlight;
import stroom.util.shared.Indicators;
import stroom.editor.client.event.ChangeFilterEvent;
import stroom.editor.client.event.ChangeFilterEvent.ChangeFilterHandler;
import stroom.editor.client.event.FormatEvent.FormatHandler;
import stroom.editor.client.event.HasChangeFilterHandlers;
import stroom.editor.client.event.HasFormatHandlers;
import stroom.editor.client.view.EditorMenuPresenter;

import java.util.List;

public class EditorPresenter extends MyPresenterWidget<EditorView>
        implements HasKeyDownHandlers, HasFormatHandlers, HasChangeFilterHandlers, HasText, EditorUiHandlers {
    private final EditorMenuPresenter contextMenu;

    private boolean showFilterSettings;
    private boolean input;

    @Inject
    public EditorPresenter(final EventBus eventBus, final EditorView view,
                           final EditorMenuPresenter contextMenu) {
        super(eventBus, view);
        this.contextMenu = contextMenu;
        view.setUiHandlers(this);

        registerHandler(view.addMouseDownHandler(event -> contextMenu.hide()));
        registerHandler(view.addContextMenuHandler(event -> contextMenu.show(EditorPresenter.this, event.getX(), event.getY())));
        registerHandler(view.addKeyDownHandler(event -> {
            if (event.isAltKeyDown() || event.isControlKeyDown()) {
                eventBus.fireEvent(event);
            }
        }));
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
        getView().setText(text);
    }

    /**
     * Formats the currently displayed XML.
     */
    public void format() {
        getView().format();
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

    public void setFirstLineNumber(final int firstLineNumber) {
        getView().setFirstLineNumber(firstLineNumber);
    }

    public void setIndicators(final Indicators indicators) {
        getView().setIndicators(indicators);
    }

    public void setHighlights(final List<Highlight> highlights) {
        getView().setHighlights(highlights);
    }

    @Override
    public void changeFilterSettings() {
        ChangeFilterEvent.fire(this);
    }

    public void setShowFilterSettings(final boolean showFilterSettings) {
        this.showFilterSettings = showFilterSettings;
        getView().showFilterButton(showFilterSettings);
    }

    public void setFilterActive(final boolean active) {
        getView().setFilterActive(active);
    }

    public void setControlsVisible(final boolean visible) {
        getView().setControlsVisible(visible);
    }

    public void setReadOnly(final boolean readOnly) {
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

    public void setInput(final boolean input) {
        this.input = input;
    }

    public boolean isInput() {
        return input;
    }

    public EditorMenuPresenter getContextMenu() {
        return contextMenu;
    }

    @Override
    public HandlerRegistration addKeyDownHandler(final KeyDownHandler handler) {
        return getView().addKeyDownHandler(handler);
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
}
