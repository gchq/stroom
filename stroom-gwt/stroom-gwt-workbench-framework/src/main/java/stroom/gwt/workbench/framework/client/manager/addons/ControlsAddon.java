/*
 * Copyright 2026 Crown Copyright
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

package stroom.gwt.workbench.framework.client.manager.addons;

import stroom.gwt.workbench.framework.client.BrowserUtil;
import stroom.gwt.workbench.framework.client.args.ArgType;
import stroom.gwt.workbench.framework.client.args.Args;
import stroom.gwt.workbench.framework.client.args.ControlType;
import stroom.gwt.workbench.framework.client.manager.HtmlTokens;
import stroom.gwt.workbench.framework.client.manager.MenuHtml;
import stroom.gwt.workbench.framework.client.story.Story;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.dom.client.SelectElement;
import com.google.gwt.dom.client.TextAreaElement;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/// The Controls addon, which shows the story's args in a table with a control to change each
/// one, as in React Storybook.
public class ControlsAddon extends Addon {

    private static final String ATTR_ARG = "data-arg";
    private static final String ATTR_ACTION = "data-action";
    private static final String ATTR_VALUE = "data-value";
    private static final String ACTION_SET = "set";
    private static final String ACTION_BOOLEAN = "boolean";
    private static final String ACTION_RESET = "reset";
    private static final String CLASS_INVALID = "wbm-control--invalid";
    private static final String CLASS_MODIFIED_SLOT = "wbm-controls__modified-slot";

    private final Consumer<Args> argsChangeHandler;
    private Story story;
    private Args args = Args.empty();

    /// @param argsChangeHandler Called with all the story's args when the user changes one.
    public ControlsAddon(final Consumer<Args> argsChangeHandler) {
        this.argsChangeHandler = argsChangeHandler;
    }

    @Override
    public String getTitle() {
        return "Controls";
    }

    @Override
    public String getBadge() {
        return story != null && !story.getArgTypes().isEmpty()
                ? String.valueOf(story.getArgTypes().size())
                : null;
    }

    @Override
    protected void onAttach() {
        BrowserUtil.addListener(getElement(), "click", event -> onClick(Element.as(event.getEventTarget())));
        // Each control is handled on one event only, so a change isn't applied twice
        BrowserUtil.addListener(getElement(), "input", event -> onEvent(Element.as(event.getEventTarget()), false));
        BrowserUtil.addListener(getElement(), "change", event -> onEvent(Element.as(event.getEventTarget()), true));
    }

    /// Shows a story's args.
    ///
    /// @param story The story.
    /// @param args  The story's current args.
    public void setStory(final Story story, final Args args) {
        this.story = story;
        this.args = args;
        render();
        badgeChanged();
    }

    // ---------- Rendering ----------

    private void render() {
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        if (story == null || story.getArgTypes().isEmpty()) {
            appendEmptyState(builder, "No controls for this story",
                    "Declare arg types for the story's component, e.g. with ArgType.text(...), and a control "
                    + "for each one is shown here so its value can be changed.", null);
            getElement().setInnerSafeHtml(builder.toSafeHtml());
            return;
        }

        final boolean detailed = story.getArgTypes().stream()
                .anyMatch(argType -> argType.getDescription() != null || argType.getDefaultSummary() != null);
        builder.appendHtmlConstant("<div class=\"wbm-controls\"><table class=\"wbm-args-table"
                                   + (detailed
                ? " wbm-args-table--detailed"
                : "")
                                   + "\"><thead><tr><th>Name</th>");
        if (detailed) {
            builder.appendHtmlConstant("<th>Description</th><th>Default</th>");
        }
        builder.appendHtmlConstant("<th><span class=\"wbm-args-table__control-header\">Control"
                                   + "<button type=\"button\" class=\"wbm-icon-button\" " + ATTR_ACTION + "=\""
                                   + ACTION_RESET + "\" aria-label=\"Reset controls\" title=\"Reset controls\">");
        MenuHtml.appendIcon(builder, "wbm-icon", "wbm-icon-undo");
        builder.appendHtmlConstant("</button></span></th></tr></thead><tbody>");
        for (final ArgType argType : story.getArgTypes()) {
            appendRow(builder, argType, detailed);
        }
        builder.appendHtmlConstant("</tbody></table></div><div class=\"" + CLASS_MODIFIED_SLOT + "\"></div>");
        getElement().setInnerSafeHtml(builder.toSafeHtml());
        renderModifiedBar();
    }

    /// Shows or hides the bar telling the user they have changed the story's args.
    private void renderModifiedBar() {
        final NodeList<Element> slots = getElement().getElementsByTagName("div");
        Element slot = null;
        for (int i = 0; i < slots.getLength(); i++) {
            if (slots.getItem(i).hasClassName(CLASS_MODIFIED_SLOT)) {
                slot = slots.getItem(i);
            }
        }
        if (slot == null) {
            return;
        }
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        if (!args.equals(story.getInitialArgs())) {
            builder.appendHtmlConstant("<div class=\"wbm-controls__modified\"><span>You modified this story. "
                                       + "Do you want to save your changes?</span>"
                                       + "<button type=\"button\" class=\"wbm-secondary-button\" "
                                       + ATTR_ACTION + "=\"" + ACTION_RESET + "\">");
            MenuHtml.appendIcon(builder, "wbm-icon", "wbm-icon-undo");
            builder.appendHtmlConstant("Reset</button></div>");
        }
        slot.setInnerSafeHtml(builder.toSafeHtml());
    }

    private void appendRow(final SafeHtmlBuilder builder, final ArgType argType, final boolean detailed) {
        builder.appendHtmlConstant("<tr><td class=\"wbm-args-table__name\">")
                .appendEscaped(argType.getName())
                .appendHtmlConstant("</td>");
        if (detailed) {
            builder.appendHtmlConstant("<td class=\"wbm-args-table__description\">");
            if (argType.getDescription() != null) {
                builder.appendHtmlConstant("<div>").appendEscaped(argType.getDescription())
                        .appendHtmlConstant("</div>");
            }
            builder.appendHtmlConstant("<div><code class=\"wbm-code\">")
                    .appendEscaped(argType.getTypeName())
                    .appendHtmlConstant("</code></div></td><td>");
            if (argType.getDefaultSummary() != null) {
                builder.appendHtmlConstant("<code class=\"wbm-code\">")
                        .appendEscaped(argType.getDefaultSummary())
                        .appendHtmlConstant("</code>");
            } else {
                builder.appendHtmlConstant("<span class=\"wbm-muted\">-</span>");
            }
            builder.appendHtmlConstant("</td>");
        }
        builder.appendHtmlConstant("<td>");
        appendControl(builder, argType);
        builder.appendHtmlConstant("</td></tr>");
    }

    private void appendControl(final SafeHtmlBuilder builder, final ArgType argType) {
        final String name = argType.getName();
        final ControlType control = argType.getControl();
        if (control == ControlType.ACTION) {
            builder.appendHtmlConstant("<span class=\"wbm-muted\">-</span>");
            return;
        }
        if (!args.has(name) && !control.hasOptions()) {
            builder.appendHtmlConstant("<button type=\"button\" class=\"wbm-secondary-button\" " + ATTR_ACTION
                                       + "=\"" + ACTION_SET + "\" " + ATTR_ARG + "=\"")
                    .appendEscaped(name)
                    .appendHtmlConstant("\">Set " + setLabel(control) + "</button>");
            return;
        }

        final String value = args.getString(name, "");
        switch (control) {
            case BOOLEAN:
                final boolean on = args.getBoolean(name);
                builder.appendHtmlConstant("<span class=\"wbm-toggle\" role=\"radiogroup\">");
                appendToggleOption(builder, name, false, !on);
                appendToggleOption(builder, name, true, on);
                builder.appendHtmlConstant("</span>");
                break;
            case NUMBER:
                appendInput(builder, name, "number", value, "");
                break;
            case RANGE:
                builder.appendHtmlConstant("<span class=\"wbm-range\"><span class=\"wbm-muted\">")
                        .appendEscaped(Args.formatNumber(argType.getMin()))
                        .appendHtmlConstant("</span>");
                appendInput(builder, name, "range", value, " min=\"" + Args.formatNumber(argType.getMin())
                                                           + "\" max=\"" + Args.formatNumber(argType.getMax())
                                                           + "\" step=\"" + Args.formatNumber(argType.getStep())
                                                           + "\"");
                builder.appendHtmlConstant("<span class=\"wbm-muted\">")
                        .appendEscaped(value + " / " + Args.formatNumber(argType.getMax()))
                        .appendHtmlConstant("</span></span>");
                break;
            case RADIO:
            case INLINE_RADIO:
            case CHECK:
            case INLINE_CHECK:
                appendOptions(builder, argType);
                break;
            case SELECT:
                builder.appendHtmlConstant("<select class=\"wbm-select\" " + ATTR_ARG + "=\"")
                        .appendEscaped(name)
                        .appendHtmlConstant("\"><option value=\"\">Choose option...</option>");
                for (final String option : argType.getOptions()) {
                    builder.appendHtmlConstant("<option" + (option.equals(value)
                                    ? " selected"
                                    : "") + " value=\"")
                            .appendEscaped(option)
                            .appendHtmlConstant("\">")
                            .appendEscaped(option)
                            .appendHtmlConstant("</option>");
                }
                builder.appendHtmlConstant("</select>");
                break;
            case COLOR:
                builder.appendHtmlConstant("<span class=\"wbm-color\"><span class=\"wbm-color__swatch\"");
                // The colour may come from a shared URL so is only used if it is a plain colour
                if (HtmlTokens.isSafeColour(value)) {
                    builder.appendHtmlConstant(" style=\"background-color: ")
                            .appendEscaped(value)
                            .appendHtmlConstant("\"");
                }
                builder.appendHtmlConstant("></span>");
                appendInput(builder, name, "text", value, "");
                builder.appendHtmlConstant("</span>");
                break;
            case DATE:
                appendInput(builder, name, "datetime-local", value, "");
                break;
            case OBJECT:
            case TEXT:
            default:
                builder.appendHtmlConstant("<textarea class=\"wbm-textarea" + (control == ControlType.OBJECT
                                ? " wbm-textarea--code"
                                : "") + "\" rows=\"1\" spellcheck=\"false\" " + ATTR_ARG + "=\"")
                        .appendEscaped(name)
                        .appendHtmlConstant("\">")
                        .appendEscaped(value)
                        .appendHtmlConstant("</textarea>");
                break;
        }
    }

    private static String setLabel(final ControlType control) {
        switch (control) {
            case BOOLEAN:
                return "boolean";
            case NUMBER:
            case RANGE:
                return "number";
            case COLOR:
                return "colour";
            case DATE:
                return "date";
            case OBJECT:
                return "object";
            default:
                return "string";
        }
    }

    private static void appendToggleOption(final SafeHtmlBuilder builder,
                                           final String name,
                                           final boolean value,
                                           final boolean selected) {
        builder.appendHtmlConstant("<button type=\"button\" role=\"radio\" class=\"wbm-toggle__option"
                                   + (selected
                ? " wbm-toggle__option--selected"
                : "")
                                   + "\" aria-checked=\"" + selected + "\" " + ATTR_ACTION + "=\""
                                   + ACTION_BOOLEAN + "\" " + ATTR_VALUE + "=\"" + value + "\" "
                                   + ATTR_ARG + "=\"")
                .appendEscaped(name)
                .appendHtmlConstant("\">" + (value
                        ? "True"
                        : "False") + "</button>");
    }

    private static void appendInput(final SafeHtmlBuilder builder,
                                    final String name,
                                    final String type,
                                    final String value,
                                    final String extraAttributes) {
        builder.appendHtmlConstant("<input class=\"wbm-input\" type=\"" + type + "\" spellcheck=\"false\""
                                   + extraAttributes + " " + ATTR_ARG + "=\"")
                .appendEscaped(name)
                .appendHtmlConstant("\" value=\"")
                .appendEscaped(value)
                .appendHtmlConstant("\">");
    }

    private void appendOptions(final SafeHtmlBuilder builder, final ArgType argType) {
        final ControlType control = argType.getControl();
        final boolean inline = control == ControlType.INLINE_RADIO || control == ControlType.INLINE_CHECK;
        final String inputType = control.isMulti()
                ? "checkbox"
                : "radio";
        final List<String> selected = control.isMulti()
                ? args.getList(argType.getName())
                : List.of(args.getString(argType.getName(), ""));
        builder.appendHtmlConstant("<fieldset class=\"wbm-options" + (inline
                ? " wbm-options--inline"
                : "") + "\">");
        for (final String option : argType.getOptions()) {
            builder.appendHtmlConstant("<label class=\"wbm-option\"><input type=\"" + inputType + "\""
                                       + (selected.contains(option)
                    ? " checked"
                    : "") + " " + ATTR_ARG + "=\"")
                    .appendEscaped(argType.getName())
                    .appendHtmlConstant("\" " + ATTR_VALUE + "=\"")
                    .appendEscaped(option)
                    .appendHtmlConstant("\"><span>")
                    .appendEscaped(option)
                    .appendHtmlConstant("</span></label>");
        }
        builder.appendHtmlConstant("</fieldset>");
    }

    // ---------- Events ----------

    private void onClick(final Element target) {
        final Element actionElement = BrowserUtil.closest(target, "[" + ATTR_ACTION + "]");
        if (actionElement == null || story == null) {
            return;
        }
        final String action = actionElement.getAttribute(ATTR_ACTION);
        final String name = actionElement.getAttribute(ATTR_ARG);
        if (ACTION_RESET.equals(action)) {
            changeArgs(story.getInitialArgs(), true);
        } else if (ACTION_SET.equals(action)) {
            changeArgs(args.with(name, initialValue(story.getArgType(name))), true);
        } else if (ACTION_BOOLEAN.equals(action)) {
            changeArgs(args.with(name, Boolean.parseBoolean(actionElement.getAttribute(ATTR_VALUE))), true);
        }
    }

    private static Object initialValue(final ArgType argType) {
        switch (argType.getControl()) {
            case BOOLEAN:
                return false;
            case NUMBER:
            case RANGE:
                return argType.getMin();
            case COLOR:
                return "#000000";
            case OBJECT:
                return "{}";
            default:
                return "";
        }
    }

    private void onEvent(final Element target, final boolean changeEvent) {
        final String inputType = "INPUT".equalsIgnoreCase(target.getTagName())
                ? target.<InputElement>cast().getType()
                : null;
        if (ControlEvents.isHandledOnChange(target.getTagName(), inputType) == changeEvent) {
            onInput(target);
        }
    }

    private void onInput(final Element target) {
        final String name = target.getAttribute(ATTR_ARG);
        if (name == null || name.isEmpty() || story == null || target.hasAttribute(ATTR_ACTION)) {
            return;
        }
        final ArgType argType = story.getArgType(name);
        if (argType == null) {
            return;
        }
        final String tagName = target.getTagName();
        if ("SELECT".equalsIgnoreCase(tagName)) {
            final String value = target.<SelectElement>cast().getValue();
            changeArgs(args.with(name, value.isEmpty()
                    ? null
                    : value), false);
        } else if ("TEXTAREA".equalsIgnoreCase(tagName)) {
            final TextAreaElement textArea = target.cast();
            final String value = textArea.getValue();
            if (argType.getControl() == ControlType.OBJECT && !isValidJson(value)) {
                textArea.addClassName(CLASS_INVALID);
                return;
            }
            textArea.removeClassName(CLASS_INVALID);
            changeArgs(args.with(name, value), false);
        } else {
            onInputElement(argType, target.cast());
        }
    }

    private void onInputElement(final ArgType argType, final InputElement input) {
        final String name = argType.getName();
        final String type = input.getType();
        if ("radio".equals(type)) {
            changeArgs(args.with(name, input.getAttribute(ATTR_VALUE)), false);
        } else if ("checkbox".equals(type)) {
            final List<String> values = new ArrayList<>();
            final NodeList<Element> boxes = getElement().getElementsByTagName("input");
            for (int i = 0; i < boxes.getLength(); i++) {
                final InputElement box = boxes.getItem(i).cast();
                if (name.equals(box.getAttribute(ATTR_ARG)) && box.isChecked()) {
                    values.add(box.getAttribute(ATTR_VALUE));
                }
            }
            changeArgs(args.with(name, values), false);
        } else {
            final Object value = argType.convert(input.getValue());
            // Update the range's label and the colour's swatch in place, as re-rendering would
            // stop a slider drag and take the focus from the colour field
            if (argType.getControl() == ControlType.RANGE) {
                updateRangeLabel(argType, input);
            } else if (argType.getControl() == ControlType.COLOR) {
                updateColourSwatch(input);
            }
            changeArgs(args.with(name, value), false);
        }
    }

    private static void updateRangeLabel(final ArgType argType, final InputElement input) {
        final Element label = input.getNextSiblingElement();
        if (label != null) {
            label.setInnerText(input.getValue() + " / " + Args.formatNumber(argType.getMax()));
        }
    }

    private static void updateColourSwatch(final InputElement input) {
        final Element swatch = input.getPreviousSiblingElement();
        if (swatch == null) {
            return;
        }
        final String colour = input.getValue();
        if (HtmlTokens.isSafeColour(colour)) {
            swatch.getStyle().setBackgroundColor(colour.trim());
        } else {
            swatch.getStyle().clearBackgroundColor();
        }
    }

    private static boolean isValidJson(final String value) {
        try {
            JSONParser.parseStrict(value);
            return true;
        } catch (final RuntimeException e) {
            return false;
        }
    }

    private void changeArgs(final Args newArgs, final boolean rerender) {
        args = newArgs;
        argsChangeHandler.accept(newArgs);
        // Re-render to show the change, unless typing, where it would lose the focus
        if (rerender) {
            render();
        } else {
            renderModifiedBar();
        }
    }
}
