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

import stroom.ai.api.CodeChangeStepper;
import stroom.ai.api.CodeChangeStepper.StepOutcome;
import stroom.ai.api.CodeChangeStepper.StepProblem;
import stroom.ai.api.CodeChangeStepper.StepTrial;
import stroom.ai.impl.TabSnapshots;
import stroom.ai.impl.TabSnapshots.StoredTabSnapshot;
import stroom.ai.impl.TabSnapshots.UnsavedPart;
import stroom.ai.impl.UserActions;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SafeXPath;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.shared.AiSteppingContext;
import stroom.ai.shared.AiSteppingElement;
import stroom.ai.shared.AiTabSnapshotPart;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.diff.LineDiff;
import stroom.util.shared.diff.LineDiffResult;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/// `propose_code_change` - offers the user a change to the code of the pipeline element selected in their
/// stepping screen (an XSLT or text converter), shown as a diff with a "Review and apply" button. It changes
/// nothing itself: the button opens a side by side review, from which the user can apply the change to the
/// element's editor as an unsaved edit (which needs Edit on the document, checked when they apply it) and undo it.
/// Nothing is ever saved.
///
/// The change is diffed against the element's code in the latest snapshot of the tab, unsaved edits included,
/// and is only applied while the editor still holds that code. A proposal that isn't well formed XML, or is the
/// same as the current code, is refused so the agent can correct it.
///
/// Before it is offered, the change is tried: the stepping screen's record is stepped through the pipeline with the
/// element's current code and then with the proposed code, as the user (Stroom's own stepping, so the pipeline's
/// later elements, such as a schema filter validating against the event-logging schema, run as they will). A
/// proposal giving errors the current code doesn't is refused, with the errors, so the agent can fix it, unless the
/// user has accepted them. If it can't be tried, it is offered with a note saying why.
class ProposeCodeChangeTool extends AbstractWorkspaceTool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProposeCodeChangeTool.class);

    static final String NAME = "propose_code_change";
    static final String ELEMENT_ARG = "element";
    static final String CODE_ARG = "code";
    static final String EDITS_ARG = "edits";
    static final String OLD_TEXT_ARG = "oldText";
    static final String NEW_TEXT_ARG = "newText";
    static final String SUMMARY_ARG = "summary";
    static final String ALLOW_ERRORS_ARG = "allowErrors";
    /// The most characters of the element's output with the proposed code given back.
    static final int MAX_OUTPUT_CHARS = 3_000;
    /// The most errors or warnings listed.
    static final int MAX_PROBLEMS = 20;
    static final int MAX_SUMMARY_CHARS = 500;
    /// The document types whose code can be proposed: both are XML.
    static final Set<String> CODE_TYPES = Set.of("XSLT", "TextConverter");

    static final String NOT_STEPPING = "No pipeline stepping screen is attached to the chat, so no change was "
                                       + "proposed. Ask the user to open the pipeline, step to the record, select "
                                       + "the element and attach the tab (+ by the chat's input), then ask again. "
                                       + "Meanwhile give the code as a code block to copy.";

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final TabSnapshots tabSnapshots;
    private final DocumentAccess documentAccess;
    private final AgentInstructions agentInstructions;
    private final Provider<CodeChangeStepper> codeChangeStepperProvider;

    @Inject
    ProposeCodeChangeTool(final ChatWorkspace workspace,
                          final TabSnapshots tabSnapshots,
                          final DocumentAccess documentAccess,
                          final AgentInstructions agentInstructions,
                          final Provider<CodeChangeStepper> codeChangeStepperProvider) {
        super(workspace);
        this.tabSnapshots = tabSnapshots;
        this.documentAccess = documentAccess;
        this.agentInstructions = agentInstructions;
        this.codeChangeStepperProvider = codeChangeStepperProvider;
    }

    @Override
    protected Duration timeLimit(final ToolCallContext context) {
        // Trying the change steps the record twice, which can take longer than a workspace step.
        return limits(context).getQueryTimeLimit().getDuration();
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW)
                .build();
    }

    @Override
    public boolean isChatOnly() {
        // It proposes a change to the tab the user attached to the chat.
        return true;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CONTENT;
    }

    @Override
    public ToolDefinition definition() {
        final ObjectSchema edit = ObjectSchema.builder()
                .addStringProperty(OLD_TEXT_ARG, "Text to replace, exactly as it is in the code, and appearing "
                                                 + "only once in it. A short unique part of a line is enough.")
                .addStringProperty(NEW_TEXT_ARG, "The text to replace it with.")
                .required(OLD_TEXT_ARG, NEW_TEXT_ARG)
                .build();
        return ToolDefinition.builder()
                .name(NAME)
                .description("While the user is stepping a pipeline, proposes a change to the code (XSLT or "
                             + "text converter) of the element selected in their stepping screen. The user is "
                             + "shown a diff with a 'Review and apply' button; nothing changes until they apply it "
                             + "to the element's editor, and it is never saved. Give either edits (best for small "
                             + "changes: only the text to replace and its replacement, so nothing else need be "
                             + "copied) or the whole revised code, changing only what is needed and keeping the "
                             + "rest exactly as it is. The result must be well formed XML. To change another "
                             + "element, ask the user to select it first. "
                             + "The change is tried first, by stepping the record with it: one that gives errors "
                             + "the current code doesn't (e.g. its output fails the pipeline's schema validation) "
                             + "is refused with them, so fix it and propose it again. The output it gave is "
                             + "returned, so show that rather than what you expect.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(ELEMENT_ARG, ToolDescription.builder()
                                .general("The element's id, as named in the pipeline")
                                .chatOnly(" (see the tab's structure file)")
                                .general(".")
                                .build())
                        .addStringProperty(CODE_ARG, "The whole proposed code. Give this or " + EDITS_ARG
                                                     + ", not both.")
                        .addProperty(EDITS_ARG, ArraySchema.builder()
                                .items(edit)
                                .description("Replacements made to the element's current code, in order. Give "
                                             + "this or " + CODE_ARG + ", not both.")
                                .build())
                        .addStringProperty(SUMMARY_ARG, "A sentence saying what the change does and why, shown "
                                                        + "with the diff.")
                        .addBooleanProperty(ALLOW_ERRORS_ARG, "Optional. True to offer the change though it gives "
                                                              + "new errors, only when the user has said to.")
                        .required(ELEMENT_ARG, SUMMARY_ARG)
                        .build())
                .build();
    }

    /// @param current  The element's code as it is.
    /// @param proposed The code the model proposed.
    /// @return The proposed code ending in the same line breaks as the current code (none, or e.g. one `\n`), so
    /// only the model's real changes are shown and applied.
    static String withLineEndingOf(final String current, final String proposed) {
        return stripLineBreaks(proposed) + current.substring(stripLineBreaks(current).length());
    }

    /// @param current The element's code as it is.
    /// @param edits   The model's edits argument: a list of objects each with the text to replace and its
    ///                replacement.
    /// @return The code with the edits made, in order, each to the code as the edits before it left it.
    /// @throws WorkspaceException if the edits aren't a list of replacements, or the text one replaces isn't in
    ///                            the code exactly once.
    static String applyEdits(final String current, final Object edits) {
        return applyEdits(current, edits, "the element's code");
    }

    /// @param current The text as it is.
    /// @param edits   The model's edits argument: a list of objects each with the text to replace and its
    ///                replacement.
    /// @param what    What the text is, for the errors, e.g. "the documentation".
    /// @return The text with the edits made, in order, each to the text as the edits before it left it.
    /// @throws WorkspaceException if the edits aren't a list of replacements, or the text one replaces isn't in
    ///                            the text exactly once.
    static String applyEdits(final String current, final Object edits, final String what) {
        if (!(edits instanceof final List<?> list) || list.isEmpty()) {
            throw new WorkspaceException("Give " + EDITS_ARG + " as a list of objects, each with " + OLD_TEXT_ARG
                                         + " and " + NEW_TEXT_ARG + ".");
        }
        String code = current;
        for (int i = 0; i < list.size(); i++) {
            final String which = "Edit " + (i + 1);
            if (!(list.get(i) instanceof final Map<?, ?> edit)
                || !(edit.get(OLD_TEXT_ARG) instanceof final String oldText)
                || !(edit.get(NEW_TEXT_ARG) instanceof final String newText)) {
                throw new WorkspaceException(which + " needs " + OLD_TEXT_ARG + " and " + NEW_TEXT_ARG + ".");
            }
            if (oldText.isEmpty()) {
                throw new WorkspaceException(which + " has no text to replace.");
            }
            final int index = code.indexOf(oldText);
            if (index < 0) {
                throw new WorkspaceException(which + "'s text to replace isn't in " + what
                                             + (i > 0
                        ? " (as the edits before it left it)"
                        : "") + ". Read it again and give text exactly as it is there.");
            }
            if (code.indexOf(oldText, index + 1) >= 0) {
                throw new WorkspaceException(which + "'s text to replace appears more than once in " + what
                                             + ". Give more of it, so it appears once.");
            }
            code = code.substring(0, index) + newText + code.substring(index + oldText.length());
        }
        return code;
    }

    private static String stripLineBreaks(final String text) {
        int end = text.length();
        while (end > 0 && (text.charAt(end - 1) == '\n' || text.charAt(end - 1) == '\r')) {
            end--;
        }
        return text.substring(0, end);
    }

    @Override
    protected ToolResult run(final ToolCall request, final ToolCallContext context) {
        final String elementId = stringArg(request, ELEMENT_ARG);
        final String given = rawArg(request, CODE_ARG);
        final Object edits = argsMap(request).get(EDITS_ARG);
        final String summary = stringArg(request, SUMMARY_ARG);
        if (elementId == null) {
            throw new WorkspaceException("Give the element's id, as named in the pipeline.");
        }
        final boolean hasCode = given != null && !given.isBlank();
        if (hasCode == (edits != null)) {
            throw new WorkspaceException("Give either " + EDITS_ARG + " or the whole proposed " + CODE_ARG
                                         + ", but not both.");
        }
        if (hasCode && given.length() > UserActions.MAX_CODE_CHARS) {
            throw new WorkspaceException("The code may be at most " + UserActions.MAX_CODE_CHARS + " characters.");
        }
        if (summary != null && summary.length() > MAX_SUMMARY_CHARS) {
            throw new WorkspaceException("The summary may be at most " + MAX_SUMMARY_CHARS + " characters.");
        }
        if (context.runContext().isEmpty()) {
            return ToolResult.text("Error: a button can only be offered while answering a question in a chat.");
        }

        // The tab the user attached to the chat.
        final int chatId = context.session().requireChatId(NAME);
        final StoredTabSnapshot tab = tabSnapshots.forLatestQuestion(chatId).orElse(null);
        final AiSteppingContext stepping = NullSafe.get(tab, StoredTabSnapshot::stepping);
        if (tab == null || stepping == null || tab.docRef() == null) {
            return ToolResult.text(NOT_STEPPING);
        }
        final AiSteppingElement element = stepping.findElement(elementId);
        if (element == null) {
            return ToolResult.text(
                    "The pipeline has no element '" + elementId + "'. Its elements are: "
                    + String.join(", ", stepping.getElements().stream().map(AiSteppingElement::getId).toList())
                    + ".");
        }
        if (!elementId.equals(stepping.getSelectedElementId())) {
            return ToolResult.text(
                    "Element '" + elementId + "' isn't selected in the user's stepping screen, so its code isn't "
                    + "shared. Ask the user to select it, then ask again.");
        }
        final DocRef docRef = element.getDocRef();
        if (docRef == null || !documentAccess.canView(docRef)) {
            return ToolResult.text(DocumentAccess.NO_PERMISSION);
        }
        if (!CODE_TYPES.contains(docRef.getType())) {
            return ToolResult.text(
                    "Only an XSLT's or a text converter's code can be proposed, and element '" + elementId
                    + "' uses a " + docRef.getType() + ". Give the change as a code block to copy.");
        }
        final UnsavedPart base = tabSnapshots.part(chatId, tab, AiTabSnapshotPart.STEP_CODE).orElse(null);
        if (base == null) {
            return ToolResult.text(
                    "The element's code isn't in the tab snapshot (it may still be loading). Ask the user to wait "
                    + "for it to load, then ask again.");
        }
        if (base.cut()) {
            return ToolResult.text(
                    "The element's code is too large to have been shared in full, so a change to it can't be "
                    + "proposed. Give the change as a code block to copy.");
        }

        // The model often drops or adds a final line break, which would show as a change of its own.
        final String code = hasCode
                ? withLineEndingOf(base.text(), given)
                : applyEdits(base.text(), edits);
        if (code.length() > UserActions.MAX_CODE_CHARS) {
            throw new WorkspaceException("The code may be at most " + UserActions.MAX_CODE_CHARS + " characters.");
        }
        final String problem = xmlProblem(code);
        if (problem != null) {
            return ToolResult.text("The proposed code isn't well formed XML, so no change was proposed: " + problem
                                   + "\nFix it and propose it again.");
        }
        final LineDiffResult diff = LineDiff.diff(base.text(), code);
        if (diff.isIdentical()) {
            return ToolResult.text(
                    "The proposed code is the same as the element's current code, so there is nothing to propose.");
        }
        final Trial trial = tryChange(tab, stepping, elementId, base.text(), code, context);
        if (!trial.introduced().isEmpty() && !booleanArg(request, ALLOW_ERRORS_ARG, false)) {
            return ToolResult.text(
                    "The change was tried by stepping " + trial.where() + " with it, and gives errors the current "
                    + "code doesn't, so it wasn't proposed:\n" + describe(trial.introduced())
                    + "\nFix them (check the elements you write against the schema the pipeline validates with) "
                    + "and propose it again. Only if the user says to offer it anyway, pass " + ALLOW_ERRORS_ARG
                    + "." + trial.outputNote());
        }

        final Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("doc", docRefMap(docRef));
        arguments.put("pipeline", docRefMap(tab.docRef()));
        arguments.put("element", elementId);
        arguments.put("request", code);
        arguments.put("base", base.text());
        arguments.put("base_hash", sha256(base.text()));
        NullSafe.consume(tab.hash(), hash -> arguments.put("snapshot_hash", hash));
        arguments.put("added", diff.getAddedLineCount());
        arguments.put("removed", diff.getRemovedLineCount());
        NullSafe.consume(summary, text -> arguments.put("summary", text));
        final ToolUserAction action = new ToolUserAction("apply-code-change-" + SEQUENCE.incrementAndGet(),
                UserActions.APPLY_CODE_CHANGE, arguments);
        documentAccess.logView(context.session(), docRef, "Proposed a change to its code, in element '" + elementId
                                               + "' while stepping, with the " + NAME + " tool");

        final String lines = "+" + diff.getAddedLineCount() + " −" + diff.getRemovedLineCount() + " lines";
        return ToolResult.text(
                "The user has been shown the change to " + docRef.getType() + " '" + docRef.getName()
                + "' in element '" + elementId + "' (" + lines + ", in " + diff.getChangedBlocks().size()
                + " place(s)) as a diff, with a 'Review and apply' button. Nothing changes until they apply it to "
                + "the element's editor, and it is never saved; they can undo it. Tell them about the button "
                + "rather than saying the code was changed, and show the output you expect once they apply it "
                + "and step.\nThe diff:\n" + diff.toUnifiedDiff(LineDiffResult.DEFAULT_CONTEXT_LINES)
                + trial.note() + instructionsNote(context, docRef, tab.docRef())).withUserAction(action);
    }

    /// Steps the stepping screen's record with the element's current code, then with the proposed code, and finds
    /// the errors only the proposed code gives.
    private Trial tryChange(final StoredTabSnapshot tab,
                            final AiSteppingContext stepping,
                            final String elementId,
                            final String current,
                            final String proposed,
                            final ToolCallContext context) {
        if (stepping.getMetaId() == null) {
            return Trial.notTried("the stepping screen's stream isn't known");
        }
        final long part = Math.max(0, NullSafe.getOrElse(stepping.getPartNo(), no -> no - 1, 0L));
        final long record = Math.max(0, NullSafe.getOrElse(stepping.getRecordNo(), no -> no - 1, 0L));
        final String where = "record " + (record + 1) + (part > 0
                ? " of part " + (part + 1)
                : "") + " of stream " + stepping.getMetaId();
        try {
            final List<StepOutcome> outcomes = codeChangeStepperProvider.get().step(new StepTrial(tab.docRef(),
                    stepping.getMetaId(), stepping.getChildStreamType(), part, record,
                    List.of(Map.of(elementId, current), Map.of(elementId, proposed)),
                    timeLimit(context).multipliedBy(3).dividedBy(4)));
            final StepOutcome before = outcomes.get(0);
            final StepOutcome after = outcomes.get(1);
            if (!after.foundRecord()) {
                return Trial.notTried("the record couldn't be found to step");
            }
            final Set<String> had = new HashSet<>();
            before.problems().forEach(problem -> had.add(key(problem)));
            final List<StepProblem> introduced = new ArrayList<>();
            final List<StepProblem> warnings = new ArrayList<>();
            for (final StepProblem problem : after.problems()) {
                if (had.contains(key(problem))) {
                    continue;
                }
                if (isError(problem)) {
                    introduced.add(problem);
                } else {
                    warnings.add(problem);
                }
            }
            final long fixed = before.problems().stream()
                    .filter(problem -> isError(problem))
                    .filter(problem -> after.problems().stream().noneMatch(other -> key(other).equals(key(problem))))
                    .count();
            return new Trial(true, where, null, introduced, warnings, (int) fixed,
                    after.outputs().get(elementId));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to try the change by stepping", e);
            return Trial.notTried(NullSafe.nonBlankStringElse(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    // Problems are the same if the same element reports the same thing; lines move with the change.
    private static String key(final StepProblem problem) {
        return problem.element() + "|" + problem.severity() + "|" + problem.message();
    }

    private static boolean isError(final StepProblem problem) {
        return "ERROR".equals(problem.severity()) || "FATAL_ERROR".equals(problem.severity());
    }

    private static String describe(final List<StepProblem> problems) {
        final StringBuilder sb = new StringBuilder();
        problems.stream().limit(MAX_PROBLEMS).forEach(problem -> {
            sb.append("- ").append(problem.severity()).append(" in ").append(problem.element());
            if (problem.line() > 0) {
                sb.append(" at line ").append(problem.line());
                if (problem.column() > 0) {
                    sb.append(", column ").append(problem.column());
                }
            }
            sb.append(": ").append(NullSafe.string(problem.message()).strip()).append('\n');
        });
        if (problems.size() > MAX_PROBLEMS) {
            sb.append("- … and ").append(problems.size() - MAX_PROBLEMS).append(" more\n");
        }
        return sb.toString().stripTrailing();
    }

    // The AGENTS docs that apply where the code and its pipeline are, e.g. XSLT style and how fields are mapped,
    // for the agent to check the change follows them.
    private String instructionsNote(final ToolCallContext context, final DocRef code, final DocRef pipeline) {
        final String note = agentInstructions.load(context.session(),
                "Standing instructions read by the " + NAME + " tool")
                .note(context.links(), List.of(agentInstructions.folderOf(code), agentInstructions.folderOf(pipeline)));
        return note.isEmpty()
                ? ""
                : note + "\n\nCheck the change follows these. If it doesn't, propose it again, fixed, and tell the "
                  + "user what changed.";
    }

    /// @param code Some code.
    /// @return Why it isn't well formed XML (with the line and column), or null if it is. The parser refuses a
    /// DOCTYPE and never loads anything from outside the code.
    static String xmlProblem(final String code) {
        try {
            final XMLReader reader = SafeXPath.secureReader();
            reader.setErrorHandler(null);
            reader.parse(new InputSource(new StringReader(code)));
            return null;
        } catch (final SAXParseException e) {
            return "line " + e.getLineNumber() + ", column " + e.getColumnNumber() + ": " + e.getMessage();
        } catch (final SAXException e) {
            return e.getMessage();
        } catch (final IOException e) {
            return "it could not be read: " + e.getMessage();
        }
    }

    /// @param text Some text.
    /// @return The SHA-256 of its UTF-8 bytes, as hex.
    static String sha256(final String text) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    static Map<String, Object> docRefMap(final DocRef docRef) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", docRef.getType());
        map.put("uuid", docRef.getUuid());
        return map;
    }


    // --------------------------------------------------------------------------------


    /// What trying a change by stepping found.
    ///
    /// @param tried      True if the record was stepped with it.
    /// @param where      The record stepped, e.g. "record 3 of stream 1234".
    /// @param whyNot     Why it couldn't be tried, or null.
    /// @param introduced The errors only the proposed code gives.
    /// @param warnings   The warnings only the proposed code gives.
    /// @param fixed      How many of the current code's errors the proposed code doesn't give.
    /// @param output     The element's output with the proposed code, or null.
    private record Trial(boolean tried,
                         String where,
                         String whyNot,
                         List<StepProblem> introduced,
                         List<StepProblem> warnings,
                         int fixed,
                         String output) {

        static Trial notTried(final String whyNot) {
            return new Trial(false, null, whyNot, List.of(), List.of(), 0, null);
        }

        /// @return What the agent is told about the trial, after the diff.
        String note() {
            if (!tried) {
                return "\n\nThe change couldn't be tried by stepping (" + whyNot + "), so check its output once "
                       + "the user applies it and steps.";
            }
            final StringBuilder sb = new StringBuilder("\n\nIt was tried by stepping ").append(where)
                    .append(introduced.isEmpty()
                            ? ": it gives no new errors"
                            : ": it gives new errors, which the user accepted:\n" + describe(introduced));
            if (fixed > 0) {
                sb.append(introduced.isEmpty()
                        ? ", and "
                        : "\nIt ").append("clears ").append(fixed).append(" error(s) the current code gives");
            }
            sb.append('.');
            if (!warnings.isEmpty()) {
                sb.append(" New warnings:\n").append(describe(warnings));
            }
            return sb + outputNote();
        }

        /// @return The element's output with the proposed code, cut short if long.
        String outputNote() {
            if (output == null) {
                return "";
            }
            final String shown = output.length() > MAX_OUTPUT_CHARS
                    ? output.substring(0, MAX_OUTPUT_CHARS) + "\n[cut short]"
                    : output;
            return "\nThe element's output with the proposed code, for this record:\n" + shown;
        }
    }
}
