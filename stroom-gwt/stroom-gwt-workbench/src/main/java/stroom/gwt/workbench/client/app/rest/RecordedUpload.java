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

package stroom.gwt.workbench.client.app.rest;

import java.util.Objects;

/// A file upload a screen made (Stroom's `CustomFileUpload.submit()`), as the harness records it:
/// the URL it was posted to, the chosen file's name and, for a small text file, its content.
public final class RecordedUpload {

    /// The largest file whose content is recorded, in bytes.
    public static final int MAX_CONTENT_BYTES = 64 * 1024;

    private static final char REPLACEMENT_CHARACTER = '�';
    private static final char NUL = '\u0000';

    private final String url;
    private final String fileName;
    private final String content;

    /// @param url      The URL the file was posted to, relative to the host page, e.g.
    ///                 `importfile.rpc`.
    /// @param fileName The chosen file's name, e.g. `import.zip`.
    /// @param content  The file's content as text, or null if it isn't recorded (see
    ///                 [#textContent(String, double)]).
    public RecordedUpload(final String url, final String fileName, final String content) {
        this.url = Objects.requireNonNull(url, "url");
        this.fileName = Objects.requireNonNull(fileName, "fileName");
        this.content = content;
    }

    /// Decides whether a file's content is recorded: only a small file that is valid UTF-8 text.
    ///
    /// @param decoded The file decoded as UTF-8 (`FileReader.readAsText`), which replaces invalid
    ///                bytes with U+FFFD, or null if it wasn't read.
    /// @param size    The file's size in bytes.
    /// @return The content, or null for a file that is too big or isn't text.
    public static String textContent(final String decoded, final double size) {
        if (decoded == null || size > MAX_CONTENT_BYTES) {
            return null;
        }
        for (int i = 0; i < decoded.length(); i++) {
            final char c = decoded.charAt(i);
            if (c == REPLACEMENT_CHARACTER || c == NUL) {
                return null;
            }
        }
        return decoded;
    }

    /// @return The URL the file was posted to, relative to the host page, e.g. `importfile.rpc`.
    public String getUrl() {
        return url;
    }

    /// @return The chosen file's name.
    public String getFileName() {
        return fileName;
    }

    /// @return The file's content, or null if it isn't recorded.
    public String getContent() {
        return content;
    }

    /// @return The upload as a request, e.g. `POST importfile.rpc (upload of import.zip)`.
    public String describe() {
        return "POST " + url + " (upload of " + fileName + ")";
    }

    @Override
    public String toString() {
        return describe();
    }
}
