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

package stroom.ai.impl.tools;

import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.ToolSchema.StringSchema;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Lets the agent ask the user the questions it needs answered to carry on, rather than listing them at the end of
/// its answer and stopping: the chat shows them one at a time, each with answers to click (the recommended one
/// first) and, unless the agent says otherwise, a box for the user's own answer. The run waits for the answers,
/// which are the tool's result. It changes nothing in Stroom.
class AskUserTool extends AbstractBuiltInTool {

    static final String NAME = "ask_user";
    static final String INTRO_ARG = "intro";
    static final String QUESTIONS_ARG = "questions";
    static final String QUESTION = "question";
    static final String OPTIONS = "options";
    static final String RECOMMENDED = "recommended";
    static final String ALLOW_CUSTOM = "allowCustom";
    // An option the model gives as an object rather than as text, e.g. {"label": "Create", "description": "..."}.
    static final String OPTION_LABEL = "label";
    static final String OPTION_DESCRIPTION = "description";
    // Not part of an MCP form's schema: what each option means, shown under it. Only Stroom AI's chat reads it.
    static final String OPTION_DESCRIPTIONS = "optionDescriptions";
    private static final List<String> OPTION_LABEL_KEYS = List.of(OPTION_LABEL, "value", "title", "name");
    static final int MAX_QUESTIONS = 8;
    static final int MAX_OPTIONS = 6;
    static final int MAX_QUESTION_CHARS = 500;
    static final int MAX_OPTION_CHARS = 200;
    private static final int MAX_ANSWER_CHARS = 2_000;
    private static final String DEFAULT_INTRO = "Some questions before I carry on";

    @Inject
    AskUserTool() {
    }

    @Override
    public ToolDefinition definition() {
        final ObjectSchema question = ObjectSchema.builder()
                .addStringProperty(QUESTION, "The question, on its own, e.g. 'Which environment is it?'.")
                .addProperty(OPTIONS, ArraySchema.builder()
                        .items(StringSchema.builder().build())
                        .description("Sensible answers to choose from (2 to " + MAX_OPTIONS + "), each short, "
                                     + "the best first.")
                        .build())
                .addStringProperty(RECOMMENDED, "The answer you recommend: one of the options.")
                .addBooleanProperty(ALLOW_CUSTOM, "True (the default) if the user may type their own answer; "
                                                  + "false if only the options make sense.")
                .required(QUESTION, OPTIONS)
                .build();
        return ToolDefinition.builder()
                .name(NAME)
                .description("Asks the user the questions you need answered before you carry on, and waits for "
                             + "the answers. The user sees them one at a time, with your options to click and "
                             + "a box for their own answer. Use it rather than listing questions in your answer "
                             + "whenever you need decisions to continue. Ask only what you can't sensibly decide "
                             + "yourself, at most " + MAX_QUESTIONS + " at a time.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(INTRO_ARG, "What the questions are for, in a line, e.g. 'A few "
                                                      + "decisions before I save the translation'.")
                        .addProperty(QUESTIONS_ARG, ArraySchema.builder().items(question)
                                .description("The questions, in the order to ask them.").build())
                        .required(QUESTIONS_ARG)
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall request, final ToolCallContext context) {
        final List<Question> questions;
        try {
            questions = questions(request.arguments());
        } catch (final IllegalArgumentException e) {
            return ToolResult.text("Error: " + e.getMessage());
        }
        if (!context.userQuestions().canAsk()) {
            return ToolResult.text(
                    "Not asked: the user isn't watching this chat. Put the questions in your answer instead, with "
                    + "what you recommend.");
        }
        final String intro = NullSafe.nonBlankStringElse(NullSafe.trim(stringArg(request, INTRO_ARG)),
                DEFAULT_INTRO);
        final ElicitResult result = context.userQuestions().ask(NAME, intro, form(questions));
        final String text = switch (result.action()) {
            case ACCEPT -> answers(questions, NullSafe.map(result.content()));
            case DECLINE -> "The user chose not to answer these questions. Don't ask them again now: carry on with "
                            + "what you recommended if you can, saying what you assumed, or stop and say what "
                            + "you need.";
            case CANCEL -> "The questions were not answered (the user stopped the chat, sent a message instead, which "
                           + "follows if so, or didn't answer in time). "
                           + "Don't carry on as if they had.";
        };
        return ToolResult.text(text);
    }

    @Override
    public boolean readsUntrustedContent() {
        // What the user answers.
        return false;
    }

    /// @return The questions in the arguments, checked.
    /// @throws IllegalArgumentException if they aren't a list of questions, each with options.
    static List<Question> questions(final String arguments) {
        final Object given;
        try {
            given = JsonUtil.readValue(arguments, Map.class).get(QUESTIONS_ARG);
        } catch (final RuntimeException e) {
            throw new IllegalArgumentException("Give the questions as a list.");
        }
        if (!(given instanceof final List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("Give the questions as a list of at least one.");
        }
        if (list.size() > MAX_QUESTIONS) {
            throw new IllegalArgumentException("Ask at most " + MAX_QUESTIONS + " questions at a time.");
        }
        final List<Question> questions = new ArrayList<>();
        for (final Object item : list) {
            if (!(item instanceof final Map<?, ?> map) || !(map.get(QUESTION) instanceof final String text)
                || text.isBlank()) {
                throw new IllegalArgumentException("Each question needs its question.");
            }
            if (text.length() > MAX_QUESTION_CHARS) {
                throw new IllegalArgumentException("A question may be at most " + MAX_QUESTION_CHARS
                                                   + " characters.");
            }
            final List<String> options = new ArrayList<>();
            final List<String> descriptions = new ArrayList<>();
            if (map.get(OPTIONS) instanceof final List<?> given2) {
                for (final Object option : given2) {
                    final String value = shorten(optionLabel(option));
                    if (!value.isEmpty() && !options.contains(value)) {
                        options.add(value);
                        descriptions.add(shorten(optionDescription(option)));
                    }
                }
            }
            final boolean allowCustom = !Boolean.FALSE.equals(map.get(ALLOW_CUSTOM));
            if (options.isEmpty() && !allowCustom) {
                throw new IllegalArgumentException("A question the user can't answer in their own words needs "
                                                   + "options.");
            }
            if (options.size() > MAX_OPTIONS) {
                throw new IllegalArgumentException("Give at most " + MAX_OPTIONS + " options for a question.");
            }
            final String recommended = map.get(RECOMMENDED) instanceof final String value
                                       && options.contains(value.strip())
                    ? value.strip()
                    : null;
            questions.add(new Question(text.strip(), options, descriptions, recommended, allowCustom));
        }
        return questions;
    }

    /// @return An option's text. Models are asked for text, but some give an object with a label and a
    /// description, as an MCP form's titled choices have.
    private static String optionLabel(final Object option) {
        if (option instanceof final Map<?, ?> map) {
            for (final String key : OPTION_LABEL_KEYS) {
                if (map.get(key) instanceof final String label && !label.isBlank()) {
                    return label;
                }
            }
            return "";
        }
        return option == null
                ? ""
                : String.valueOf(option);
    }

    /// @return What an option given as an object says it means, or an empty string.
    private static String optionDescription(final Object option) {
        return option instanceof final Map<?, ?> map && map.get(OPTION_DESCRIPTION) instanceof final String text
                ? text
                : "";
    }

    private static String shorten(final String text) {
        final String value = NullSafe.trim(text);
        return value.length() > MAX_OPTION_CHARS
                ? value.substring(0, MAX_OPTION_CHARS)
                : value;
    }

    /// @return The questions as a form: a text field per question, whose suggested answers are its examples (or
    /// the only answers allowed, if the user can't give their own), with the recommended one as its default.
    static Map<String, Object> form(final List<Question> questions) {
        final Map<String, Object> properties = new LinkedHashMap<>();
        for (int i = 0; i < questions.size(); i++) {
            final Question question = questions.get(i);
            final Map<String, Object> field = new LinkedHashMap<>();
            field.put("type", "string");
            field.put("title", question.text());
            if (!question.options().isEmpty()) {
                field.put(question.allowCustom()
                        ? "examples"
                        : "enum", question.options());
                if (question.optionDescriptions().stream().anyMatch(description -> !description.isEmpty())) {
                    field.put(OPTION_DESCRIPTIONS, question.optionDescriptions());
                }
            }
            if (question.recommended() != null) {
                field.put("default", question.recommended());
            }
            field.put("maxLength", MAX_ANSWER_CHARS);
            properties.put(fieldName(i), field);
        }
        final Map<String, Object> form = new LinkedHashMap<>();
        form.put("type", "object");
        form.put("properties", properties);
        form.put("required", new ArrayList<>(properties.keySet()));
        return form;
    }

    /// @return The answers, as the model is given them.
    static String answers(final List<Question> questions, final Map<String, Object> content) {
        final StringBuilder sb = new StringBuilder("The user answered:");
        for (int i = 0; i < questions.size(); i++) {
            final Question question = questions.get(i);
            final String answer = NullSafe.trim(content.get(fieldName(i)) == null
                    ? null
                    : String.valueOf(content.get(fieldName(i))));
            sb.append("\n- ").append(question.text()).append("\n  ");
            if (answer.isEmpty()) {
                sb.append("(no answer)");
            } else {
                sb.append(answer);
                if (!question.options().contains(answer)) {
                    sb.append(" (in their own words)");
                }
            }
        }
        return sb.toString();
    }

    private static String fieldName(final int index) {
        return "q" + (index + 1);
    }


    // --------------------------------------------------------------------------------


    /// A question for the user.
    ///
    /// @param text        The question.
    /// @param options            Answers to choose from.
    /// @param optionDescriptions What each answer means, an empty string for one the agent didn't explain.
    /// @param recommended        The answer the agent recommends, or null.
    /// @param allowCustom        True if the user may give their own answer.
    record Question(String text,
                    List<String> options,
                    List<String> optionDescriptions,
                    String recommended,
                    boolean allowCustom) {

    }
}
