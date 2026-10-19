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

package stroom.ai.client;

import stroom.util.shared.NullSafe;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.dom.client.SelectElement;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONBoolean;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// The fields of a form an MCP tool asked the user to fill in (MCP elicitation), shown in the chat's card for it:
/// a text box for text and numbers, a list for a choice of one, and tick boxes for true/false and for a choice of
/// several. Everything the server wrote is shown as text, never as HTML.
final class AiElicitationCard {

    /// On each input: the name of the field it is for.
    static final String FIELD_ATTRIBUTE = "data-form-field";
    /// On each input: how to read it (text, choice, flag or several).
    static final String KIND_ATTRIBUTE = "data-form-kind";
    /// On each answer to click: the answer.
    static final String OPTION_ATTRIBUTE = "data-question-option";
    /// On each Back button.
    static final String BACK_ATTRIBUTE = "data-question-back";
    /// The class of a question.
    static final String QUESTION_CLASS = "ai-question";
    /// The class of the box for the user's own answer.
    static final String CUSTOM_ANSWER_CLASS = "ai-question-custom";
    /// The class of a question not being shown.
    static final String LATER_CLASS = "ai-question--later";

    private static final SafeHtml INPUT = SafeHtmlUtils.fromSafeConstant("input");
    private static final SafeHtml SELECT = SafeHtmlUtils.fromSafeConstant("select");
    private static final SafeHtml OPTION = SafeHtmlUtils.fromSafeConstant("option");
    private static final SafeHtml LABEL = SafeHtmlUtils.fromSafeConstant("label");
    private static final SafeHtml BUTTON = SafeHtmlUtils.fromSafeConstant("button");
    private static final String TEXT = "text";
    private static final String CHOICE = "choice";
    private static final String FLAG = "flag";
    private static final String SEVERAL = "several";

    private AiElicitationCard() {
        // Static only.
    }

    /// @param fields A form's fields, from [AiElicitationForm#fields(String)].
    /// @return The form's one field if it is a single yes or no question, to answer with Yes and No buttons rather
    /// than a form, or null if the form has other fields.
    static AiElicitationForm.Field yesNoQuestion(final List<AiElicitationForm.Field> fields) {
        if (fields.size() != 1) {
            return null;
        }
        final AiElicitationForm.Field field = fields.get(0);
        return "boolean".equals(field.getType()) && !field.isMulti()
                ? field
                : null;
    }

    /// Adds what a yes or no question says beyond the form's message: its title, if it isn't the message's first
    /// line, and its description.
    ///
    /// @param hb       Where to add it.
    /// @param field    The question, from [#yesNoQuestion(List)].
    /// @param message  The form's message, already shown.
    static void appendYesNoQuestion(final HtmlBuilder hb,
                                    final AiElicitationForm.Field field,
                                    final String message) {
        if (field.getTitle() != null && !field.getTitle().equals(firstLine(message, null))) {
            hb.div(field.getTitle(), Attribute.className("ai-elicitation-label"));
        }
        if (field.getDescription() != null) {
            hb.div(field.getDescription(), Attribute.className("ai-elicitation-description"));
        }
    }

    /// Adds the form's fields.
    ///
    /// @param hb     Where to add them.
    /// @param fields The form's fields, from [AiElicitationForm#fields(String)].
    static void appendFields(final HtmlBuilder hb, final List<AiElicitationForm.Field> fields) {
        if (fields.isEmpty()) {
            return;
        }
        hb.div(form -> {
            for (final AiElicitationForm.Field field : fields) {
                form.div(row -> appendField(row, field), Attribute.className("ai-elicitation-field"));
            }
        }, Attribute.className("ai-elicitation-form"));
    }

    private static void appendField(final HtmlBuilder row, final AiElicitationForm.Field field) {
        final String required = field.isRequired()
                ? " *"
                : "";
        final boolean flag = "boolean".equals(field.getType()) && !field.isMulti();
        // A tick box has its title as its own label.
        if (!flag) {
            row.div(field.getTitle() + required, Attribute.className("ai-elicitation-label"));
        }
        if (field.getDescription() != null) {
            row.div(field.getDescription(), Attribute.className("ai-elicitation-description"));
        }
        if (field.isMulti()) {
            for (final int i : recommendedFirst(field.getValues(), field.getDefaults())) {
                checkBox(row, field.getLabels().get(i), field, SEVERAL, field.getValues().get(i),
                        field.getDefaults().contains(field.getValues().get(i)));
            }
        } else if (flag) {
            checkBox(row, field.getTitle() + required, field, FLAG, "true", "true".equals(field.getDefault()));
        } else if (!field.getValues().isEmpty() && !field.isCustom()) {
            row.elem(select -> {
                if (!field.isRequired()) {
                    select.elem("", OPTION, new Attribute("value", ""));
                }
                for (final int i : recommendedFirst(field.getValues(), field.getDefaults())) {
                    final String value = field.getValues().get(i);
                    if (value.equals(field.getDefault())) {
                        select.elem(field.getLabels().get(i), OPTION, new Attribute("value", value),
                                new Attribute("selected", "selected"));
                    } else {
                        select.elem(field.getLabels().get(i), OPTION, new Attribute("value", value));
                    }
                }
            }, SELECT, Attribute.className("allow-focus ai-elicitation-input"),
                    new Attribute(FIELD_ATTRIBUTE, field.getName()),
                    new Attribute(KIND_ATTRIBUTE, CHOICE));
        } else {
            row.elem(INPUT, Attribute.className("allow-focus ai-elicitation-input"),
                    new Attribute("type", "text"),
                    new Attribute("value", NullSafe.string(field.getDefault())),
                    new Attribute("placeholder", placeholder(field.getFormat())),
                    new Attribute(FIELD_ATTRIBUTE, field.getName()),
                    new Attribute(KIND_ATTRIBUTE, TEXT));
        }
    }

    private static void checkBox(final HtmlBuilder row,
                                 final String label,
                                 final AiElicitationForm.Field field,
                                 final String kind,
                                 final String value,
                                 final boolean checked) {
        // The label holds its box, so clicking the text ticks it.
        row.elem(box -> {
            final List<Attribute> attributes = new ArrayList<>(List.of(Attribute.className("allow-focus"),
                    new Attribute("type", "checkbox"),
                    new Attribute("value", value),
                    new Attribute(FIELD_ATTRIBUTE, field.getName()),
                    new Attribute(KIND_ATTRIBUTE, kind)));
            if (checked) {
                attributes.add(new Attribute("checked", "checked"));
            }
            box.elem(INPUT, attributes.toArray(new Attribute[0]));
            box.span(label);
        }, LABEL, Attribute.className("ai-elicitation-check"));
    }

    private static String placeholder(final String format) {
        if ("date".equals(format)) {
            return "YYYY-MM-DD";
        } else if ("date-time".equals(format)) {
            return "YYYY-MM-DDThh:mm:ssZ";
        } else if ("email".equals(format)) {
            return "name@example.com";
        }
        return "";
    }

    /// Adds questions from Stroom AI's agent (its ask_user tool), shown one at a time: each with its answers to
    /// click (the recommended one marked), a box for the user's own answer if they may give one, and Back.
    /// Choosing an answer moves on to the next question, and after the last, sends the answers.
    ///
    /// @param hb     Where to add them.
    /// @param fields The questions, as the form's fields.
    static void appendQuestions(final HtmlBuilder hb, final List<AiElicitationForm.Field> fields) {
        hb.div(all -> {
            for (int i = 0; i < fields.size(); i++) {
                final AiElicitationForm.Field field = fields.get(i);
                final int number = i + 1;
                final boolean first = i == 0;
                all.div(question -> {
                    if (fields.size() > 1) {
                        question.div("Question " + number + " of " + fields.size(),
                                Attribute.className("ai-question-progress"));
                    }
                    question.div(field.getTitle(), Attribute.className("ai-question-text"));
                    question.div(options -> {
                        for (final int j : recommendedFirst(field.getValues(), field.getDefaults())) {
                            final String value = field.getValues().get(j);
                            final boolean recommended = value.equals(field.getDefault());
                            final String label = field.getLabels().get(j);
                            final String description = field.getOptionDescription(j);
                            options.elem(option -> {
                                if (description != null) {
                                    // What the answer means, under it.
                                    option.span(text -> {
                                        text.span(label, Attribute.className("ai-question-option-label"));
                                        text.span(description, Attribute.className("ai-question-option-description"));
                                    }, Attribute.className("ai-question-option-text"));
                                } else {
                                    option.span(label);
                                }
                                if (recommended) {
                                    option.span("Recommended", Attribute.className("ai-question-recommended"));
                                }
                            }, BUTTON, Attribute.className("allow-focus ai-question-option"
                                                           + (recommended
                                    ? " ai-question-option--recommended"
                                    : "")),
                                    new Attribute(OPTION_ATTRIBUTE, value));
                        }
                    }, Attribute.className("ai-question-options"));
                    if (field.isCustom() || field.getValues().isEmpty()) {
                        question.elem(INPUT, Attribute.className("allow-focus ai-elicitation-input "
                                                                 + CUSTOM_ANSWER_CLASS),
                                new Attribute("type", "text"),
                                new Attribute("placeholder", field.getValues().isEmpty()
                                        ? "Type your answer, then press Enter"
                                        : "Or type your own answer, then press Enter"));
                    }
                    if (!first) {
                        // On its own line under the answers, not beside the typed answer.
                        question.div(nav -> nav.elem("Back", BUTTON,
                                        Attribute.className("allow-focus ai-question-back"),
                                        new Attribute(BACK_ATTRIBUTE, "true")),
                                Attribute.className("ai-question-nav"));
                    }
                    // Holds the answer given, which is what is sent.
                    question.elem(INPUT, new Attribute("type", "hidden"),
                            new Attribute(FIELD_ATTRIBUTE, field.getName()),
                            new Attribute(KIND_ATTRIBUTE, TEXT));
                }, Attribute.className("ai-question" + (first
                        ? ""
                        : " ai-question--later")));
            }
        }, Attribute.className("ai-questions"));
    }

    /// @param values      A field's options.
    /// @param recommended The options recommended (the field's default, or defaults).
    /// @return The positions of the options in the order to show them: those recommended first, then the rest, each
    /// in the order given.
    static List<Integer> recommendedFirst(final List<String> values, final List<String> recommended) {
        final List<Integer> order = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            if (recommended.contains(values.get(i))) {
                order.add(i);
            }
        }
        for (int i = 0; i < values.size(); i++) {
            if (!recommended.contains(values.get(i))) {
                order.add(i);
            }
        }
        return order;
    }

    /// @param message  What the server said.
    /// @param fallback What to use if it says nothing.
    /// @return Its first line with text in it, without Markdown heading or emphasis marks, e.g. "Records to
    /// translate as Unknown events" from `**Records to translate as Unknown events**`.
    static String firstLine(final String message, final String fallback) {
        if (message != null) {
            for (final String line : message.split("\n")) {
                final String text = line.trim()
                        .replaceAll("^#+\\s*", "")
                        .replaceAll("^[*_]+|[*_]+$", "")
                        .trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return fallback;
    }

    /// Adds what the user answered: each field they gave a value, by its title, with a choice shown by its label
    /// and true/false as Yes/No.
    ///
    /// @param hb        Where to add it.
    /// @param json      An answered ELICITATION message, with its answer as `content`.
    /// @param cardTitle The title shown on the card, or null. A single field that asks the same isn't repeated.
    /// @return True if there was an answer to show.
    static boolean appendAnswer(final HtmlBuilder hb, final String json, final String cardTitle) {
        final List<String[]> rows = answerRows(json);
        if (rows.isEmpty()) {
            return false;
        }
        hb.div(answer -> {
            if (rows.size() == 1 && isCardTitle(rows.get(0)[0], cardTitle)) {
                // The card's title already asks the question, so just the answer.
                answer.div(rows.get(0)[1], Attribute.className("ai-elicitation-answer-value"));
                return;
            }
            for (final String[] row : rows) {
                if (row[0].endsWith("?")) {
                    // A question: its answer below it.
                    answer.div(row[0], Attribute.className("ai-elicitation-answer-title"));
                    answer.div(row[1], Attribute.className("ai-elicitation-answer-value"));
                } else {
                    answer.div(line -> {
                        line.span(row[0] + ": ", Attribute.className("ai-elicitation-answer-title"));
                        line.span(row[1]);
                    });
                }
            }
        }, Attribute.className("ai-elicitation-answer"));
        return true;
    }

    /// @param fieldTitle A field's title.
    /// @param cardTitle  The title shown on the card, or null if there is none.
    /// @return True if the field's title says the same as the card's, ignoring Markdown marks and spaces, so
    /// showing it again would repeat it.
    static boolean isCardTitle(final String fieldTitle, final String cardTitle) {
        return cardTitle != null && cardTitle.equals(firstLine(fieldTitle, null));
    }

    /// @param json     An answered ELICITATION message, with its answer as `content`.
    /// @param question What the form asks, for a field without a title of its own.
    /// @return Each question the user answered and their answer, a line each, e.g. "Question: Which schema
    /// version?" then "Answer: 4.1.0", or null if there is no answer to show.
    static String answerText(final String json, final String question) {
        final List<String> lines = new ArrayList<>();
        for (final String[] row : answerRows(json)) {
            lines.add(AiChatOverview.QUESTION_LABEL + (NullSafe.isNonBlankString(row[0])
                    ? row[0]
                    : question));
            lines.add(AiChatOverview.ANSWER_LABEL + row[1]);
        }
        return lines.isEmpty()
                ? null
                : String.join("\n", lines);
    }

    // Each field the user gave a value, as its title and the value shown.
    private static List<String[]> answerRows(final String json) {
        final List<String[]> rows = new ArrayList<>();
        final JSONObject content;
        try {
            final JSONValue value = JSONParser.parseStrict(json).isObject().get("content");
            content = value != null
                    ? value.isObject()
                    : null;
        } catch (final RuntimeException e) {
            return rows;
        }
        if (content == null) {
            return rows;
        }
        for (final AiElicitationForm.Field field : AiElicitationForm.fields(json)) {
            final String shown = shown(field, content.get(field.getName()));
            if (shown != null) {
                rows.add(new String[]{field.getTitle(), shown});
            }
        }
        return rows;
    }

    private static String shown(final AiElicitationForm.Field field, final JSONValue value) {
        if (value == null || value.isNull() != null) {
            return null;
        }
        if (value.isBoolean() != null) {
            return value.isBoolean().booleanValue()
                    ? "Yes"
                    : "No";
        }
        if (value.isArray() != null) {
            final List<String> labels = new ArrayList<>();
            for (int i = 0; i < value.isArray().size(); i++) {
                labels.add(label(field, text(value.isArray().get(i))));
            }
            return labels.isEmpty()
                    ? null
                    : String.join(", ", labels);
        }
        final String text = text(value);
        return text.isEmpty()
                ? null
                : label(field, text);
    }

    private static String text(final JSONValue value) {
        return value.isString() != null
                ? value.isString().stringValue()
                : value.toString();
    }

    private static String label(final AiElicitationForm.Field field, final String value) {
        final int index = field.getValues().indexOf(value);
        return index >= 0
                ? field.getLabels().get(index)
                : value;
    }

    /// @param json   A waiting ELICITATION message.
    /// @param status Its new status.
    /// @param values For an accepted form, what the user entered (from [#values(Element)]), or null.
    /// @return The message as answered, with the answer as `content`, so it can be shown before the server's
    /// copy is fetched.
    static String answered(final String json, final String status, final Map<String, String> values) {
        try {
            final JSONObject message = JSONParser.parseStrict(json).isObject();
            message.put("status", new JSONString(status));
            if (values != null) {
                final JSONObject content = new JSONObject();
                for (final AiElicitationForm.Field field : AiElicitationForm.fields(json)) {
                    final String value = values.get(field.getName());
                    if (value == null) {
                        continue;
                    }
                    if (field.isMulti()) {
                        content.put(field.getName(), JSONParser.parseStrict(value));
                    } else if ("boolean".equals(field.getType())) {
                        content.put(field.getName(), JSONBoolean.getInstance(Boolean.parseBoolean(value)));
                    } else {
                        content.put(field.getName(), new JSONString(value));
                    }
                }
                message.put("content", content);
            }
            return message.toString();
        } catch (final RuntimeException e) {
            return AiToolMessages.withStatus(json, status);
        }
    }

    /// Reads what the user has entered in a card's form.
    ///
    /// @param card The card's element.
    /// @return Each field's value as text, by name: a choice of several as a JSON list.
    static Map<String, String> values(final Element card) {
        final Map<String, String> values = new HashMap<>();
        final Map<String, JSONArray> several = new HashMap<>();
        final NodeList<Element> inputs = card.getElementsByTagName("input");
        for (int i = 0; i < inputs.getLength(); i++) {
            final InputElement input = InputElement.as(inputs.getItem(i));
            final String name = input.getAttribute(FIELD_ATTRIBUTE);
            final String kind = input.getAttribute(KIND_ATTRIBUTE);
            if (name == null || name.isEmpty()) {
                continue;
            }
            if (TEXT.equals(kind)) {
                values.put(name, input.getValue());
            } else if (FLAG.equals(kind)) {
                values.put(name, String.valueOf(input.isChecked()));
            } else if (SEVERAL.equals(kind)) {
                final JSONArray chosen = several.computeIfAbsent(name, k -> new JSONArray());
                if (input.isChecked()) {
                    chosen.set(chosen.size(), new JSONString(input.getValue()));
                }
            }
        }
        several.forEach((name, chosen) -> values.put(name, chosen.toString()));
        final NodeList<Element> selects = card.getElementsByTagName("select");
        for (int i = 0; i < selects.getLength(); i++) {
            final SelectElement select = SelectElement.as(selects.getItem(i));
            final String name = select.getAttribute(FIELD_ATTRIBUTE);
            if (name != null && !name.isEmpty()) {
                values.put(name, select.getValue());
            }
        }
        return values;
    }
}
