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

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// A raw stream with bytes its feed's character set can't decode: Stroom's viewer refuses its text ("Unable to
/// decode a UTF-8 character"), its pipelines read on replacing them; the tools read it as a pipeline does, and say
/// what was replaced and what to do.
class TestStreamCharsets {

    /// UTF-8, then a Latin-1 é, then Windows-1252 curly quotes.
    static final byte[] MIXED = mixed();

    @Test
    void bytesAUtf8FeedCantDecodeAreReplacedAndSaid() {
        final StreamCharsets.Decoded decoded = StreamCharsets.decode(MIXED, "UTF-8", true);

        assertThat(decoded.text())
                .contains("café 👀")
                .contains("latin1 caf�")
                .contains("cp1252 �q�");
        final Map<String, Object> report = decoded.report();
        assertThat(report.get("undecodable_bytes")).isEqualTo(3);
        assertThat(report.get("as_windows_1252")).isEqualTo(Map.of("93", "“", "94", "”", "E9", "é"));
        assertThat((String) report.get("advice")).contains("mixes character sets");
    }

    @Test
    void aStreamWholeInWindows1252IsSaidToBeSo() {
        final byte[] cp1252 = "café “q”".getBytes(Charset.forName("windows-1252"));

        final StreamCharsets.Decoded decoded = StreamCharsets.decode(cp1252, "UTF-8", true);

        assertThat((String) decoded.report().get("advice")).contains("set the feed's encoding to windows-1252");
    }

    @Test
    void textThatDecodesHasNoReport() {
        assertThat(StreamCharsets.decode("café".getBytes(StandardCharsets.UTF_8), "UTF-8", true))
                .isEqualTo(new StreamCharsets.Decoded("café", null));
    }

    @Test
    void aSequenceCutOffByTheEndOfWhatWasReadIsLeftOutNotCounted() {
        final byte[] bytes = "café".getBytes(StandardCharsets.UTF_8);
        final byte[] cut = new byte[bytes.length - 1];
        System.arraycopy(bytes, 0, cut, 0, cut.length);

        assertThat(StreamCharsets.decode(cut, "UTF-8", false)).isEqualTo(new StreamCharsets.Decoded("caf", null));
    }

    @Test
    void theFeedsOwnEncodingDecodesTheBytes() {
        final String text = "café “q”\n".repeat(50);

        final StreamCharsets.Decoded decoded = StreamCharsets.decode(
                text.getBytes(Charset.forName("windows-1252")), "windows-1252", true);

        assertThat(decoded).isEqualTo(new StreamCharsets.Decoded(text, null));
    }

    @Test
    void anUnknownEncodingIsReadAsUtf8() {
        assertThat(StreamCharsets.charset("no-such-charset")).isEqualTo(StandardCharsets.UTF_8);
        assertThat(StreamCharsets.charset(null)).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    void theNoteSaysWhichBytesAndWhatToDo() {
        final String note = StreamCharsets.note(7, StreamCharsets.decode(MIXED, null, true).report());

        assertThat(note)
                .startsWith("stream 7: Stroom can't show it as text, because 3 byte(s) aren't UTF-8 (bytes 93, 94, "
                            + "E9)")
                .contains("mixes character sets");
    }

    private static byte[] mixed() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("<recs><rec>utf8 café 👀</rec><rec>latin1 caf".getBytes(StandardCharsets.UTF_8));
        out.write(0xE9);
        out.writeBytes("</rec><rec>cp1252 ".getBytes(StandardCharsets.UTF_8));
        out.write(0x93);
        out.write('q');
        out.write(0x94);
        out.writeBytes("</rec></recs>".getBytes(StandardCharsets.UTF_8));
        out.writeBytes("<rec>pad</rec>".repeat(400).getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }
}
