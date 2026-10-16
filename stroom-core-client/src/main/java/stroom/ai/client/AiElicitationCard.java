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

import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.dom.client.SelectElement;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

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

    private static final SafeHtml INPUT = SafeHtmlUtils.fromSafeConstant("input");
    private static final SafeHtml SELECT = SafeHtmlUtils.fromSafeConstant("select");
    private static final SafeHtml OPTION = SafeHtmlUtils.fromSafeConstant("option");
    private static final SafeHtml LABEL = SafeHtmlUtils.fromSafeConstant("label");
    private static final String TEXT = "text";
    private static final String CHOICE = "choice";
    private static final String FLAG = "flag";
    private static final String SEVERAL = "several";

    private AiElicitationCard() {
        // Static only.
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
            for (int i = 0; i < field.getValues().size(); i++) {
                checkBox(row, field.getLabels().get(i), field, SEVERAL, field.getValues().get(i));
            }
        } else if (flag) {
            checkBox(row, field.getTitle() + required, field, FLAG, "true");
        } else if (!field.getValues().isEmpty()) {
            row.elem(select -> {
                if (!field.isRequired()) {
                    select.elem("", OPTION, new Attribute("value", ""));
                }
                for (int i = 0; i < field.getValues().size(); i++) {
                    select.elem(field.getLabels().get(i), OPTION, new Attribute("value", field.getValues().get(i)));
                }
            }, SELECT, Attribute.className("allow-focus ai-elicitation-input"),
                    new Attribute(FIELD_ATTRIBUTE, field.getName()),
                    new Attribute(KIND_ATTRIBUTE, CHOICE));
        } else {
            row.elem(INPUT, Attribute.className("allow-focus ai-elicitation-input"),
                    new Attribute("type", "text"),
                    new Attribute("placeholder", placeholder(field.getFormat())),
                    new Attribute(FIELD_ATTRIBUTE, field.getName()),
                    new Attribute(KIND_ATTRIBUTE, TEXT));
        }
    }

    private static void checkBox(final HtmlBuilder row,
                                 final String label,
                                 final AiElicitationForm.Field field,
                                 final String kind,
                                 final String value) {
        // The label holds its box, so clicking the text ticks it.
        row.elem(box -> {
            box.elem(INPUT, Attribute.className("allow-focus"),
                    new Attribute("type", "checkbox"),
                    new Attribute("value", value),
                    new Attribute(FIELD_ATTRIBUTE, field.getName()),
                    new Attribute(KIND_ATTRIBUTE, kind));
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
