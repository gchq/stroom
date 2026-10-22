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

import stroom.util.shared.NullSafe;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/// A raw stream's bytes decoded as a pipeline decodes them, saying what could not be.
///
/// Stroom's data viewer refuses a stream with bytes its feed's character set can't decode ("Unable to decode a
/// UTF-8 character starting at byte offset 66"), while its pipelines read on, replacing each such byte with U+FFFD
/// and logging an error ("Found un-decodable byte [93]"). The tools read such a stream as a pipeline does, so the
/// agent sees it as processing will, and say what was replaced: which bytes, how they would read in Windows-1252,
/// and what to do about it.
public final class StreamCharsets {

    private static final char REPLACEMENT = '�';
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
    private static final int MAX_BYTE_VALUES = 40;
    private static final int MAX_EXAMPLES = 5;
    private static final int CONTEXT_CHARS = 30;

    private StreamCharsets() {
        // Static methods only.
    }

    /// @param encoding A Java charset name (a feed's encoding), or null.
    /// @return The charset; UTF-8 when there is none or it is unknown.
    public static Charset charset(final String encoding) {
        try {
            return NullSafe.isBlankString(encoding)
                    ? StandardCharsets.UTF_8
                    : Charset.forName(encoding.strip());
        } catch (final RuntimeException e) {
            return StandardCharsets.UTF_8;
        }
    }

    /// Decodes bytes as a pipeline would, each byte the character set can't decode replaced by U+FFFD.
    ///
    /// @param data     The bytes.
    /// @param encoding The feed's encoding, or null for UTF-8.
    /// @param last     False if only the head was read: a sequence cut off at the end is then left out, not
    ///                 replaced.
    /// @return The text, and the report of what was replaced (null when every byte decoded).
    public static Decoded decode(final byte[] data, final String encoding, final boolean last) {
        final Charset charset = charset(encoding);
        final CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        final ByteBuffer in = ByteBuffer.wrap(data);
        final CharBuffer out = CharBuffer.allocate(Math.max(16, (int) (data.length * (double) decoder.maxCharsPerByte())
                                                                + 16));
        final List<int[]> bad = new ArrayList<>();
        while (true) {
            final CoderResult result = decoder.decode(in, out, last);
            if (result.isUnderflow()) {
                if (last) {
                    decoder.flush(out);
                }
                break;
            }
            if (result.isError()) {
                for (int i = 0; i < result.length(); i++) {
                    bad.add(new int[]{out.position(), in.get() & 0xFF});
                    out.put(REPLACEMENT);
                }
            } else {
                // Overflow can't happen with this buffer, but stop rather than loop.
                break;
            }
        }
        out.flip();
        final String text = out.toString();
        if (bad.isEmpty()) {
            return new Decoded(text, null);
        }
        return new Decoded(text, report(text, bad, NullSafe.isBlankString(encoding)
                ? "UTF-8"
                : encoding, charset));
    }

    private static String cp1252(final int value) {
        try {
            return WINDOWS_1252.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(new byte[]{(byte) value}))
                    .toString();
        } catch (final CharacterCodingException e) {
            return null;
        }
    }

    private static String hex(final int value) {
        return String.format(Locale.ROOT, "%02X", value);
    }

    private static Map<String, Object> report(final String text,
                                              final List<int[]> bad,
                                              final String encoding,
                                              final Charset charset) {
        final Set<Integer> values = new TreeSet<>();
        bad.forEach(b -> values.add(b[1]));
        final List<Integer> shownValues = values.stream().limit(MAX_BYTE_VALUES).toList();
        final Map<String, String> as1252 = new LinkedHashMap<>();
        shownValues.forEach(v -> as1252.put(hex(v), cp1252(v)));
        // Characters the stream does decode beyond ASCII: real UTF-8 in it, so it mixes character sets.
        final boolean decodedBeyondAscii = text.chars().anyMatch(c -> c > 0x7F && c != REPLACEMENT);
        final List<Map<String, Object>> examples = new ArrayList<>();
        for (final int[] b : bad.subList(0, Math.min(MAX_EXAMPLES, bad.size()))) {
            final Map<String, Object> example = new LinkedHashMap<>();
            example.put("at_character", b[0]);
            example.put("byte", hex(b[1]));
            example.put("as_windows_1252", cp1252(b[1]));
            example.put("context", text.substring(Math.max(0, b[0] - CONTEXT_CHARS),
                    Math.min(text.length(), b[0] + CONTEXT_CHARS)));
            examples.add(example);
        }
        final String advice;
        if (!StandardCharsets.UTF_8.equals(charset)) {
            advice = "These bytes aren't characters in the feed's encoding (" + encoding + "). Ask the user which "
                     + "character set the source writes, and set it as the feed's encoding, or have the source "
                     + "fixed.";
        } else if (decodedBeyondAscii) {
            advice = "The stream mixes character sets: UTF-8 (which decodes) and single bytes from another one, "
                     + "most likely Windows-1252 or Latin-1 (how they would read is given). No one encoding reads it "
                     + "all: Stroom replaces these bytes with the replacement character and logs an error for each, "
                     + "and the characters are lost. Tell the user; the fix is at the source (to send UTF-8 "
                     + "throughout).";
        } else if (as1252.values().stream().allMatch(NullSafe::isNonEmptyString)) {
            advice = "Nothing in the stream is UTF-8 beyond ASCII: it reads whole as Windows-1252 (how these bytes "
                     + "read is given). Ask the user to confirm, then set the feed's encoding to windows-1252, so "
                     + "Stroom reads these characters instead of replacing them (or have the source send UTF-8).";
        } else {
            advice = "Nothing in the stream is UTF-8 beyond ASCII, and some bytes aren't Windows-1252 either: it may "
                     + "be ISO-8859-1 or another single-byte character set. Ask the user which, and set it as the "
                     + "feed's encoding.";
        }
        final Map<String, Object> report = new LinkedHashMap<>();
        report.put("encoding", encoding);
        report.put("undecodable_bytes", bad.size());
        report.put("byte_values", shownValues.stream().map(StreamCharsets::hex).toList());
        report.put("as_windows_1252", as1252);
        report.put("examples", examples);
        report.put("advice", advice);
        return report;
    }

    /// @param streamId The stream.
    /// @param report   What reading it replaced.
    /// @return One line for a tool's notes.
    @SuppressWarnings("unchecked")
    public static String note(final long streamId, final Map<String, Object> report) {
        final List<String> values = (List<String>) report.get("byte_values");
        return "stream " + streamId + ": Stroom can't show it as text, because " + report.get("undecodable_bytes")
               + " byte(s) aren't " + report.get("encoding") + " (bytes "
               + String.join(", ", values.subList(0, Math.min(8, values.size())))
               + "); read through its bytes instead, each replaced with the replacement character, as a pipeline "
               + "reads it. " + report.get("advice");
    }


    // --------------------------------------------------------------------------------


    /// Bytes decoded.
    ///
    /// @param text   The text, each byte that couldn't be decoded replaced by U+FFFD.
    /// @param report What was replaced (`encoding`, `undecodable_bytes`, `byte_values`, `as_windows_1252`,
    ///               `examples`, `advice`), or null if every byte decoded.
    public record Decoded(String text, Map<String, Object> report) {

    }
}
