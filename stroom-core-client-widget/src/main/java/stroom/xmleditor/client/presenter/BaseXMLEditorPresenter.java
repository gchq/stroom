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

package stroom.xmleditor.client.presenter;

import java.util.List;

import com.google.gwt.event.dom.client.HasKeyDownHandlers;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseDownHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.HasText;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import stroom.util.shared.Highlight;
import stroom.util.shared.Indicators;
import stroom.widget.contextmenu.client.event.ContextMenuEvent;
import stroom.xmleditor.client.event.ChangeFilterEvent;
import stroom.xmleditor.client.event.ChangeFilterEvent.ChangeFilterHandler;
import stroom.xmleditor.client.event.FormatEvent.FormatHandler;
import stroom.xmleditor.client.event.HasChangeFilterHandlers;
import stroom.xmleditor.client.event.HasFormatHandlers;
import stroom.xmleditor.client.view.XMLEditorMenuPresenter;

public abstract class BaseXMLEditorPresenter extends MyPresenterWidget<XMLEditorView>
        implements HasKeyDownHandlers, HasFormatHandlers, HasChangeFilterHandlers, HasText, XMLEditorUiHandlers {
    private boolean showFilterSettings;
    private boolean input;

    @Inject
    public BaseXMLEditorPresenter(final EventBus eventBus, final XMLEditorView view,
            final XMLEditorMenuPresenter contextMenu) {
        super(eventBus, view);
        view.setUiHandlers(this);

        registerHandler(view.addMouseDownHandler(new MouseDownHandler() {
            @Override
            public void onMouseDown(final MouseDownEvent event) {
                contextMenu.hide();
            }
        }));
        registerHandler(view.addContextMenuHandler(new ContextMenuEvent.Handler() {
            @Override
            public void onContextMenu(final ContextMenuEvent event) {
                contextMenu.show(BaseXMLEditorPresenter.this, event.getX(), event.getY());
            }
        }));
        registerHandler(view.addKeyDownHandler(new KeyDownHandler() {
            @Override
            public void onKeyDown(final KeyDownEvent event) {
                if (event.isAltKeyDown() || event.isControlKeyDown()) {
                    eventBus.fireEvent(event);
                }
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
     * Sets the text for this control. If XML is supplied it will be turned into
     * HTML for styling.
     */
    public void setText(final String text, final boolean format) {
        getView().setText(text, 1, format, null, null, false);
    }

    /**
     * Sets the text for this control. If XML is supplied it will be turned into
     * HTML for styling.
     */
    public void setText(final String text, final int startLineNo) {
        getView().setText(text, startLineNo, false, null, null, false);
    }

    /**
     * Sets the text of this control. If XML is supplied it will be turned into
     * HTML for styling.
     *
     * @param format
     *            Determines if the content is formatted (pretty printed). If
     *            this is required this parameter should be set to true.
     */
    public void setText(final String content, final int startLineNo, final boolean format) {
        getView().setText(content, startLineNo, format, null, null, false);
    }

    /**
     * Sets the text of this control. If XML is supplied it will be turned into
     * HTML for styling.
     *
     * @param format
     *            Determines if the content is formatted (pretty printed). If
     *            this is required this parameter should be set to true.
     */
    public void setText(final String content, final int startLineNo, final boolean format,
            final List<Highlight> highlights, final Indicators indicators, final boolean controlsVisible) {
        getView().setText(content, startLineNo, format, highlights, indicators, controlsVisible);
    }

    public void setHTML(final String html, final List<Highlight> highlights, final boolean controlsVisible) {
        getView().setHTML(html, highlights, controlsVisible);
    }

    /**
     * Call to re-apply all current settings to the content. This is necessary
     * if new content is written or pasted into the widget.
     *
     * @param format
     *            True if the content should be formatted while applying the
     *            current settings.
     */
    public void refresh(final boolean format) {
        getView().refresh(format);
    }

    /**
     * Formats the currently displayed XML.
     */
    public void formatXML() {
        getView().formatXML();
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

    public Indicators getIndicators() {
        return getView().getIndicators();
    }

    public void setIndicators(final Indicators indicators) {
        getView().setIndicators(indicators);
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

    public void setControlsHeight(final int controlsHeight) {
        getView().setControlsHeight(controlsHeight);
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
