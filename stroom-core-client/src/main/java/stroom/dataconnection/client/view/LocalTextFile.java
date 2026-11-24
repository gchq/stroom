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

package stroom.dataconnection.client.view;

import com.google.gwt.dom.client.Element;

import java.util.function.Consumer;

/// Reads a text file the user chose in a file input, in the browser, so it never goes to the server
/// on its own.
final class LocalTextFile {

    private LocalTextFile() {
    }

    /// Reads the first file chosen in a file input as UTF-8 text.
    ///
    /// The file is taken from the input straight away, so the input may be cleared as soon as this
    /// returns. A file larger than the given size is not read, as the whole of it would go into the
    /// editor before the server could refuse it.
    ///
    /// @param fileInput The `input type="file"` element.
    /// @param maxBytes  The largest file to read.
    /// @param onText    Takes the text once read.
    /// @param onError   Takes a message if the file is too large or cannot be read.
    static native void read(Element fileInput,
                            double maxBytes,
                            Consumer<String> onText,
                            Consumer<String> onError) /*-{
        var files = fileInput.files;
        if (!files || files.length === 0) {
            return;
        }
        var file = files[0];
        var name = file.name;
        if (file.size > maxBytes) {
            onError.@java.util.function.Consumer::accept(Ljava/lang/Object;)(
                "The file '" + name + "' is too large to load.");
            return;
        }
        var reader = new $wnd.FileReader();
        reader.onload = $entry(function () {
            onText.@java.util.function.Consumer::accept(Ljava/lang/Object;)(reader.result);
        });
        reader.onerror = $entry(function () {
            onError.@java.util.function.Consumer::accept(Ljava/lang/Object;)(
                "The file '" + name + "' could not be read.");
        });
        reader.readAsText(file, "UTF-8");
    }-*/;
}
