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

import com.google.gwt.dom.client.Element;

import java.util.function.Consumer;

/// Reads a text file the user chose in a file input, in the browser, so it never goes to the server
/// on its own (e.g. CSV loaded into an editor).
public final class LocalTextFile {

    private LocalTextFile() {
        // Static utility
    }

    /// Reads the first file chosen in a file input as UTF-8 text. The file is taken from the input
    /// straight away, so the input may be cleared as soon as this returns. A file larger than the
    /// given size is not read.
    ///
    /// @param fileInput The `input type="file"` element.
    /// @param maxBytes  The largest file to read.
    /// @param onText    Given the text once read.
    /// @param onFailure Given what is wrong if no file is chosen, it is too large, or it can't be read.
    public static native void read(Element fileInput,
                                   double maxBytes,
                                   Consumer<String> onText,
                                   Consumer<String> onFailure) /*-{
        var files = fileInput.files;
        if (!files || files.length === 0) {
            onFailure.@java.util.function.Consumer::accept(Ljava/lang/Object;)("No file chosen");
            return;
        }
        var file = files[0];
        var name = file.name;
        if (file.size > maxBytes) {
            onFailure.@java.util.function.Consumer::accept(Ljava/lang/Object;)(
                "The file '" + name + "' is too large to load.");
            return;
        }
        var reader = new $wnd.FileReader();
        reader.onload = $entry(function () {
            onText.@java.util.function.Consumer::accept(Ljava/lang/Object;)(reader.result);
        });
        reader.onerror = $entry(function () {
            onFailure.@java.util.function.Consumer::accept(Ljava/lang/Object;)(
                "The file '" + name + "' could not be read.");
        });
        reader.readAsText(file, "UTF-8");
    }-*/;
}
