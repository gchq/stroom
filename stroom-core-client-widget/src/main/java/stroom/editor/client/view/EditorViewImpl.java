/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.editor.client.event.FormatEvent;
import stroom.editor.client.event.FormatEvent.FormatHandler;
import stroom.editor.client.model.XmlFormatter;
import stroom.editor.client.presenter.Action;
import stroom.editor.client.presenter.EditorView;
import stroom.editor.client.presenter.Option;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.Location;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;
import stroom.util.shared.TextRange;
import stroom.widget.contextmenu.client.event.ContextMenuEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import edu.ycp.cs.dh.acegwt.client.ace.AceAnnotationType;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorCursorPosition;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;
import edu.ycp.cs.dh.acegwt.client.ace.AceMarkerType;
import edu.ycp.cs.dh.acegwt.client.ace.AceRange;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Inject;

/**
 * This is a widget that can be used to edit text. It provides useful
 * functionality such as formatting, styling, line numbers and warning/error
 * markers.
 */
public class EditorViewImpl
        extends AbstractEditorViewImpl
        implements EditorView {

    // These modes are all XML and thus can be formatted as XML
    private static final EnumSet<AceEditorMode> XML_MODES = EnumSet.of(
            AceEditorMode.XML,
            AceEditorMode.STROOM_DATA_SPLITTER,
            AceEditorMode.STROOM_FRAGMENT_PARSER,
            AceEditorMode.STROOM_COMBINED_PARSER);

    private static final IndicatorPopup indicatorPopup = new IndicatorPopup();
    private final Action formatAction;
    private final Widget widget;

    @UiField(provided = true)
    SimplePanel content;
    @UiField
    RightBar rightBar;

    private IndicatorLines indicators;
    private int firstLineNumber = 1;
    private Function<String, List<TextRange>> formattedHighlightsFunc;

    @Inject
    public EditorViewImpl(final Binder binder) {
        super();
        content = createResizablePanel();
        widget = binder.createAndBindUi(this);
        initOptions();
        final Editor editor = getEditor();
        content.setWidget(editor.asWidget());

        formatAction = new Action("Format", false, this::format);

        editor.addDomHandler(this::handleMouseDown, MouseDownEvent.getType());
        rightBar.setEditor(editor);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    SimplePanel getContentPanel() {
        return content;
    }

    protected void handleMouseDown(final MouseDownEvent event) {
        if (MouseUtil.isSecondary(event)) {
            final PopupPosition popupPosition = new PopupPosition(event.getClientX(), event.getClientY());
            ContextMenuEvent.fire(this, popupPosition);
        } else {
            EditorViewImpl.indicatorPopup.hide();
            MouseDownEvent.fireNativeEvent(event.getNativeEvent(), this);
        }
    }


    @Override
    protected void doLayout(final boolean showIndicators) {
        super.doLayout(showIndicators);
        rightBar.render(indicators, showIndicators);
    }

    @Override
    public void setText(final String text) {
        content.setWidget(editor.asWidget());
        super.setText(text);
    }

    public void setText(final String text, final boolean format) {
        content.setWidget(editor.asWidget());
        if (text == null) {
            editor.setText("");
        } else {
            if (format) {
                final String formattedText = formatAsIfXml(text);
                editor.setText(formattedText);
                applyFormattedHighlights(formattedText);
            } else {
                editor.setText(text);
            }
        }
    }

    @Override
    public void setFirstLineNumber(final int firstLineNumber) {
        this.firstLineNumber = firstLineNumber;
        editor.setFirstLineNumber(firstLineNumber);
    }

    @Override
    public void setIndicators(final IndicatorLines indicators) {
        this.indicators = indicators;
        final List<Annotation> annotations = new ArrayList<>();

        if (indicators != null) {
            for (final Integer lineNumber : indicators.getLineNumbers()) {
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

//                            GWT.log("row: " + row
//                                    + " col: " + col
//                                    + " severity: " + error.getSeverity()
//                                    + " msg: " + error.getMessage());

                        final Severity severity = error.getSeverity();
                        final AceAnnotationType annotationType;
                        switch (severity) {
                            case INFO:
                                annotationType = AceAnnotationType.INFO;
                                break;
                            case WARNING:
                                annotationType = AceAnnotationType.WARNING;
                                break;
                            case ERROR:
                                annotationType = AceAnnotationType.ERROR;
                                break;
                            case FATAL_ERROR:
                                annotationType = AceAnnotationType.FATAL_ERROR;
                                break;
                            default:
                                throw new RuntimeException("Unexpected severity " + severity);
                        }

                        // Ace munges all the msgs together in one popup for annotations on the same line/col
                        // so add the severity as if we have some info + warnings then we get a warning
                        // icon whose popup contains all the msgs.
                        annotations.add(new Annotation(
                                row,
                                col,
                                error.getSeverity().toString() + " " + error.getMessage(),
                                annotationType));
                    }
                }
            }
        }

        // Ensure line numbers are visible if there are annotations.
        if (!annotations.isEmpty()) {
            getLineNumbersOption().setOn(true);
        }

        editor.setAnnotations(annotations);
        doLayout(indicatorsOption.isOn());
    }

    @Override
    public void setFormattedHighlights(final Function<String, List<TextRange>> highlightsFunction) {
        this.formattedHighlightsFunc = highlightsFunction;
    }

    @Override
    public void setHighlights(final List<TextRange> highlights) {
        Scheduler.get().scheduleDeferred(() -> {
            if (highlights != null && highlights.size() > 0) {
                // Find our first from location
                final Location minLocation = highlights.stream()
                        .map(TextRange::getFrom)
                        .min(Location::compareTo)
                        .orElse(DefaultLocation.beginning());

                final List<Marker> markers = highlights.stream()
                        .map(highlightStr -> {
                            // Location is all one based and exclusive, AceRange is a mix
                            return AceRange.create(
                                    highlightStr.getFrom().getLineNo() - firstLineNumber,
                                    highlightStr.getFrom().getColNo() - 1, // zero-based & inclusive
                                    highlightStr.getTo().getLineNo() - firstLineNumber,
                                    highlightStr.getTo().getColNo()); // zero-based & exclusive so no need to change
                        })
                        .map(aceRange ->
                                new Marker(aceRange, "hl", AceMarkerType.TEXT, false))
                        .collect(Collectors.toList());

                editor.setMarkers(markers);
                // Move the cursor location so the highlight is in view.  Line and col as we
                // could be dealing with single line data with the highlight at col 500 for example.
                editor.gotoLocation(
                        minLocation.getLineNo() - firstLineNumber + 1,
                        minLocation.getColNo());
            } else {
                editor.setMarkers(null);
            }
        });
    }

    @Override
    public void setMarkers(final List<Marker> markers) {
        editor.setMarkers(markers);
    }

    @Override
    public void setErrorText(final String title, final String errorText) {

        final SafeHtmlBuilder safeHtmlBuilder = new SafeHtmlBuilder()
                .appendHtmlConstant("<div class=\"editor-error\">")
                .appendHtmlConstant("<p>")
                .append(SafeHtmlUtils.fromString(title))
                .appendHtmlConstant("</p>");

        if (errorText != null) {
            final String[] errorTextLines = errorText.split("\n");
            for (final String line : errorTextLines) {
                safeHtmlBuilder
                        .appendHtmlConstant("<p>")
                        .append(SafeHtmlUtils.fromString(line))
                        .appendHtmlConstant("</p>");
            }
        }

        safeHtmlBuilder.appendHtmlConstant("</div>");

        final ScrollPanel scrollPanel = new ScrollPanel();
        scrollPanel.addDomHandler(this::handleMouseDown, MouseDownEvent.getType());
        scrollPanel.getElement().addClassName("editor-error-container");
        scrollPanel.getElement().addClassName("max");
        scrollPanel.setWidget(new HTMLPanel(safeHtmlBuilder.toSafeHtml()).asWidget());
        content.setWidget(scrollPanel.asWidget());
    }

    private String formatAsIfXml(final String text) {
        return new XmlFormatter().format(text);
    }

    /**
     * Formats the currently displayed text.
     */
    private void format() {
        Scheduler.get().scheduleDeferred(() -> {
            final int scrollTop = editor.getScrollTop();
            final AceEditorCursorPosition cursorPosition = editor.getCursorPosition();

            final String formattedText;
            if (isModeXml(mode)) {
                formattedText = formatAsIfXml(getText());
                setText(formattedText);
            } else {
                editor.beautify();
                formattedText = editor.getText();
            }

            if (cursorPosition != null) {
                editor.moveTo(cursorPosition.getRow(), cursorPosition.getColumn());
            }
            if (scrollTop > 0) {
                editor.setScrollTop(scrollTop);
            }

            editor.focus();

            FormatEvent.fire(this);

            applyFormattedHighlights(formattedText);
        });
    }

    private boolean isModeXml(final AceEditorMode mode) {
        return XML_MODES.contains(mode);
    }

    private void applyFormattedHighlights(final String formattedText) {
        if (formattedHighlightsFunc != null) {
            final List<TextRange> highlightRanges = formattedHighlightsFunc.apply(formattedText);
            setHighlights(highlightRanges);
        }
    }

    @Override
    public Action getFormatAction() {
        return formatAction;
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
    public Option getShowIndentGuides() {
        return showIndentGuides;
    }

    @Override
    public Option getShowInvisiblesOption() {
        return showInvisiblesOption;
    }

    @Override
    public Option getBasicAutoCompletionOption() {
        return basicAutoCompletionOption;
    }

    @Override
    public Option getSnippetsOption() {
        return snippetsOption;
    }

    @Override
    public Option getHighlightActiveLineOption() {
        return highlightActiveLineOption;
    }

    @Override
    public Option getViewAsHexOption() {
        return viewAsHexOption;
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
    public void setOptionsToDefaultAvailability() {
        getBasicAutoCompletionOption().setToDefaultAvailability();
        getFormatAction().setToDefaultAvailability();
        getHighlightActiveLineOption().setToDefaultAvailability();
        getHighlightActiveLineOption().setToDefaultState();
        getLineNumbersOption().setToDefaultAvailability();
        getLineWrapOption().setToDefaultAvailability();
        getLiveAutoCompletionOption().setToDefaultAvailability();
        getShowIndentGuides().setToDefaultAvailability();
        getShowInvisiblesOption().setToDefaultAvailability();
        getSnippetsOption().setToDefaultAvailability();
        getStylesOption().setToDefaultAvailability();
        getUseVimBindingsOption().setToDefaultAvailability();
        getViewAsHexOption().setToDefaultAvailability();
    }


    @Override
    public HandlerRegistration addFormatHandler(final FormatHandler handler) {
        return content.addHandler(handler, FormatEvent.TYPE);
    }

    @Override
    public HandlerRegistration addContextMenuHandler(final ContextMenuEvent.Handler handler) {
        return content.addHandler(handler, ContextMenuEvent.getType());
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<FlowPanel, EditorViewImpl> {

    }
}
