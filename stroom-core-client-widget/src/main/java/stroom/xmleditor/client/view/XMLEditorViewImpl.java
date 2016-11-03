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

package stroom.xmleditor.client.view;

import java.util.List;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseDownHandler;
import com.google.gwt.event.dom.client.ScrollEvent;
import com.google.gwt.event.dom.client.ScrollHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.DockLayoutPanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import stroom.util.shared.Highlight;
import stroom.util.shared.Indicators;
import stroom.widget.contextmenu.client.event.ContextMenuEvent;
import stroom.xmleditor.client.event.FormatEvent;
import stroom.xmleditor.client.event.FormatEvent.FormatHandler;
import stroom.xmleditor.client.model.HTMLUtil;
import stroom.xmleditor.client.model.XMLStyler;
import stroom.xmleditor.client.presenter.Option;
import stroom.xmleditor.client.presenter.XMLEditorUiHandlers;
import stroom.xmleditor.client.presenter.XMLEditorView;

/**
 * This is a widget that can be used to edit XML. It provides useful
 * functionality such as formating, styling, line numbers and warning/error
 * markers.
 */
public class XMLEditorViewImpl extends ViewWithUiHandlers<XMLEditorUiHandlers>implements XMLEditorView {
    /**
     * Declare styles.
     */
    public interface Style extends CssResource {
        String filterButtons();

        String filterButton();
    }

    /**
     * Bundle for the indicator icons and styles.
     */
    public interface Resources extends ClientBundle {
        ImageResource warning();

        ImageResource error();

        ImageResource filterActive();

        ImageResource filterInactive();

        @Source("xmleditor.css")
        Style style();
    }

    public interface Binder extends UiBinder<DockLayoutPanel, XMLEditorViewImpl> {
    }

    private class InnerOption extends Option {
        public InnerOption(final String text, final boolean on, final boolean available) {
            super(text, on, available);
        }

        @Override
        protected void refresh() {
            XMLEditorViewImpl.this.refresh(false);
        }
    }

    private static volatile Binder binder;
    private static volatile Resources resources;
    private static final String NEW_LINE = "<br/>";

    private static final IndicatorPopup indicatorPopup = new IndicatorPopup();
    private final XMLStyler styler = new XMLStyler();

    private int startLineNo = 1;
    private List<Highlight> highlights;

    DockLayoutPanel widget;
    private final Option stylesOption = new InnerOption("Styles", true, true);
    private final Option lineNumbersOption = new InnerOption("Line Numbers", false, true);
    private final Option indicatorsOption = new InnerOption("Indicators", false, false);
    private Indicators indicators;
    private boolean controlsVisible;
    private ScheduledCommand layoutCommand;
    private boolean initialized;
    private Element body;

    @UiField(provided = true)
    DockLayoutPanel layout;
    @UiField(provided = true)
    XMLArea xmlArea;
    @UiField
    LeftBar leftBar;
    @UiField
    RightBar rightBar;
    @UiField
    FlowPanel filterButtons;
    @UiField
    Image filterInactive;
    @UiField
    Image filterActive;

    public XMLEditorViewImpl(final boolean readOnly) {
        if (binder == null) {
            synchronized (LeftBar.class) {
                if (binder == null) {
                    binder = GWT.create(Binder.class);
                    resources = GWT.create(Resources.class);
                    resources.style().ensureInjected();
                }
            }
        }

        layout = new DockLayoutPanel(Unit.PX) {
            @Override
            public void onResize() {
                super.onResize();
                doLayout();
            }
        };

        if (readOnly) {
            xmlArea = new XMLAreaReadOnlyImpl();
        } else {
            xmlArea = new XMLAreaEditableImpl();
        }
        afterInit(xmlArea.getBody());

        widget = binder.createAndBindUi(this);

        xmlArea.addMouseDownHandler(new MouseDownHandler() {
            @Override
            public void onMouseDown(final MouseDownEvent event) {
                handleMouseDown(event);
            }
        });
        xmlArea.addContextMenuHandler(new ContextMenuEvent.Handler() {
            @Override
            public void onContextMenu(final ContextMenuEvent event) {
                handleContextMenu(event);
            }
        });
        xmlArea.addScrollHandler(new ScrollHandler() {
            @Override
            public void onScroll(final ScrollEvent event) {
                doLayout();
            }
        });
        filterButtons.addDomHandler(new ClickHandler() {
            @Override
            public void onClick(final ClickEvent event) {
                if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                    if (getUiHandlers() != null) {
                        getUiHandlers().changeFilterSettings();
                    }
                }
            }
        }, ClickEvent.getType());

        filterButtons.setWidth(ScrollbarMetrics.getVerticalScrollBarWidth() + "px");
        filterButtons.setHeight(ScrollbarMetrics.getHorizontalScrollBarWidth() + "px");

        final int left = ((ScrollbarMetrics.getVerticalScrollBarWidth() - 10) / 2);
        final int top = ((ScrollbarMetrics.getHorizontalScrollBarWidth() - 10) / 2);
        filterInactive.getElement().getStyle().setLeft(left, Unit.PX);
        filterActive.getElement().getStyle().setLeft(left, Unit.PX);
        filterInactive.getElement().getStyle().setTop(top, Unit.PX);
        filterActive.getElement().getStyle().setTop(top, Unit.PX);

        rightBar.setXmlArea(xmlArea);
    }

    protected void afterInit(final Element body) {
        this.body = body;
        initialized = true;
        doLayout();
    }

    private void handleMouseDown(final MouseDownEvent event) {
        indicatorPopup.hide();
        MouseDownEvent.fireNativeEvent(event.getNativeEvent(), this);
    }

    private void handleContextMenu(final ContextMenuEvent event) {
        ContextMenuEvent.fire(this, event.getX(), event.getY());
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    private void doLayout() {
        if (initialized && layoutCommand == null) {
            layoutCommand = new ScheduledCommand() {
                @Override
                public void execute() {
                    layoutCommand = null;
                    leftBar.render(startLineNo, body.getScrollTop(), body.getScrollHeight(), lineNumbersOption,
                            indicatorsOption, indicators);
                    widget.setWidgetSize(leftBar, leftBar.getWidth());

                    rightBar.render(startLineNo, body.getScrollTop(), body.getScrollHeight(), lineNumbersOption,
                            indicatorsOption, indicators);
                    widget.setWidgetSize(rightBar, rightBar.getWidth());
                }
            };
            Scheduler.get().scheduleDeferred(layoutCommand);
        }
    }

    /**
     * Turns all HTML present in this control into XML and returns it.
     *
     * @return The XML present in this control.
     */
    @Override
    public String getText() {
        final String html = xmlArea.getHTML();

        // Turn HTML into text.
        final String text = HTMLUtil.htmlToText(html);
        return text;
    }

    /**
     * Sets the text for this control. If XML is supplied it will be turned into
     * HTML for styling.
     *
     * @param text
     *            The text to use.
     */
    @Override
    public void setText(final String text) {
        setText(text, 1, false, null, null, false);
    }

    /**
     * Sets the text of this control. If XML is supplied it will be turned into
     * HTML for styling.
     *
     * @param format
     *            Determines if the content is formatted (pretty printed). If
     *            this is required this parameter should be set to true.
     */
    @Override
    public void setText(final String content, final int startLineNo, final boolean format,
            final List<Highlight> highlights, final Indicators indicators, final boolean controlsVisible) {
        this.startLineNo = startLineNo;
        this.highlights = highlights;
        this.indicators = indicators;
        this.controlsVisible = controlsVisible;

        if (content == null || content.trim().length() == 0) {
            xmlArea.setHTML("");

        } else {
            // Get the content html.
            String html = styler.processXML(content, stylesOption.isOk(), format, startLineNo, highlights);
            // We need new line characters to be replaced with breaks.
            html = html.replaceAll("\r\n", NEW_LINE);
            html = html.replaceAll("\n", NEW_LINE);

            xmlArea.setHTML(html);
            if (highlights != null && highlights.size() > 0) {
                xmlArea.scrollHighlightIntoView();
            }

            // Add a margin to the bottom of the content if we are going to show
            // a play button so that all of the content can be seen when
            // scrolled down.
            xmlArea.setControlsVisible(controlsVisible);
        }

        doLayout();
    }

    @Override
    public void setHTML(final String html, final List<Highlight> highlights, final boolean controlsVisible) {
        this.startLineNo = 1;
        this.highlights = highlights;
        this.indicators = null;
        this.controlsVisible = controlsVisible;

        if (html == null || html.trim().length() == 0) {
            xmlArea.setHTML("");

        } else {
            xmlArea.setHTML(html);
            if (highlights != null && highlights.size() > 0) {
                xmlArea.scrollHighlightIntoView();
            }

            // Add a margin to the bottom of the content if we are going to show
            // a play button so that all of the content can be seen when
            // scrolled down.
            xmlArea.setControlsVisible(controlsVisible);
        }

        doLayout();
    }

    /**
     * Formats the currently displayed XML.
     */
    @Override
    public void formatXML() {
        refresh(true);
        FormatEvent.fire(this);
    }

    /**
     * Call to re-apply all current settings to the content. This is necessary
     * if new content is written or pasted into the widget.
     *
     * @param format
     *            True if the content should be formatted while applying the
     *            current settings.
     */
    @Override
    public void refresh(final boolean format) {
        setText(getText(), startLineNo, format, highlights, indicators, controlsVisible);
    }

    @Override
    public Option getStylesOption() {
        return stylesOption;
    }

    @Override
    public Option getLineNumbersOption() {
        return lineNumbersOption;
    }

    @Override
    public Option getIndicatorsOption() {
        return indicatorsOption;
    }

    @Override
    public Indicators getIndicators() {
        return indicators;
    }

    @Override
    public void setIndicators(final Indicators indicators) {
        this.indicators = indicators;
        refresh(false);
    }

    @Override
    public void showFilterButton(final boolean show) {
        filterActive.setVisible(false);
        filterButtons.setVisible(show);
    }

    @Override
    public void setFilterActive(final boolean active) {
        filterActive.setVisible(active);
    }

    @Override
    public void setControlsHeight(final int controlsHeight) {
        xmlArea.setControlsHeight(controlsHeight);
    }

    @Override
    public HandlerRegistration addKeyDownHandler(final KeyDownHandler handler) {
        return xmlArea.addKeyDownHandler(handler);
    }

    @Override
    public HandlerRegistration addFormatHandler(final FormatHandler handler) {
        return widget.addHandler(handler, FormatEvent.TYPE);
    }

    @Override
    public HandlerRegistration addMouseDownHandler(final MouseDownHandler handler) {
        return widget.addHandler(handler, MouseDownEvent.getType());
    }

    @Override
    public HandlerRegistration addContextMenuHandler(final ContextMenuEvent.Handler handler) {
        return widget.addHandler(handler, ContextMenuEvent.getType());
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        widget.fireEvent(event);
    }
}
