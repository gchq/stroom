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

package stroom.editor.client.component;

import stroom.editor.client.ace.Editor;
import stroom.editor.client.ace.IndicatorLines;
import stroom.editor.client.ace.IndicatorPopup;
import stroom.editor.client.ace.Marker;
import stroom.editor.client.ace.RightBar;
import stroom.ui.config.shared.AceEditorTheme;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.Location;
import stroom.util.shared.NullSafe;
import stroom.util.shared.TextRange;
import stroom.widget.component.client.Component;
import stroom.widget.contextmenu.client.ContextMenuManager;
import stroom.widget.contextmenu.client.TextEditTarget;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.tab.client.GlobalResizeObserver;
import stroom.widget.text.client.Html;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.ContextMenuEvent;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.Widget;
import edu.ycp.cs.dh.acegwt.client.ace.AceMarkerType;
import edu.ycp.cs.dh.acegwt.client.ace.AceRange;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/// A code editor (BEM block `code-editor`, `code-editor.css`): text in Ace ([Editor]), e.g. XSLT or a
/// query, with an overview bar ([RightBar]) of its errors and warnings beside it, which shows them
/// in a popup ([IndicatorPopup]) as the mouse moves over it. An error loading the text is shown in
/// its place ([#setErrorText]).
///
/// It is dumb: its [CodeEditorController] sets its text and options, and shows its menu, which it
/// asks for ([#setOnContextMenu]).
public class CodeEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String HIGHLIGHT_CLASS = "code-editor__highlight";

    @UiField
    FlowPanel content;
    @UiField
    Editor editor;
    @UiField
    ScrollPanel errorPanel;
    @UiField
    Html errorText;
    @UiField
    RightBar rightBar;

    private IndicatorLines indicators;
    private boolean showIndicators;
    private int firstLineNumber = 1;
    private BiConsumer<PopupPosition, TextEditTarget> onContextMenu = (popupPosition, editTarget) -> {
    };

    /// Makes an empty editor.
    public CodeEditor() {
        initWidget(BINDER.createAndBindUi(this));
        rightBar.setEditor(editor);
        // Laid out again as its text's panel changes size
        content.addAttachHandler(event -> {
            if (event.isAttached()) {
                GlobalResizeObserver.addListener(content.getElement(), element -> layout());
            } else {
                GlobalResizeObserver.removeListener(content.getElement());
            }
        });
        editor.addDomHandler(this::onContextMenu, ContextMenuEvent.getType());
        errorPanel.addDomHandler(this::onContextMenu, ContextMenuEvent.getType());
    }

    /// @return The Ace editor the text is in, for the controller to set it up and act on it.
    public Editor getEditor() {
        return editor;
    }

    /// Shows text in place of any error shown.
    ///
    /// @param text The text, or null for none.
    public void setText(final String text) {
        showEditor();
        editor.setText(NullSafe.string(text));
    }

    /// @return The text.
    public String getText() {
        return editor.getText();
    }

    /// Shows an error loading the text in place of the text: its title, then its lines.
    ///
    /// @param title     The error's title.
    /// @param errorText The error's text, its lines separated by new lines, or null for none.
    public void setErrorText(final String title, final String errorText) {
        final SafeHtmlBuilder safeHtmlBuilder = new SafeHtmlBuilder()
                .appendHtmlConstant("<p>")
                .append(SafeHtmlUtils.fromString(title))
                .appendHtmlConstant("</p>");
        if (errorText != null) {
            for (final String line : errorText.split("\n")) {
                safeHtmlBuilder
                        .appendHtmlConstant("<p>")
                        .append(SafeHtmlUtils.fromString(line))
                        .appendHtmlConstant("</p>");
            }
        }
        this.errorText.setHTML(safeHtmlBuilder.toSafeHtml());
        editor.setVisible(false);
        errorPanel.setVisible(true);
    }

    /// Sets the number of the first line, e.g. for a part of a stream.
    ///
    /// @param firstLineNumber The number.
    public void setFirstLineNumber(final int firstLineNumber) {
        this.firstLineNumber = firstLineNumber;
        editor.setFirstLineNumber(firstLineNumber);
    }

    /// Sets the lines the overview bar marks with their errors and warnings.
    ///
    /// @param indicators The lines and their errors, or null for none.
    public void setIndicators(final IndicatorLines indicators) {
        this.indicators = indicators;
        layout();
    }

    /// Sets whether the overview bar shows the errors and warnings.
    ///
    /// @param showIndicators Whether it does.
    public void setShowIndicators(final boolean showIndicators) {
        this.showIndicators = showIndicators;
        layout();
    }

    /// Highlights ranges of the text, and moves to the first so that it is in view.
    ///
    /// @param highlights The ranges, or null or empty for none.
    public void setHighlights(final List<TextRange> highlights) {
        Scheduler.get().scheduleDeferred(() -> {
            if (highlights != null && !highlights.isEmpty()) {
                // Find our first from location
                final Location minLocation = highlights.stream()
                        .map(TextRange::getFrom)
                        .min(Location::compareTo)
                        .orElse(DefaultLocation.beginning());

                final List<Marker> markers = highlights.stream()
                        .map(highlight -> {
                            // Location is all one based and exclusive, AceRange is a mix
                            return AceRange.create(
                                    highlight.getFrom().getLineNo() - firstLineNumber,
                                    highlight.getFrom().getColNo() - 1, // zero-based & inclusive
                                    highlight.getTo().getLineNo() - firstLineNumber,
                                    highlight.getTo().getColNo()); // zero-based & exclusive so no need to change
                        })
                        .map(aceRange -> new Marker(aceRange, HIGHLIGHT_CLASS, AceMarkerType.TEXT, false))
                        .collect(Collectors.toList());

                editor.setMarkers(markers);
                // Move the cursor location so the highlight is in view. Line and col as we could be
                // dealing with single line data with the highlight at col 500 for example
                editor.gotoLocation(
                        minLocation.getLineNo() - firstLineNumber + 1,
                        minLocation.getColNo());
            } else {
                editor.setMarkers(null);
            }
        });
    }

    /// Sets whether the editor's controls are shown over its text, leaving room for them.
    ///
    /// @param controlsVisible Whether they are.
    public void setControlsVisible(final boolean controlsVisible) {
        if (controlsVisible) {
            editor.setScrollMargin(0, 69, 0, 0);
        } else {
            editor.setScrollMargin(0, 0, 0, 0);
        }
    }

    /// @param theme The Ace theme the text is shown in.
    public void setTheme(final AceEditorTheme theme) {
        editor.setTheme(theme);
    }

    /// Puts the focus on the text.
    public void focus() {
        editor.focus();
    }

    /// Sets what is told when the editor's menu is asked for: a right-click, Shift+F10 or the
    /// context menu key, in place of the browser's menu (a Shift+right-click, or any if the user
    /// prefers the browser's menu for text, is left to the browser).
    ///
    /// @param onContextMenu Given where to show the menu and the text it is for (whose edit items
    ///                      start it), or null for the text if it was asked for elsewhere (e.g. on
    ///                      an error shown in place of the text).
    public void setOnContextMenu(final BiConsumer<PopupPosition, TextEditTarget> onContextMenu) {
        this.onContextMenu = onContextMenu;
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        editor.setReadOnly(readOnly);
    }

    /// Lays the text and the overview bar out for the size they now have.
    void layout() {
        editor.onResize();
        rightBar.render(indicators, showIndicators);
    }

    private void showEditor() {
        if (!editor.isVisible()) {
            errorPanel.setVisible(false);
            editor.setVisible(true);
            // Ace was hidden, so lays itself out again for the size it has now
            layout();
        }
    }

    private void onContextMenu(final ContextMenuEvent event) {
        final NativeEvent nativeEvent = event.getNativeEvent();
        if (ContextMenuManager.isLeftToBrowser(nativeEvent)) {
            return;
        }
        nativeEvent.preventDefault();
        nativeEvent.stopPropagation();
        final TextEditTarget editTarget = ContextMenuManager.findEditTarget(nativeEvent);
        final PopupPosition popupPosition = editTarget != null
                ? ContextMenuManager.getPosition(nativeEvent, editTarget)
                : new PopupPosition(nativeEvent.getClientX(), nativeEvent.getClientY());
        onContextMenu.accept(popupPosition, editTarget);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, CodeEditor> {

    }
}
