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

package stroom.editor.client.component;

import stroom.editor.client.Action;
import stroom.editor.client.Option;
import stroom.editor.client.ace.Annotation;
import stroom.editor.client.ace.Editor;
import stroom.editor.client.ace.Indicator;
import stroom.editor.client.ace.IndicatorLines;
import stroom.editor.client.ace.Marker;
import stroom.editor.client.event.FormatEvent;
import stroom.editor.client.model.XmlFormatter;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.shared.AceEditorTheme;
import stroom.util.shared.Location;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;
import stroom.util.shared.TextRange;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.Listeners;
import stroom.widget.contextmenu.client.ContextMenuManager;
import stroom.widget.contextmenu.client.TextEditTarget;
import stroom.widget.menu.client.CheckMenuItem;
import stroom.widget.menu.client.IconMenuItem;
import stroom.widget.menu.client.IconParentMenuItem;
import stroom.widget.menu.client.Item;
import stroom.widget.menu.client.ShowMenuEvent;
import stroom.widget.popup.client.PopupPosition;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.Command;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceAnnotationType;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionProvider;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorCursorPosition;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/// A code editor's controller: it sets the text of its [CodeEditor] and holds its options
/// ([CodeEditorOptions]) and its Format action, and shows its menu: the edit items for its text (Cut,
/// Copy, Paste and so on), Format, and the options, ticked to show how they are set. Its
/// [AceEditorController] sets the editor up as the user prefers.
///
/// A template declares the editor, and its screen's controller attaches one of these to it:
///
/// ```java
/// codeEditor = make(codeEditorControllerProvider).attach(view.getCodeEditor());
/// ```
public class CodeEditorController extends Controller {

    // These modes are all XML and thus can be formatted as XML
    private static final EnumSet<AceEditorMode> XML_MODES = EnumSet.of(
            AceEditorMode.XML,
            AceEditorMode.STROOM_DATA_SPLITTER,
            AceEditorMode.STROOM_FRAGMENT_PARSER,
            AceEditorMode.STROOM_COMBINED_PARSER);

    private final Action formatAction = new Action("Format", false, this::format);
    private final AceEditorController aceEditorController;
    private final Listeners<FormatEvent> formatListeners = new Listeners<>();
    private CodeEditor view;
    private Editor editor;
    private CodeEditorOptions options;
    private Function<String, List<TextRange>> formattedHighlightsFunc;

    /// Made by GIN; [#attach(CodeEditor)] gives it its editor.
    ///
    /// @param eventBus            The application's event bus, which its menu is shown on.
    /// @param aceEditorController Sets the editor up as the user prefers; this owns it.
    @Inject
    public CodeEditorController(final EventBus eventBus,
                                final AceEditorController aceEditorController) {
        super(eventBus);
        this.aceEditorController = own(aceEditorController);
    }

    /// Attaches this to an editor, e.g. one a template declares, setting it up: its options at
    /// their defaults, as the user prefers, with its menu. The editor is then this controller's
    /// widget, e.g. to show it as a dialog.
    ///
    /// @param view The editor.
    /// @return This, for its owner to keep.
    public CodeEditorController attach(final CodeEditor view) {
        this.view = Objects.requireNonNull(view, "view");
        initWidget(view);
        this.editor = view.getEditor();
        options = new CodeEditorOptions(editor, view::setShowIndicators);
        aceEditorController.attach(editor, options);
        view.setOnContextMenu(this::showMenu);
        return this;
    }

    /// @return The editor this is attached to.
    public CodeEditor getCodeEditor() {
        return view;
    }

    /// @return The options, which its menu turns on and off.
    public CodeEditorOptions getOptions() {
        return options;
    }

    /// @return Format, which formats the text, e.g. as XML.
    public Action getFormatAction() {
        return formatAction;
    }

    /// @return The text.
    public String getText() {
        return view.getText();
    }

    /// Shows text in place of any error shown.
    ///
    /// @param text The text, or null for none.
    public void setText(final String text) {
        view.setText(text);
    }

    /// Shows text, formatted as XML or as it is, in place of any error shown.
    ///
    /// @param text   The text, or null for none.
    /// @param format Whether to format it, as if it were XML, first.
    public void setText(final String text, final boolean format) {
        if (text == null) {
            view.setText("");
        } else if (format) {
            final String formattedText = new XmlFormatter().format(text);
            view.setText(formattedText);
            applyFormattedHighlights(formattedText);
        } else {
            view.setText(text);
        }
    }

    /// Shows an error loading the text in place of the text.
    ///
    /// @param title     The error's title.
    /// @param errorText The error's text, or null for none.
    public void setErrorText(final String title, final String errorText) {
        view.setErrorText(title, errorText);
    }

    /// Sets the mode the text is in, e.g. XML, which it is highlighted for while Syntax
    /// Highlighting is on.
    ///
    /// @param mode The mode.
    public void setMode(final AceEditorMode mode) {
        options.setMode(mode);
    }

    /// @param theme The Ace theme the text is shown in.
    public void setTheme(final AceEditorTheme theme) {
        view.setTheme(theme);
    }

    /// Shows the errors and warnings of the text's lines, in its gutter (with its line numbers
    /// shown, if there are any) and in the overview bar.
    ///
    /// @param indicators The lines and their errors, or null for none.
    public void setIndicators(final IndicatorLines indicators) {
        final List<Annotation> annotations = toAnnotations(indicators);
        // Ensure line numbers are visible if there are annotations
        if (!annotations.isEmpty()) {
            options.getLineNumbersOption().setOn(true);
        }
        editor.setAnnotations(annotations);
        view.setIndicators(indicators);
    }

    /// Sets a function that gives the highlights in the text once it is formatted, as the line and
    /// column positions in the formatted text may differ from those in the original. Set it before
    /// the text.
    ///
    /// @param highlightsFunction Gives the ranges to highlight in the formatted text.
    public void setFormattedHighlights(final Function<String, List<TextRange>> highlightsFunction) {
        this.formattedHighlightsFunc = highlightsFunction;
    }

    /// @return The Ace editor's ID, e.g. to register completion providers for it.
    public String getEditorId() {
        return editor.getId();
    }

    /// Puts the focus on the text.
    public void focus() {
        view.focus();
    }

    /// @return Whether the text is unchanged since it was set or last marked clean.
    public boolean isClean() {
        return editor.isClean();
    }

    /// Marks the text as unchanged, e.g. once it is saved.
    public void markClean() {
        editor.markClean();
    }

    /// @param text Text to insert where the cursor is.
    public void insertTextAtCursor(final String text) {
        editor.insertTextAtCursor(text);
    }

    /// @param text Text to put in place of the text selected.
    public void replaceSelectedText(final String text) {
        editor.replaceSelectedText(text);
    }

    /// @param snippet An Ace snippet to insert where the cursor is, with its tab stops.
    public void insertSnippet(final String snippet) {
        editor.insertSnippet(snippet);
    }

    /// Sets the number of the first line, e.g. for a part of a stream.
    ///
    /// @param firstLineNumber The number.
    public void setFirstLineNumber(final int firstLineNumber) {
        view.setFirstLineNumber(firstLineNumber);
    }

    /// Marks ranges of the text, e.g. the errors of a step.
    ///
    /// @param markers The markers, or null for none.
    public void setMarkers(final List<Marker> markers) {
        editor.setMarkers(markers);
    }

    /// Highlights ranges of the text, and moves to the first so that it is in view.
    ///
    /// @param highlights The ranges, or null or empty for none.
    public void setHighlights(final List<TextRange> highlights) {
        view.setHighlights(highlights);
    }

    /// Sets whether the editor's controls are shown over its text, leaving room for them.
    ///
    /// @param visible Whether they are.
    public void setControlsVisible(final boolean visible) {
        view.setControlsVisible(visible);
    }

    /// Makes Format and every option available or not, as they are by default.
    public void setOptionsToDefaultAvailability() {
        options.setToDefaultAvailability();
        formatAction.setToDefaultAvailability();
    }

    /// Makes the text read only or not; read only, Format and the auto completion options are not
    /// available, and the active line isn't highlighted.
    ///
    /// @param readOnly Whether it is.
    public void setReadOnly(final boolean readOnly) {
        if (readOnly) {
            formatAction.setUnavailable();
            options.getBasicAutoCompletionOption().setOff();
            options.getBasicAutoCompletionOption().setUnavailable();
            options.getSnippetsOption().setOff();
            options.getSnippetsOption().setUnavailable();
            options.getLiveAutoCompletionOption().setOff();
            options.getLiveAutoCompletionOption().setUnavailable();
            options.getHighlightActiveLineOption().setOff();
        } else {
            formatAction.setToDefaultAvailability();
            options.getBasicAutoCompletionOption().setToDefaultState();
            options.getBasicAutoCompletionOption().setToDefaultAvailability();
            options.getSnippetsOption().setToDefaultState();
            options.getSnippetsOption().setToDefaultAvailability();
            options.getLiveAutoCompletionOption().setToDefaultState();
            options.getLiveAutoCompletionOption().setToDefaultAvailability();
            options.getHighlightActiveLineOption().setToDefaultState();
        }
        view.setReadOnly(readOnly);
    }

    /// Listens for the text being formatted with Format, until the registration is removed.
    ///
    /// @param listener Told each time.
    /// @return The registration, for the listener's owner to own.
    public HandlerRegistration addFormatListener(final Consumer<FormatEvent> listener) {
        // As GWT's registration, which the editor's format handlers are given
        return formatListeners.add(listener)::removeHandler;
    }

    /// Listens for the text changing, until the registration is removed.
    ///
    /// @param handler Told the text each time.
    /// @return The registration, for the handler's owner to own.
    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<String> handler) {
        return editor.addValueChangeHandler(handler);
    }

    /// @see AceEditorController#registerCompletionProviders(AceEditorMode, AceCompletionProvider...)
    public void registerCompletionProviders(final AceEditorMode aceEditorMode,
                                            final AceCompletionProvider... completionProviders) {
        aceEditorController.registerCompletionProviders(aceEditorMode, completionProviders);
    }

    /// @see AceEditorController#registerCompletionProviders(AceCompletionProvider...)
    public void registerCompletionProviders(final AceCompletionProvider... completionProviders) {
        aceEditorController.registerCompletionProviders(completionProviders);
    }

    /// @see AceEditorController#deRegisterCompletionProviders()
    public void deRegisterCompletionProviders() {
        aceEditorController.deRegisterCompletionProviders();
    }

    // Shows the editor's menu: on its text, the text's edit items, then Format and the options in a
    // sub menu; elsewhere (e.g. on an error shown in place of the text), the options then Format
    private void showMenu(final PopupPosition popupPosition, final TextEditTarget editTarget) {
        final ShowMenuEvent.Builder builder = ShowMenuEvent
                .builder()
                .name(editTarget != null
                        ? "Editor"
                        : "Editor Options")
                .items(editTarget != null
                        ? ContextMenuManager.withEditItems(editTarget, getEditorItems())
                        : getOptionAndFormatItems())
                .popupPosition(popupPosition);
        if (editTarget != null) {
            builder.onHide(e -> editTarget.restore());
        }
        builder.fire(this);
    }

    // Format, then the editor's options in a sub menu of their own, below the edit items
    private List<Item> getEditorItems() {
        final List<Item> menuItems = new ArrayList<>();
        // With icons, as the edit items above them have
        if (formatAction.isAvailable()) {
            menuItems.add(new IconMenuItem.Builder()
                    .priority(0)
                    .icon(SvgImage.FORMAT)
                    .text(formatAction.getText())
                    .command(formatAction::execute)
                    .build());
        }
        final List<Item> optionItems = getOptionItems();
        if (!optionItems.isEmpty()) {
            menuItems.add(new IconParentMenuItem.Builder()
                    .priority(1)
                    .icon(SvgImage.SETTINGS)
                    .text("Editor Options")
                    .children(optionItems)
                    .build());
        }
        return menuItems;
    }

    // The editor's options, ticked to show its current settings
    private List<Item> getOptionItems() {
        int position = 0;
        final List<Item> menuItems = new ArrayList<>();
        addMenuItem(position++, menuItems, options.getStylesOption());
        addMenuItem(position++, menuItems, options.getIndicatorsOption());
        addMenuItem(position++, menuItems, options.getLineNumbersOption());
        addMenuItem(position++, menuItems, options.getLineWrapOption());
        addMenuItem(position++, menuItems, options.getShowIndentGuides());
        addMenuItem(position++, menuItems, options.getShowInvisiblesOption());
        addMenuItem(position++, menuItems, options.getViewAsHexOption());
        addMenuItem(position++, menuItems, options.getHighlightActiveLineOption());
        addMenuItem(position++, menuItems, options.getUseVimBindingsOption());
        addMenuItem(position++, menuItems, options.getBasicAutoCompletionOption());
        addMenuItem(position++, menuItems, options.getSnippetsOption());
        addMenuItem(position, menuItems, options.getLiveAutoCompletionOption());
        return menuItems;
    }

    // The options with Format below them, for a menu opened away from the editor's text
    private List<Item> getOptionAndFormatItems() {
        final List<Item> menuItems = getOptionItems();
        if (formatAction.isAvailable()) {
            menuItems.add(createItem(formatAction.getText(), formatAction::execute, menuItems.size()));
        }
        return menuItems;
    }

    // An option is a setting that is on or off, so its item is ticked while it is on
    private void addMenuItem(final int position, final List<Item> menuItems, final Option option) {
        if (option.isAvailable()) {
            menuItems.add(new CheckMenuItem.Builder()
                    .priority(position)
                    .text(option.getName())
                    .checked(option.isOn())
                    .command(() -> option.setOn(!option.isOn()))
                    .build());
        }
    }

    private Item createItem(final SafeHtml text, final Command command, final int position) {
        return new IconMenuItem.Builder()
                .priority(position)
                .text(text)
                .command(command)
                .build();
    }

    // Formats the text shown, keeping its cursor and scroll position
    private void format() {
        Scheduler.get().scheduleDeferred(() -> {
            final int scrollTop = editor.getScrollTop();
            final AceEditorCursorPosition cursorPosition = editor.getCursorPosition();

            final String formattedText;
            if (XML_MODES.contains(options.getMode())) {
                formattedText = new XmlFormatter().format(getText());
                view.setText(formattedText);
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

            formatListeners.fire(new FormatEvent());

            applyFormattedHighlights(formattedText);
        });
    }

    private void applyFormattedHighlights(final String formattedText) {
        if (formattedHighlightsFunc != null) {
            view.setHighlights(formattedHighlightsFunc.apply(formattedText));
        }
    }

    // Each error of the lines as an annotation in the gutter
    private static List<Annotation> toAnnotations(final IndicatorLines indicators) {
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
                        // Ace munges all the msgs together in one popup for annotations on the same
                        // line/col so add the severity as if we have some info + warnings then we
                        // get a warning icon whose popup contains all the msgs
                        annotations.add(new Annotation(
                                row,
                                col,
                                error.getSeverity().toString() + " " + error.getMessage(),
                                toAnnotationType(error.getSeverity())));
                    }
                }
            }
        }
        return annotations;
    }

    private static AceAnnotationType toAnnotationType(final Severity severity) {
        switch (severity) {
            case INFO:
                return AceAnnotationType.INFO;
            case WARNING:
                return AceAnnotationType.WARNING;
            case ERROR:
                return AceAnnotationType.ERROR;
            case FATAL_ERROR:
                return AceAnnotationType.FATAL_ERROR;
            default:
                throw new RuntimeException("Unexpected severity " + severity);
        }
    }
}
