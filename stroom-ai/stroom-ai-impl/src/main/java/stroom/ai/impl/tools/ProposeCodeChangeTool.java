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

import stroom.ai.impl.TabSnapshots;
import stroom.ai.impl.TabSnapshots.StoredTabSnapshot;
import stroom.ai.impl.TabSnapshots.UnsavedPart;
import stroom.ai.impl.UserActions;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SafeXPath;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.shared.AiSteppingContext;
import stroom.ai.shared.AiSteppingElement;
import stroom.ai.shared.AiTabSnapshotPart;
import stroom.docref.DocRef;
import stroom.util.shared.NullSafe;
import stroom.util.shared.diff.LineDiff;
import stroom.util.shared.diff.LineDiffResult;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import jakarta.inject.Inject;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
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
class ProposeCodeChangeTool extends AbstractWorkspaceTool {

    static final String NAME = "propose_code_change";
    static final String ELEMENT_ARG = "element";
    static final String CODE_ARG = "code";
    static final String SUMMARY_ARG = "summary";
    static final int MAX_SUMMARY_CHARS = 500;
    /// The document types whose code can be proposed: both are XML.
    static final Set<String> CODE_TYPES = Set.of("XSLT", "TextConverter");

    static final String NOT_STEPPING = "The user's active tab isn't a pipeline's stepping screen, so no change was "
                                       + "proposed. Ask the user to open the pipeline, step to the record and "
                                       + "select the element, then ask again. Meanwhile give the code as a code "
                                       + "block to copy.";

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final TabSnapshots tabSnapshots;
    private final DocumentAccess documentAccess;

    @Inject
    ProposeCodeChangeTool(final ChatWorkspace workspace,
                          final TabSnapshots tabSnapshots,
                          final DocumentAccess documentAccess) {
        super(workspace);
        this.tabSnapshots = tabSnapshots;
        this.documentAccess = documentAccess;
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(NAME)
                .description("While the user is stepping a pipeline, proposes a change to the code (XSLT or "
                             + "text converter) of the element selected in their stepping screen. The user is "
                             + "shown a diff with a 'Review and apply' button; nothing changes until they apply it "
                             + "to the element's editor, and it is never saved. Give the whole revised code, "
                             + "changing only what is needed and keeping the rest exactly as it is. It must be "
                             + "well formed XML. To change another element, ask the user to select it first.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty(ELEMENT_ARG, "The element's id, as named in the pipeline (see the "
                                                        + "tab's structure file).")
                        .addStringProperty(CODE_ARG, "The whole proposed code.")
                        .addStringProperty(SUMMARY_ARG, "A sentence saying what the change does and why, shown "
                                                        + "with the diff.")
                        .required(ELEMENT_ARG, CODE_ARG, SUMMARY_ARG)
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

    private static String stripLineBreaks(final String text) {
        int end = text.length();
        while (end > 0 && (text.charAt(end - 1) == '\n' || text.charAt(end - 1) == '\r')) {
            end--;
        }
        return text.substring(0, end);
    }

    @Override
    String run(final ToolExecutionRequest request, final int chatId) {
        final String elementId = stringArg(request, ELEMENT_ARG);
        final String given = rawArg(request, CODE_ARG);
        final String summary = stringArg(request, SUMMARY_ARG);
        if (elementId == null) {
            throw new WorkspaceException("Give the element's id, as named in the pipeline.");
        }
        if (given == null || given.isBlank()) {
            throw new WorkspaceException("Give the whole proposed code.");
        }
        if (given.length() > UserActions.MAX_CODE_CHARS) {
            throw new WorkspaceException("The code may be at most " + UserActions.MAX_CODE_CHARS + " characters.");
        }
        if (summary != null && summary.length() > MAX_SUMMARY_CHARS) {
            throw new WorkspaceException("The summary may be at most " + MAX_SUMMARY_CHARS + " characters.");
        }
        final ToolRunContext context = ToolRunContext.current().orElse(null);
        if (context == null) {
            return "Error: a button can only be offered while answering a question in a chat.";
        }

        final StoredTabSnapshot tab = tabSnapshots.forLatestQuestion(chatId).orElse(null);
        final AiSteppingContext stepping = NullSafe.get(tab, StoredTabSnapshot::stepping);
        if (tab == null || stepping == null || tab.docRef() == null) {
            return NOT_STEPPING;
        }
        final AiSteppingElement element = stepping.findElement(elementId);
        if (element == null) {
            return "The pipeline has no element '" + elementId + "'. Its elements are: "
                   + String.join(", ", stepping.getElements().stream().map(AiSteppingElement::getId).toList())
                   + ".";
        }
        if (!elementId.equals(stepping.getSelectedElementId())) {
            return "Element '" + elementId + "' isn't selected in the user's stepping screen, so its code isn't "
                   + "shared. Ask the user to select it, then ask again.";
        }
        final DocRef docRef = element.getDocRef();
        if (docRef == null || !documentAccess.canView(docRef)) {
            return DocumentAccess.NO_PERMISSION;
        }
        if (!CODE_TYPES.contains(docRef.getType())) {
            return "Only an XSLT's or a text converter's code can be proposed, and element '" + elementId
                   + "' uses a " + docRef.getType() + ". Give the change as a code block to copy.";
        }
        final UnsavedPart base = tabSnapshots.part(chatId, tab, AiTabSnapshotPart.STEP_CODE).orElse(null);
        if (base == null) {
            return "The element's code isn't in the tab snapshot (it may still be loading). Ask the user to wait "
                   + "for it to load, then ask again.";
        }
        if (base.cut()) {
            return "The element's code is too large to have been shared in full, so a change to it can't be "
                   + "proposed. Give the change as a code block to copy.";
        }

        // The model often drops or adds a final line break, which would show as a change of its own.
        final String code = withLineEndingOf(base.text(), given);
        final String problem = xmlProblem(code);
        if (problem != null) {
            return "The proposed code isn't well formed XML, so no change was proposed: " + problem
                   + "\nFix it and propose it again.";
        }
        final LineDiffResult diff = LineDiff.diff(base.text(), code);
        if (diff.isIdentical()) {
            return "The proposed code is the same as the element's current code, so there is nothing to propose.";
        }

        final Map<String, Object> action = new LinkedHashMap<>();
        action.put("id", "apply-code-change-" + SEQUENCE.incrementAndGet());
        action.put("action", UserActions.APPLY_CODE_CHANGE);
        action.put("doc", docRefMap(docRef));
        action.put("pipeline", docRefMap(tab.docRef()));
        action.put("element", elementId);
        action.put("request", code);
        action.put("base", base.text());
        action.put("base_hash", sha256(base.text()));
        NullSafe.consume(tab.hash(), hash -> action.put("snapshot_hash", hash));
        action.put("added", diff.getAddedLineCount());
        action.put("removed", diff.getRemovedLineCount());
        NullSafe.consume(summary, text -> action.put("summary", text));
        context.offerUserAction(request.id(), action);
        documentAccess.logView(chatId, docRef, "Proposed a change to its code, in element '" + elementId
                                               + "' while stepping, with the " + NAME + " tool");

        final String lines = "+" + diff.getAddedLineCount() + " −" + diff.getRemovedLineCount() + " lines";
        return "The user has been shown the change to " + docRef.getType() + " '" + docRef.getName()
               + "' in element '" + elementId + "' (" + lines + ", in " + diff.getChangedBlocks().size()
               + " place(s)) as a diff, with a 'Review and apply' button. Nothing changes until they apply it to "
               + "the element's editor, and it is never saved; they can undo it. Tell them about the button "
               + "rather than saying the code was changed, and show the output you expect once they apply it "
               + "and step.\nThe diff:\n" + diff.toUnifiedDiff(LineDiffResult.DEFAULT_CONTEXT_LINES);
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

    private static Map<String, Object> docRefMap(final DocRef docRef) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", docRef.getType());
        map.put("uuid", docRef.getUuid());
        return map;
    }
}
