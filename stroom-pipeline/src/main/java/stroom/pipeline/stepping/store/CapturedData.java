/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.pipeline.stepping.store;

import java.nio.charset.StandardCharsets;

/**
 * One side (input or output) of an element's captured IO, in the form the store persists it - element
 * specific, as the recorder produced it.
 * <ul>
 *   <li>{@link Format#SAX_EVENTS} - the binary event stream from a {@code SAXEventRecorder} (XML elements).
 *       Faithful and directly replayable; rendered to display text through the Saxon tree path.</li>
 *   <li>{@link Format#TEXT} - UTF-8 text from a reader or writer element. Stored and displayed as-is.</li>
 * </ul>
 * A {@code null} {@link CapturedData} means that side produced nothing.
 */
public record CapturedData(Format format, byte[] data) {

    public enum Format {
        SAX_EVENTS,
        TEXT
    }

    public static CapturedData saxEvents(final byte[] eventBytes) {
        return eventBytes == null ? null : new CapturedData(Format.SAX_EVENTS, eventBytes);
    }

    public static CapturedData text(final String text) {
        return text == null ? null : new CapturedData(Format.TEXT, text.getBytes(StandardCharsets.UTF_8));
    }

    public boolean isSaxEvents() {
        return format == Format.SAX_EVENTS;
    }

    /**
     * @return the UTF-8 text, for a {@link Format#TEXT} value. Not valid for {@link Format#SAX_EVENTS}, which
     * must be rendered through the event/tree path instead.
     */
    public String asText() {
        if (format != Format.TEXT) {
            throw new IllegalStateException("Not text: " + format);
        }
        return new String(data, StandardCharsets.UTF_8);
    }
}
