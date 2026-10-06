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

package stroom.widget.form.client;

/// Utility methods for file uploads.
public final class FileUploadUtil {

    private FileUploadUtil() {
        // Static utility
    }

    /// Gets the name of a file from the value of a file input.
    ///
    /// Browsers give a file input's value as a fake path, e.g. `C:\fakepath\data.txt` (whatever
    /// the platform), so that the user's real file system isn't revealed. Older browsers may give
    /// the real path. Either way the server only wants the file's name.
    ///
    /// @param fileInputValue The value of the file input. May be null.
    /// @return The part of `fileInputValue` after its last `\` or `/`, the whole value if it has
    /// neither, or null if `fileInputValue` is null.
    public static String getFileName(final String fileInputValue) {
        if (fileInputValue == null) {
            return null;
        }
        final int lastSeparator = Math.max(
                fileInputValue.lastIndexOf('\\'),
                fileInputValue.lastIndexOf('/'));
        return fileInputValue.substring(lastSeparator + 1);
    }
}
