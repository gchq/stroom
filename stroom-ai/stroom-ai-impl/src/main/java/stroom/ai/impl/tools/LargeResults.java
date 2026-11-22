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

import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/// A tool's result too large for a client outside Stroom to take whole: saved whole to the session's workspace, the
/// result giving its large parts as outlines, and saying where the whole is and how to read a part of it.
///
/// A client such as Claude Code takes about 25,000 tokens of a tool's result and saves a result past some 40,000
/// characters to a file of its own, which the agent can't read through Stroom's tools: it then works blind (seen in
/// the Haiku evals of 2026-11-20: survey_feed results of 52,000 to 109,000 characters, write_documentation's of
/// 73,000 to 88,000, catalogue_event_types's of 61,000 to 93,000). Stroom AI's own chat keeps a large result in its
/// workspace itself, so gets every result whole; so does a session without a workspace, or one the file can't be
/// written to.
///
/// A tool says, in order, the steps that make its result smaller ([#outline], [#step]), each run only while the
/// result is still past the limit, and the parts the agent must act on, never cut ([#keep]): status, hint, next,
/// problems and refusals, forms and review ids, the documents saved. Past its steps, the parts not kept are cut
/// ([#cutTheRest]), each list and long text saying how much more the saved file holds.
public final class LargeResults {

    /// The most characters a result is given whole in to a client outside Stroom.
    public static final int WHOLE_RESULT_CHARS = 40_000;
    /// The key of a result given in outline that says where it is saved whole and how to read it.
    public static final String WHOLE_RESULT = "whole_result";
    /// The folder results too large to give whole are saved in, a folder a tool.
    public static final String FOLDER = "/outputs/";

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LargeResults.class);

    // A workspace read cuts a line past this.
    private static final int LINE_CHARS = AbstractWorkspaceTool.MAX_LINE_CHARS;
    // Room kept for the whole_result note itself.
    private static final int NOTE_ROOM = 1_200;
    // The most times the rest is cut, each by half.
    private static final int MOST_CUTS = 10;
    // Lists are cut to no fewer entries than this, texts to no fewer characters.
    private static final int FEWEST_ENTRIES = 3;
    private static final int FEWEST_CHARS = 300;
    private static final Pattern PLAIN = Pattern.compile("[^A-Za-z0-9._-]");
    // Always kept: what the agent must act on, and what the user chose (seen in chat 183: told it only in part, the
    // agent asked the user again).
    private static final Set<String> ALWAYS_KEPT = Set.of(WHOLE_RESULT, "status", "hint", "next", "ok", "done",
            "problems", "refused", "error", "review_id", "user_actions", "user_chose");

    private final String tool;
    private final String key;
    private final List<Step> steps = new ArrayList<>();
    private final Set<String> kept = new LinkedHashSet<>(ALWAYS_KEPT);
    private boolean cutsTheRest;
    private boolean textsAsLines = true;

    private LargeResults(final String tool, final String key) {
        this.tool = tool;
        this.key = key;
    }

    /// @param tool The tool whose result it is.
    /// @param key  What the result is of (its build, else a stable key such as the feed): its file's name.
    /// @return How a result of the tool is made small enough, its steps still to add.
    public static LargeResults of(final String tool, final String key) {
        return new LargeResults(tool, key);
    }

    /// @param keys The result's parts never cut: those the agent must act on (a part within a part by its dotted
    ///             path, `check.problems`).
    /// @return This.
    public LargeResults keep(final String... keys) {
        kept.addAll(List.of(keys));
        return this;
    }

    /// @param part     A part of the result.
    /// @param outliner Gives the part in outline.
    /// @return This, the part outlined next while the result is past the limit.
    public LargeResults outline(final String part, final Outliner outliner) {
        steps.add(new Step(part, (result, room) -> {
            final Object value = result.get(part);
            if (value == null) {
                return false;
            }
            final Object outlined = outliner.outline(value, room.forPart(length(value)));
            if (Objects.equals(outlined, value)) {
                return false;
            }
            result.put(part, outlined);
            return true;
        }));
        return this;
    }

    /// @param part What the step changes, said in the result's note.
    /// @param step Changes the result in place.
    /// @return This, the step run next while the result is past the limit.
    public LargeResults step(final String part, final ResultStep step) {
        steps.add(new Step(part, step));
        return this;
    }

    /// @return This, a long text saved whole as one JSON string, not as the list of its lines: for a result whose
    ///         texts are records to give back as they are (fetch_events'), not documents to read a line at a time.
    public LargeResults wholeTexts() {
        textsAsLines = false;
        return this;
    }

    /// @return This, the parts not kept cut next while the result is past the limit (else after the last step).
    public LargeResults cutTheRest() {
        cutsTheRest = true;
        steps.add(new Step(null, this::cut));
        return this;
    }

    /// Gives the result in outline when the caller is a client outside Stroom and the result is past the limit: the
    /// whole saved to `/outputs/<tool>/<key>.json` (one entry a line where lists are long; a long text a line a
    /// list entry), then the steps run in order while it is still past the limit, and `whole_result` saying where
    /// it is saved, what was outlined or cut and how to read a part. Left whole for Stroom AI's own chat, within the
    /// limit, without a workspace, or when the file can't be written.
    ///
    /// @param caller    Who called the tool.
    /// @param workspace The session's workspace, if it has one.
    /// @param result    The tool's result, changed in place.
    /// @param limit     The most characters the result is given whole in.
    /// @return True if the result was given in outline.
    public boolean apply(final ToolCaller caller,
                  final Optional<SessionWorkspace> workspace,
                  final Map<String, Object> result,
                  final int limit) {
        if (!tooLarge(caller, workspace, result, limit)) {
            return false;
        }
        final String path = savedPath(tool, key);
        if (!save(workspace, path, result, textsAsLines)) {
            return false;
        }
        final Map<String, Object> whole = new LinkedHashMap<>(result);
        final int characters = length(result);
        final int target = Math.max(0, limit - NOTE_ROOM);
        final List<Step> toRun = new ArrayList<>(steps);
        if (!cutsTheRest) {
            toRun.add(new Step(null, this::cut));
        }
        final Set<String> outlined = new LinkedHashSet<>();
        final Set<String> cut = new LinkedHashSet<>();
        for (final Step step : toRun) {
            if (length(result) <= target) {
                break;
            }
            final Map<String, Object> before = new LinkedHashMap<>(result);
            if (step.change().apply(result, new Room(target, length(result), path)) && step.part() != null) {
                outlined.add(step.part());
            } else if (step.part() == null) {
                before.forEach((part, value) -> {
                    if (!Objects.equals(result.get(part), value) && !outlined.contains(part)) {
                        cut.add(part);
                    }
                });
            }
        }
        final Map<String, Object> said = note(path, characters, limit, whole, outlined, cut, textsAsLines);
        final Map<String, Object> rest = new LinkedHashMap<>(result);
        result.clear();
        result.put(WHOLE_RESULT, said);
        result.putAll(rest);
        return true;
    }

    /// @param caller    Who called the tool.
    /// @param workspace The session's workspace, if it has one.
    /// @param result    The tool's result.
    /// @param limit     The most characters it is given whole in.
    /// @return True if the result is past the limit for a client outside Stroom, with a workspace to save it in.
    public static boolean tooLarge(final ToolCaller caller,
                            final Optional<SessionWorkspace> workspace,
                            final Map<String, Object> result,
                            final int limit) {
        return caller.kind() == ToolCaller.Kind.EXTERNAL_CLIENT && workspace.isPresent() && length(result) > limit;
    }

    /// @param tool The tool.
    /// @param key  What the result is of.
    /// @return Where a result of the tool too large to give whole is saved.
    public static String savedPath(final String tool, final String key) {
        return FOLDER + tool + "/" + plain(key) + ".json";
    }

    /// @param candidates What a result may be of, the first that isn't blank chosen (a build, then a name).
    /// @return The first not blank, or null if none is.
    public static String keyOf(final String... candidates) {
        for (final String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }

    /// @param name A name.
    /// @return It as a file name: only letters, digits, '.', '_' and '-' (others become '_'); `result` if blank.
    public static String plain(final String name) {
        return name == null || name.isBlank()
                ? "result"
                : PLAIN.matcher(name).replaceAll("_");
    }

    /// Saves a value to the workspace as [#readable] JSON.
    ///
    /// @param workspace     The session's workspace, if it has one.
    /// @param path          Where.
    /// @param value         What.
    /// @param textsAsLines  True to save a long text of several lines as a list of its lines.
    /// @return True if it was saved; false without a workspace, or when it couldn't be written.
    public static boolean save(final Optional<SessionWorkspace> workspace,
                        final String path,
                        final Object value,
                        final boolean textsAsLines) {
        if (workspace.isEmpty()) {
            return false;
        }
        try {
            workspace.get().write(WorkspacePath.parse(path), readable(value, textsAsLines), null);
            return true;
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("A large result wasn't saved to {}: {}", path, e.getMessage()), e);
            return false;
        }
    }

    /// @param value        A JSON value.
    /// @param textsAsLines True to write a text past a line's length with several lines as a list of its lines.
    /// @return It as JSON read a line at a time: a value that fits a line on one line, else (a map, or a list) a
    ///         line a key or entry, so a workspace read (which cuts lines past 2,000 characters) and grep both see
    ///         an entry whole.
    public static String readable(final Object value, final boolean textsAsLines) {
        final StringBuilder sb = new StringBuilder();
        write(sb, value, 0, textsAsLines);
        return sb.append('\n').toString();
    }

    /// @param value A JSON value.
    /// @return Its length as compact JSON.
    public static int length(final Object value) {
        return value == null
                ? "null".length()
                : JsonUtil.writeValueAsString(value, false).length();
    }

    /// @param text A text.
    /// @param max  The most characters to give.
    /// @return The text, cut to the most characters with `...` if longer.
    public static String head(final String text, final int max) {
        return text.length() <= max
                ? text
                : text.substring(0, max) + "...";
    }

    private static void write(final StringBuilder sb, final Object value, final int indent, final boolean lines) {
        final Object v = lines
                ? asLines(value)
                : value;
        // JsonUtil gives null, not "null", for a null value (seen: build_translation_xslt's field_mapping: null).
        final String compact = v == null
                ? "null"
                : JsonUtil.writeValueAsString(v, false);
        if (compact.length() + indent <= LINE_CHARS || !(v instanceof Map<?, ?> || v instanceof List<?>)) {
            sb.append(compact);
            return;
        }
        final String pad = " ".repeat(indent + 2);
        if (v instanceof final Map<?, ?> map) {
            sb.append("{\n");
            int i = 0;
            for (final Map.Entry<?, ?> entry : map.entrySet()) {
                sb.append(pad).append(JsonUtil.writeValueAsString(String.valueOf(entry.getKey()), false))
                        .append(": ");
                write(sb, entry.getValue(), indent + 2, lines);
                sb.append(++i < map.size()
                        ? ",\n"
                        : "\n");
            }
            sb.append(" ".repeat(indent)).append('}');
        } else {
            final List<?> list = (List<?>) v;
            sb.append("[\n");
            for (int i = 0; i < list.size(); i++) {
                sb.append(pad);
                write(sb, list.get(i), indent + 2, lines);
                sb.append(i + 1 < list.size()
                        ? ",\n"
                        : "\n");
            }
            sb.append(" ".repeat(indent)).append(']');
        }
    }

    // A text past a line's length, of several lines (a Markdown table or section), as the list of its lines.
    private static Object asLines(final Object value) {
        return value instanceof final String text && text.length() > LINE_CHARS && text.indexOf('\n') >= 0
                ? text.lines().toList()
                : value;
    }

    // The parts not kept cut, each by half again until the result fits, from what they were before the first cut.
    private boolean cut(final Map<String, Object> result, final Room room) {
        final Map<String, Object> uncut = new LinkedHashMap<>(result);
        boolean changed = false;
        for (int i = 1; i <= MOST_CUTS && length(result) > room.target(); i++) {
            final double share = Math.pow(0.5, i);
            for (final Map.Entry<String, Object> entry : uncut.entrySet()) {
                if (!kept.contains(entry.getKey())) {
                    final Object cutValue = cutValue(entry.getKey(), entry.getValue(), share);
                    if (!Objects.equals(cutValue, entry.getValue())) {
                        result.put(entry.getKey(), cutValue);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    // A value cut to a share of it: a list to its first entries, then how many more the saved file holds; a long text
    // to its start, then that the rest is in the saved file; a map, each of its values not kept (by their dotted
    // path, `check.problems`).
    private Object cutValue(final String path, final Object value, final double share) {
        if (value instanceof final List<?> list) {
            final int keep = Math.max(FEWEST_ENTRIES, (int) Math.ceil(list.size() * share));
            final List<Object> cut = new ArrayList<>();
            for (final Object entry : list.subList(0, Math.min(keep, list.size()))) {
                cut.add(cutValue(path, entry, share));
            }
            if (keep < list.size()) {
                cut.add("(" + (list.size() - keep) + " more in the saved file: whole_result says where)");
            }
            return cut;
        }
        if (value instanceof final Map<?, ?> map) {
            final Map<String, Object> cut = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                final String at = path + "." + k;
                cut.put(String.valueOf(k), kept.contains(at)
                        ? v
                        : cutValue(at, v, share));
            });
            return cut;
        }
        if (value instanceof final String text) {
            final int keep = Math.max(FEWEST_CHARS, (int) (text.length() * share));
            return keep < text.length()
                    ? text.substring(0, keep) + "... (cut: the rest in the saved file)"
                    : text;
        }
        return value;
    }

    // Where the whole result is, what of it was outlined or cut, and how to read a part. When nothing could be
    // shortened (all of it what the agent acts on), it is given whole, still past the limit: the client then shows
    // only its start, which this note, first, is in, so it says plainly why the result was saved too.
    private static Map<String, Object> note(final String path,
                                            final int characters,
                                            final int limit,
                                            final Map<String, Object> whole,
                                            final Set<String> outlined,
                                            final Set<String> cut,
                                            final boolean textsAsLines) {
        final Map<String, Object> said = new LinkedHashMap<>();
        said.put("saved", path);
        said.put("characters", characters);
        if (!outlined.isEmpty()) {
            said.put("outlined", List.copyOf(outlined));
        }
        if (!cut.isEmpty()) {
            said.put("cut", List.copyOf(cut));
        }
        final Set<String> parts = new LinkedHashSet<>(outlined);
        parts.addAll(cut);
        final List<String> textLines = parts.stream()
                .filter(part -> textsAsLines && whole.get(part) instanceof String
                                && asLines(whole.get(part)) instanceof List<?>)
                .toList();
        if (!textLines.isEmpty()) {
            // Saved as a list of their lines: read with raw=true.
            said.put("text_lines", textLines);
        }
        if (parts.isEmpty()) {
            said.put("read", "This result is past the " + limit + " characters a client outside Stroom takes "
                             + "whole, but nothing in it could be shortened: all of it is what you must act "
                             + "on, so it is given whole here. It is also saved whole at " + path + ", in case your "
                             + "client shows only its start: read a part with jq (path=" + path + ", query='.<part>'; "
                             + "'.<part>[0:50][]' for a list's first 50 entries), or find a line with grep (path="
                             + path + ").");
            return said;
        }
        final String example = parts.iterator().next();
        final String query = "." + example + (textsAsLines && asLines(whole.get(example)) instanceof List<?>
                ? "[]"
                : "");
        said.put("read", "This result is too large to show whole here, so the parts named are given in outline "
                         + "(or cut, saying how many more): it is saved whole at " + path + ", a list's entries "
                         + "one a line and a long text a line an entry. Read a part with jq (path=" + path
                         + ", query='" + query + "', raw=true for text lines; '.<part>[0:50][]' for its first 50 "
                         + "entries), or find a line with grep (path=" + path + ") and read around it with "
                         + "read_file. Read only what you need: most of the time the outline is enough.");
        return said;
    }


    // --------------------------------------------------------------------------------


    /// Gives a part of a result in outline.
    @FunctionalInterface
    public interface Outliner {

        /// @param value The part.
        /// @param room  The room the result has for it.
        /// @return It in outline (or as it is, if it can't be made smaller).
        Object outline(Object value, Room room);
    }

    /// Changes a result in place to make it smaller.
    @FunctionalInterface
    public interface ResultStep {

        /// @param result The result.
        /// @param room   The room it has.
        /// @return True if it changed it.
        boolean apply(Map<String, Object> result, Room room);
    }

    /// The room a result has.
    ///
    /// @param target The most characters the result should take, leaving room for its note.
    /// @param now    The characters it takes now.
    /// @param saved  Where the whole result is saved.
    public record Room(int target, int now, String saved) {

        /// @param partNow The characters a part of the result takes now.
        /// @return The room the part has: the most characters it may take for the result to fit.
        public Room forPart(final int partNow) {
            return new Room(target - (now - partNow), partNow, saved);
        }
    }

    private record Step(String part, ResultStep change) {

    }
}
