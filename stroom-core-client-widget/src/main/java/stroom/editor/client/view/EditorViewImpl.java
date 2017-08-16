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

package stroom.editor.client.view;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseDownHandler;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
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
import edu.ycp.cs.dh.acegwt.client.ace.AceAnnotationType;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorCursorPosition;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorTheme;
import edu.ycp.cs.dh.acegwt.client.ace.AceMarkerType;
import edu.ycp.cs.dh.acegwt.client.ace.AceRange;
import stroom.editor.client.event.FormatEvent;
import stroom.editor.client.event.FormatEvent.FormatHandler;
import stroom.editor.client.model.XmlFormatter;
import stroom.editor.client.presenter.EditorUiHandlers;
import stroom.editor.client.presenter.EditorView;
import stroom.editor.client.presenter.Option;
import stroom.util.shared.Highlight;
import stroom.util.shared.Indicator;
import stroom.util.shared.Indicators;
import stroom.util.shared.Location;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;
import stroom.widget.contextmenu.client.event.ContextMenuEvent;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

/**
 * This is a widget that can be used to edit text. It provides useful
 * functionality such as formatting, styling, line numbers and warning/error
 * markers.
 */
public class EditorViewImpl extends ViewWithUiHandlers<EditorUiHandlers> implements EditorView {
    private static final IndicatorPopup indicatorPopup = new IndicatorPopup();
    private static volatile Binder binder;
    private static volatile Resources resources;
    private final Option stylesOption;
    private final Option lineNumbersOption;
    private final Option indicatorsOption;
    private final Option lineWrapOption;
    @UiField(provided = true)
    DockLayoutPanel layout;
    @UiField
    Editor editor;
    @UiField
    RightBar rightBar;
    @UiField
    FlowPanel filterButtons;
    @UiField
    Image filterInactive;
    @UiField
    Image filterActive;
    private Indicators indicators;
    private AceEditorMode mode = AceEditorMode.XML;
    @Inject
    public EditorViewImpl() {
        if (binder == null) {
            binder = GWT.create(Binder.class);
            resources = GWT.create(Resources.class);
            resources.style().ensureInjected();
        }

        layout = new DockLayoutPanel(Unit.PX) {
            @Override
            public void onResize() {
                super.onResize();
                doLayout();
            }
        };

        layout = binder.createAndBindUi(this);

        filterButtons.addDomHandler(event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                if (getUiHandlers() != null) {
                    getUiHandlers().changeFilterSettings();
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

        stylesOption = new Option("Styles", true, true, (on) -> setMode(mode));
        lineNumbersOption = new Option("Line Numbers", true, true, (on) -> updateGutter());
        indicatorsOption = new Option("Indicators", false, false, (on) -> doLayout());
        lineWrapOption = new Option("Wrap Lines", false, false, (on) -> editor.setUseWrapMode(on));

        editor.getElement().setClassName("editor");
        editor.addDomHandler(event -> handleMouseDown(event), MouseDownEvent.getType());
        rightBar.setEditor(editor);
    }

    private void handleMouseDown(final MouseDownEvent event) {
        if ((NativeEvent.BUTTON_RIGHT & event.getNativeButton()) != 0) {
            ContextMenuEvent.fire(this, event.getClientX(), event.getClientY());
        } else {
            indicatorPopup.hide();
            MouseDownEvent.fireNativeEvent(event.getNativeEvent(), this);
        }
    }

    @Override
    public Widget asWidget() {
        return layout;
    }

    private void updateGutter() {
        editor.setShowGutter(lineNumbersOption.isOn());
    }

    private void doLayout() {
        rightBar.render(indicators, indicatorsOption.isOn());
        layout.setWidgetSize(rightBar, rightBar.getWidth());
        editor.onResize();
    }

    @Override
    public String getText() {
        return editor.getText();
    }

    @Override
    public void setText(final String text) {
        editor.setText(text);
    }

    @Override
    public void setFirstLineNumber(final int firstLineNumber) {
        editor.setFirstLineNumber(firstLineNumber);
    }

    @Override
    public void setIndicators(final Indicators indicators) {
        this.indicators = indicators;
        final List<Annotation> annotations = new ArrayList<>();

        if (indicators != null) {
            for (Integer lineNumber : indicators.getLineNumbers()) {
                final Indicator indicator = indicators.getIndicator(lineNumber);

                for (final Entry<Severity, Set<StoredError>> entry : indicator.getErrorMap().entrySet()) {
                    for (final StoredError error : entry.getValue()) {
                        int row = 0;
                        int col = 0;

                        final Location location = error.getLocation();
                        if (location != null) {
                            row = Math.max(location.getLineNo() - 1, 0);
                            col = location.getColNo();
                        }

                        final Severity severity = error.getSeverity();
                        AceAnnotationType annotationType = AceAnnotationType.INFORMATION;
                        switch (severity) {
                            case INFO:
                                annotationType = AceAnnotationType.INFORMATION;
                                break;
                            case WARNING:
                                annotationType = AceAnnotationType.WARNING;
                                break;
                            case ERROR:
                                annotationType = AceAnnotationType.ERROR;
                                break;
                            case FATAL_ERROR:
                                annotationType = AceAnnotationType.ERROR;
                                break;
                        }

                        annotations.add(new Annotation(row, col, error.getMessage(), annotationType));
                    }
                }
            }
        }

        editor.setAnnotations(annotations);
        doLayout();
    }

    @Override
    public void setHighlights(final List<Highlight> highlights) {
        if (highlights != null && highlights.size() > 0) {
            final List<Marker> markers = new ArrayList<>();
            int minLineNo = Integer.MAX_VALUE;

            for (final Highlight highlight : highlights) {
                minLineNo = Math.min(minLineNo, highlight.getLineFrom());
                final AceRange range = AceRange.create(highlight.getLineFrom() - 1, highlight.getColFrom() - 1, highlight.getLineTo() - 1, highlight.getColTo());
                markers.add(new Marker(range, "hl", AceMarkerType.TEXT, false));
            }

            editor.setMarkers(markers);
            editor.gotoLine(minLineNo);
        } else {
            editor.setMarkers(null);
        }
    }

    @Override
    public void setReadOnly(final boolean readOnly) {
        editor.setReadOnly(readOnly);
    }

    @Override
    public void setMode(final AceEditorMode mode) {
        this.mode = mode;
        if (stylesOption.isOn()) {
            editor.setMode(mode);
        } else {
            editor.setMode(AceEditorMode.TEXT);
        }
    }

    @Override
    public void setTheme(final AceEditorTheme theme) {
        editor.setTheme(theme);
    }

    /**
     * Formats the currently displayed text.
     */
    @Override
    public void format() {
        final int scrollTop = editor.getScrollTop();
        final AceEditorCursorPosition cursorPosition = editor.getCursorPosition();

        if (AceEditorMode.XML.equals(mode)) {
            final String formatted = new XmlFormatter().format(getText());
            setText(formatted);
        } else {
            editor.beautify();
        }

        if (cursorPosition != null) {
            editor.moveTo(cursorPosition.getRow(), cursorPosition.getColumn());
        }
        if (scrollTop > 0) {
            editor.setScrollTop(scrollTop);
        }

        editor.focus();

        FormatEvent.fire(this);
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
    public Option getLineWrapOption() {
        return lineWrapOption;
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
    public void setControlsVisible(final boolean controlsVisible) {
        if (controlsVisible) {
            editor.setScrollMargin(0, 69, 0, 0);
        } else {
            editor.setScrollMargin(0, 0, 0, 0);
        }
    }

    @Override
    public HandlerRegistration addKeyDownHandler(final KeyDownHandler handler) {
        return layout.addDomHandler(handler, KeyDownEvent.getType());
    }

    @Override
    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<String> handler) {
        return editor.addValueChangeHandler(handler);
    }

    @Override
    public HandlerRegistration addFormatHandler(final FormatHandler handler) {
        return layout.addHandler(handler, FormatEvent.TYPE);
    }

    @Override
    public HandlerRegistration addMouseDownHandler(final MouseDownHandler handler) {
        return layout.addHandler(handler, MouseDownEvent.getType());
    }

    @Override
    public HandlerRegistration addContextMenuHandler(final ContextMenuEvent.Handler handler) {
        return layout.addHandler(handler, ContextMenuEvent.getType());
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        layout.fireEvent(event);
    }

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
        ImageResource filterActive();

        ImageResource filterInactive();

        @Source("codeeditor.css")
        Style style();
    }

    public interface Binder extends UiBinder<DockLayoutPanel, EditorViewImpl> {
    }
}
