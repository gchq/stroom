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

package stroom.ai.impl;

import stroom.ai.api.AiService;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Makes the documents and streams an answer mentions into links the user can click to open them. The model is
/// asked to write the links itself, by the document's UUID, and each link it writes is checked by it
/// ([#checkedDocLinks]); but it often writes only the name, e.g. `stream 73145` or **ACME-EVENTS**, and those are
/// linked as follows, as a fallback.
///
/// Only what the chat's tools gave is linked, so nothing is made up: a document whose UUID a tool result (or an
/// attachment) holds, and which the user can view, and a stream whose id a tool result holds. A document is
/// linked where the answer names it:
///
/// - formatted as a name, i.e. in bold, in quotes or as inline code;
/// - next to a word for its type, e.g. `the feed ACME-EVENTS` or `ACME-EVENTS pipeline`; or
/// - anywhere, if its name can't be mistaken for an ordinary word, e.g. `ACME-EVENTS` or `Event Data (Text)`.
///
/// A name written with typographic stand-ins for its hyphens or spaces (e.g. `Twitter‑Events‑V1.1`, with
/// non-breaking hyphens) is still the name, and is linked under the name itself. Where documents of different types
/// share the name, only a word for the type tells which one is meant, and without one the name is left alone. Each
/// document and stream is linked once a line. Code blocks, inline code that isn't exactly a name, and links already
/// written are left as they are.
public class AnswerLinks {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnswerLinks.class);

    private static final String DOCUMENTATION = "Documentation";

    // The most documents looked up for an answer, the latest the chat's tools gave first.
    static final int MAX_DOCUMENTS = 300;

    private static final Pattern UUID = Pattern.compile(
            "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");
    // A link already written, inline code, or a bare URL: none of them is changed.
    private static final Pattern LEAVE_ALONE = Pattern.compile(
            "!?\\[(?:[^\\]\\\\]|\\\\.)*]\\([^)\\s]*\\)|(`+).+?\\1|https?://\\S+");
    // E.g. 'stream 73145', 'Stream ID 73145', 'streams 73144 and 73145'.
    private static final Pattern STREAMS = Pattern.compile(
            "\\b[Ss]treams?(?:\\s+(?:[Ii][Dd]s?|#))?\\s+"
            + "(\\d{1,18}(?:(?:\\s*,\\s*|,?\\s+(?:and|or)\\s+)\\d{1,18})*)\\b");
    private static final Pattern NUMBER = Pattern.compile("\\d{1,18}");
    // A link that opens a document: its text, and its query.
    private static final Pattern DOC_LINK = Pattern.compile(
            "(?<!!)\\[((?:[^\\]\\\\]|\\\\.)*)]\\((\\?action=open-doc[^)\\s]*)\\)");
    private static final Pattern DOC_UUID = Pattern.compile(
            "[?&]docUuid=([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})(?![\\w-])");
    private static final Pattern INLINE_CODE = Pattern.compile("(`+).+?\\1");
    private static final Pattern FENCE = Pattern.compile("^\\s*(```|~~~)");
    // A name of ordinary words, e.g. 'Events' or 'Data Splitter', is only linked where it is formatted as a name
    // or its type is named next to it.
    private static final Pattern ORDINARY_WORDS = Pattern.compile(
            "(?:[A-Z]?[a-z]+|[A-Z]+)(?: (?:[A-Z]?[a-z]+|[A-Z]+))*");
    // A name of one word in capitals, e.g. 'TWITTER' (a feed's): distinctive, unless it is one of the capitalised
    // words an answer uses as words (seen: "all of TWITTER's existing data" not linked, as an ordinary word).
    private static final Pattern CAPITALS = Pattern.compile("[A-Z][A-Z0-9_]{2,}");
    private static final Set<String> CAPITALISED_WORDS = Set.of("XML", "JSON", "CSV", "TSV", "UTC", "GMT", "DNS",
            "HTTP", "HTTPS", "URL", "URI", "UUID", "XSLT", "XSD", "API", "SQL", "CEF", "ECS", "TCP", "UDP", "AND",
            "NOT", "ALL", "NONE", "TRUE", "FALSE", "YES", "INFO", "WARN", "WARNING", "ERROR", "FATAL", "DEBUG",
            "TRACE", "GET", "POST", "PUT", "DELETE", "SSH", "TLS", "SSL", "LDAP", "PDF", "HTML", "YAML", "UTF",
            "ASCII", "AWS", "VPN", "NOTE", "TODO");

    private final AiService aiService;
    private final DocumentAccess documentAccess;

    /// @param aiService      Reads the chat's messages.
    /// @param documentAccess Finds the documents the chat's tools gave, as the user.
    @Inject
    AnswerLinks(final AiService aiService, final DocumentAccess documentAccess) {
        this.aiService = aiService;
        this.documentAccess = documentAccess;
    }

    /// Run as the user, who must be able to view a document for it to be linked.
    ///
    /// @param chatId The chat the answer is in.
    /// @param answer The answer, in Markdown.
    /// @return The answer with the documents and streams the chat's tools gave linked, or as it was if that can't
    /// be done.
    public String link(final int chatId, final String answer) {
        if (NullSafe.isBlankString(answer)) {
            return answer;
        }
        try {
            final List<String> given = aiService.getMessages(chatId).stream()
                    .filter(message -> message.getMessageType() == AiMessageType.TOOL_RESULT
                                       || message.getMessageType() == AiMessageType.ATTACHMENT)
                    .map(AiChatMessage::getMessage)
                    .filter(NullSafe::isNonBlankString)
                    .toList()
                    .reversed();
            final Map<String, List<DocRef>> documents = documentsByName(given);
            final String allGiven = String.join("\n", given);
            // The links written first, each checked by its document's UUID; then, as a fallback, the names left.
            final String checked = checkedDocLinks(answer, uuid -> documentAccess.find(uuid)
                    .map(ExplorerNode::getDocRef));
            return link(checked, documents, id -> Pattern.compile("\\b" + id + "\\b").matcher(allGiven).find());
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to link the answer in chat {}: {}", chatId, e.getMessage()),
                    e);
            return answer;
        }
    }

    /// Run as the user, who must be able to view a document for it to stay linked.
    ///
    /// @param chatId The chat the text is in.
    /// @param text   Text a tool wrote (a form's message), in Markdown, its documents linked by their UUIDs.
    /// @return The text with each document link checked by its UUID ([#checkedDocLinks]); no names are linked, as
    /// the tool that wrote it linked what it means (a name matched there was linked to the wrong document: seen,
    /// the index a form was to create linked to the feed of its name).
    public String checkLinks(final int chatId, final String text) {
        if (NullSafe.isBlankString(text)) {
            return text;
        }
        try {
            return checkedDocLinks(text, uuid -> documentAccess.find(uuid).map(ExplorerNode::getDocRef));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to check the links in chat {}: {}", chatId, e.getMessage()),
                    e);
            return text;
        }
    }

    // The documents whose UUIDs the texts hold, that the user can view, by name.
    private Map<String, List<DocRef>> documentsByName(final List<String> texts) {
        final Set<String> uuids = new LinkedHashSet<>();
        for (final String text : texts) {
            final Matcher matcher = UUID.matcher(text);
            while (matcher.find() && uuids.size() < MAX_DOCUMENTS) {
                uuids.add(matcher.group().toLowerCase(Locale.ROOT));
            }
        }
        final Map<String, List<DocRef>> byName = new HashMap<>();
        for (final String uuid : uuids) {
            documentAccess.find(uuid)
                    .map(ExplorerNode::getDocRef)
                    .filter(docRef -> NullSafe.isNonBlankString(docRef.getName()))
                    .ifPresent(docRef -> byName.computeIfAbsent(docRef.getName(), name -> new ArrayList<>())
                            .add(docRef));
        }
        return byName;
    }

    /// The links to documents an answer holds, each checked by its UUID: one to a document the user can view is
    /// written with the document's own type (the model may give the UUID alone:
    /// `[ACME-EVENTS](?action=open-doc&docUuid=...)`), and one to a document that isn't there, or that they may
    /// not view, becomes its text alone: the document is the UUID's, never a guess (asked for by the user), and a
    /// wrong type or a made-up UUID never reaches the user. Code blocks and inline code are left as they are.
    ///
    /// @param answer  An answer, in Markdown.
    /// @param resolve Finds a document by UUID, as the user.
    /// @return The answer with its document links checked.
    static String checkedDocLinks(final String answer, final Function<String, Optional<DocRef>> resolve) {
        if (NullSafe.isBlankString(answer) || !answer.contains("action=open-doc")) {
            return answer;
        }
        final StringBuilder out = new StringBuilder();
        boolean inFence = false;
        for (final String line : answer.split("\n", -1)) {
            if (!out.isEmpty()) {
                out.append('\n');
            }
            if (FENCE.matcher(line).find()) {
                inFence = !inFence;
                out.append(line);
            } else if (inFence) {
                out.append(line);
            } else {
                out.append(checkedLine(line, resolve));
            }
        }
        return out.toString();
    }

    private static String checkedLine(final String line, final Function<String, Optional<DocRef>> resolve) {
        final StringBuilder out = new StringBuilder();
        final Matcher code = INLINE_CODE.matcher(line);
        int at = 0;
        while (code.find()) {
            out.append(checkedText(line.substring(at, code.start()), resolve)).append(code.group());
            at = code.end();
        }
        return out.append(checkedText(line.substring(at), resolve)).toString();
    }

    private static String checkedText(final String text, final Function<String, Optional<DocRef>> resolve) {
        final Matcher link = DOC_LINK.matcher(text);
        final StringBuilder out = new StringBuilder();
        while (link.find()) {
            final Matcher uuid = DOC_UUID.matcher(link.group(2));
            final Optional<DocRef> docRef = uuid.find()
                    ? resolve.apply(uuid.group(1).toLowerCase(Locale.ROOT))
                    : Optional.empty();
            link.appendReplacement(out, Matcher.quoteReplacement(docRef
                    .map(found -> "[" + link.group(1) + "](?action=open-doc&docType=" + found.getType()
                                  + "&docUuid=" + found.getUuid() + ")")
                    .orElse(link.group(1))));
        }
        link.appendTail(out);
        return out.toString();
    }

    /// @param answer      An answer, in Markdown.
    /// @param documents   The documents that may be linked, by name.
    /// @param knownStream True for the id of a stream that may be linked.
    /// @return The answer with them linked.
    static String link(final String answer,
                       final Map<String, List<DocRef>> documents,
                       final LongPredicate knownStream) {
        // Longest first, so that 'ACME-EVENTS Translation' is linked rather than 'ACME-EVENTS' in it.
        final List<String> names = documents.keySet().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        final StringBuilder linked = new StringBuilder();
        boolean inFence = false;
        for (final String line : answer.split("\n", -1)) {
            if (!linked.isEmpty()) {
                linked.append('\n');
            }
            if (FENCE.matcher(line).find()) {
                inFence = !inFence;
                linked.append(line);
            } else if (inFence) {
                linked.append(line);
            } else {
                linked.append(linkLine(line, names, documents, knownStream));
            }
        }
        return linked.toString();
    }

    private static String linkLine(final String line,
                                   final List<String> names,
                                   final Map<String, List<DocRef>> documents,
                                   final LongPredicate knownStream) {
        final List<Replacement> replacements = new ArrayList<>();
        final List<int[]> leftAlone = new ArrayList<>();
        // Names are found in the line with its hyphens and spaces made plain, at the same positions.
        final String plain = plainPunctuation(line);
        final Set<String> linkedOnLine = new HashSet<>();
        final Matcher leave = LEAVE_ALONE.matcher(line);
        while (leave.find()) {
            leftAlone.add(new int[]{leave.start(), leave.end()});
            // Inline code that is exactly a document's name is linked, keeping it as code.
            final String code = leave.group(1) != null
                    ? line.substring(leave.start() + leave.group(1).length(), leave.end() - leave.group(1).length())
                    : null;
            final DocRef docRef = code == null
                    ? null
                    : choose(documents.get(plainPunctuation(code).strip()), plain, leave.start(), leave.end());
            if (docRef != null && linkedOnLine.add(docRef.getUuid())) {
                replacements.add(new Replacement(leave.start(), leave.end(), docLink(leave.group(), docRef)));
            }
        }

        final Matcher streams = STREAMS.matcher(line);
        while (streams.find()) {
            if (overlaps(leftAlone, streams.start(), streams.end())) {
                continue;
            }
            final List<long[]> ids = new ArrayList<>();
            final Matcher number = NUMBER.matcher(line).region(streams.start(1), streams.end(1));
            while (number.find()) {
                final long id = Long.parseLong(number.group());
                if (id > 0 && knownStream.test(id) && linkedOnLine.add("stream:" + id)) {
                    ids.add(new long[]{id, number.start(), number.end()});
                }
            }
            if (ids.size() == 1 && streams.start(1) == ids.getFirst()[1] && streams.end(1) == ids.getFirst()[2]) {
                // One stream: the words and its id are the link, e.g. [stream 73145].
                replacements.add(new Replacement(streams.start(), streams.end(),
                        streamLink(streams.group(), ids.getFirst()[0])));
            } else {
                ids.forEach(id -> replacements.add(new Replacement((int) id[1], (int) id[2],
                        streamLink(String.valueOf(id[0]), id[0]))));
            }
        }

        for (final String name : names) {
            int from = 0;
            while (true) {
                final int start = plain.indexOf(name, from);
                if (start < 0) {
                    break;
                }
                final int end = start + name.length();
                from = start + 1;
                if (!isWholeName(plain, start, end)
                    || overlaps(leftAlone, start, end)
                    || replacements.stream().anyMatch(r -> r.start < end && start < r.end)) {
                    continue;
                }
                final DocRef docRef = choose(documents.get(name), plain, start, end);
                if (docRef == null) {
                    continue;
                }
                if (!isOrdinaryWords(name)
                    || isFormattedAsName(plain, start, end)
                    || typeNamedNextTo(plain, start, end, docRef.getType())) {
                    if (linkedOnLine.add(docRef.getUuid())) {
                        replacements.add(new Replacement(start, end, docLink(escape(name), docRef)));
                    }
                }
            }
        }

        if (replacements.isEmpty()) {
            return line;
        }
        replacements.sort(Comparator.comparingInt(Replacement::start));
        final StringBuilder sb = new StringBuilder();
        int at = 0;
        for (final Replacement replacement : replacements) {
            if (replacement.start < at) {
                continue;
            }
            sb.append(line, at, replacement.start).append(replacement.text);
            at = replacement.end;
        }
        return sb.append(line.substring(at)).toString();
    }

    // The text with the typographic stand-ins for a hyphen or a space a model writes in a name made plain, one
    // character for one, so a name is found where it was written (seen: a table of pipelines written with
    // non-breaking hyphens, e.g. 'Twitter‑Events‑V1.1', not linked). An em dash is left, as it parts words.
    private static String plainPunctuation(final String text) {
        final char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            chars[i] = switch (chars[i]) {
                // Hyphen, non-breaking hyphen, figure dash, en dash, minus sign.
                case '\u2010', '\u2011', '\u2012', '\u2013', '\u2212' -> '-';
                // No-break space, figure space, narrow no-break space.
                case '\u00A0', '\u2007', '\u202F' -> ' ';
                default -> chars[i];
            };
        }
        return new String(chars);
    }

    // The one document meant: the only one by the name, or the one whose type is named next to it.
    private static DocRef choose(final List<DocRef> candidates, final String line, final int start, final int end) {
        if (NullSafe.isEmptyCollection(candidates)) {
            return null;
        }
        if (candidates.size() == 1) {
            return candidates.getFirst();
        }
        final List<DocRef> named = candidates.stream()
                .filter(docRef -> typeNamedNextTo(line, start, end, docRef.getType()))
                .toList();
        if (named.size() == 1) {
            return named.getFirst();
        }
        // A Documentation doc named as another document is that document's (write_documentation names it after its
        // pipeline): the name, with no word for a type, means the document (seen: "filter 58 on
        // Twitter-Events-V1.1" not linked, as the pipeline's documentation shares its name).
        final List<DocRef> documented = candidates.stream()
                .filter(docRef -> !DOCUMENTATION.equals(docRef.getType()))
                .toList();
        return named.isEmpty() && documented.size() == 1
                ? documented.getFirst()
                : null;
    }

    // Words a sentence could hold as words, so linked only where formatted as a name or next to its type's word.
    private static boolean isOrdinaryWords(final String name) {
        return ORDINARY_WORDS.matcher(name).matches()
               && (!CAPITALS.matcher(name).matches() || CAPITALISED_WORDS.contains(name));
    }

    // Not part of a longer name or word, e.g. 'ACME' in 'ACME-EVENTS'.
    private static boolean isWholeName(final String line, final int start, final int end) {
        return (start == 0 || !isNameChar(line.charAt(start - 1)))
               && (end == line.length() || !isNameChar(line.charAt(end)) || isSentenceEnd(line, end));
    }

    private static boolean isNameChar(final char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.';
    }

    // A full stop that ends a sentence, not one in a name, e.g. 'ACME-EVENTS.' but not 'V1.0'.
    private static boolean isSentenceEnd(final String line, final int at) {
        return line.charAt(at) == '.' && (at + 1 == line.length() || Character.isWhitespace(line.charAt(at + 1)));
    }

    // In bold or italics, or in quotes, e.g. **Events**, 'Events' or "Events".
    private static boolean isFormattedAsName(final String line, final int start, final int end) {
        if (start == 0 || end == line.length()) {
            return false;
        }
        final char before = line.charAt(start - 1);
        final char after = line.charAt(end);
        return (before == '*' && after == '*')
               || (before == '_' && after == '_')
               || (before == '\'' && after == '\'')
               || (before == '"' && after == '"')
               || (before == '‘' && after == '’')
               || (before == '“' && after == '”');
    }

    // E.g. 'feed ACME-EVENTS', 'the ACME-EVENTS pipeline' or 'XSLT **ACME-EVENTS**'.
    static boolean typeNamedNextTo(final String line, final int start, final int end, final String type) {
        final String before = line.substring(0, start).replaceAll("[*_'\"`‘“\\s]+$", "");
        final String after = line.substring(end).replaceAll("^[*_'\"`’”\\s]+", "");
        final Matcher wordBefore = Pattern.compile("([A-Za-z]+)$").matcher(before);
        final Matcher wordAfter = Pattern.compile("^([A-Za-z]+)").matcher(after);
        return (wordBefore.find() && isWordForType(wordBefore.group(1), type))
               || (wordAfter.find() && isWordForType(wordAfter.group(1), type));
    }

    // E.g. 'feed' for Feed, 'index' for LuceneIndex, 'converter' for TextConverter, 'XSLT' for XSLT.
    private static boolean isWordForType(final String word, final String type) {
        if (type == null || word.length() < 4 && !"xslt".equalsIgnoreCase(word)) {
            return false;
        }
        final String lowerWord = word.toLowerCase(Locale.ROOT);
        final String lowerType = type.toLowerCase(Locale.ROOT);
        final String singular = lowerWord.endsWith("s") && !lowerWord.endsWith("ss")
                ? lowerWord.substring(0, lowerWord.length() - 1)
                : lowerWord;
        return lowerType.equals(singular) || lowerType.endsWith(singular);
    }

    private static boolean overlaps(final List<int[]> ranges, final int start, final int end) {
        return ranges.stream().anyMatch(range -> range[0] < end && start < range[1]);
    }

    private static String docLink(final String text, final DocRef docRef) {
        return "[" + text + "](?action=open-doc&docType=" + docRef.getType() + "&docUuid=" + docRef.getUuid() + ")";
    }

    private static String streamLink(final String text, final long id) {
        return "[" + text + "](?action=open-stream&id=" + id + ")";
    }

    private static String escape(final String text) {
        return text.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]");
    }


    // --------------------------------------------------------------------------------


    private record Replacement(int start, int end, String text) {

    }
}
