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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Text mangled by a Windows code page round trip, repaired.
///
/// Seen in VS Code on Windows: an agent wrote a doc body to a file and read it back with Windows PowerShell, which
/// reads and writes the ANSI code page (cp1252), so each UTF-8 character came back as two or three (an em dash as
/// 'â€”', a right quote as 'â€™', é as 'Ã©'). It then spent a stalled terminal command trying to fix them. Text a tool
/// is given is repaired here instead: a run of characters that are the cp1252 reading of one UTF-8 character's bytes
/// is put back as that character. Anything else is left as it is.
public final class MojibakeRepair {

    // How many times text mangled more than once is put back.
    private static final int MAX_PASSES = 3;
    // cp1252's bytes 0x80-0x9F that it has characters for; PowerShell keeps the five it hasn't as C1 controls.
    private static final Map<Character, Integer> HIGH = highBytes();
    // A UTF-8 lead byte (two, three or four bytes) read as cp1252, then its continuation bytes read the same way.
    private static final Pattern RUN = Pattern.compile("[\\u00C2-\\u00F4][" + continuations() + "]{1,3}");

    private MojibakeRepair() {
    }

    /// @param text Some text, or null.
    /// @return The text with mojibake runs put back, and how many were (text mangled twice is put back twice over).
    public static Repaired repair(final String text) {
        if (text == null || text.isEmpty()) {
            return new Repaired(text, 0);
        }
        String repaired = text;
        int total = 0;
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            final Matcher matcher = RUN.matcher(repaired);
            final StringBuilder out = new StringBuilder();
            int count = 0;
            while (matcher.find()) {
                final String fixed = undo(matcher.group());
                if (fixed != null) {
                    count++;
                }
                matcher.appendReplacement(out, Matcher.quoteReplacement(fixed != null
                        ? fixed
                        : matcher.group()));
            }
            matcher.appendTail(out);
            if (count == 0) {
                break;
            }
            total += count;
            repaired = out.toString();
        }
        return new Repaired(repaired, total);
    }

    /// @param count How many places were repaired.
    /// @return What the reply says when text was repaired.
    public static String note(final int count) {
        return "Repaired text mangled by a Windows code page round trip, in " + count + " place(s) (e.g. 'â€”' back "
               + "to '—'). Pass text to the server directly, not through a file a terminal reads or writes.";
    }

    // The UTF-8 character a run's bytes are, or null if they aren't one.
    private static String undo(final String run) {
        final byte[] bytes = new byte[run.length()];
        for (int i = 0; i < run.length(); i++) {
            final char ch = run.charAt(i);
            bytes[i] = (byte) HIGH.getOrDefault(ch, (int) ch).intValue();
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (final CharacterCodingException e) {
            return null;
        }
    }

    private static Map<Character, Integer> highBytes() {
        final Charset cp1252 = Charset.forName("windows-1252");
        final Map<Character, Integer> high = new HashMap<>();
        for (int b = 0x80; b < 0xA0; b++) {
            final CharBuffer decoded = decodeOne(cp1252, b);
            final char ch = decoded != null && decoded.length() == 1
                    ? decoded.charAt(0)
                    : (char) b;
            high.put(ch, b);
        }
        return Map.copyOf(high);
    }

    private static CharBuffer decodeOne(final Charset charset, final int b) {
        try {
            return charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(new byte[]{(byte) b}));
        } catch (final CharacterCodingException e) {
            return null;
        }
    }

    // The characters a continuation byte reads as, escaped for a character class.
    private static String continuations() {
        final StringBuilder chars = new StringBuilder();
        HIGH.keySet().forEach(ch -> chars.append(escaped(ch)));
        for (int b = 0xA0; b < 0xC0; b++) {
            chars.append(escaped((char) b));
        }
        return chars.toString();
    }

    private static String escaped(final char ch) {
        return String.format("\\u%04X", (int) ch);
    }


    // --------------------------------------------------------------------------------


    /// Text after repair.
    ///
    /// @param text  The text.
    /// @param count How many places were repaired.
    public record Repaired(String text, int count) {

    }
}
