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

package stroom.gwt.workbench.client.app.screen;

import stroom.gwt.workbench.client.app.rest.FixtureUploads;
import stroom.gwt.workbench.client.app.rest.RecordedUpload;
import stroom.widget.form.client.CustomFileUpload;
import stroom.widget.form.client.FileUploadCallback;
import stroom.widget.form.client.FileUploadTransport;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;

import java.util.Objects;

/// The [FileUploadTransport] a [ScreenHarness] installs in Stroom's [CustomFileUpload] (whose
/// default posts the file with an `XMLHttpRequest`), so a screen's file uploads are answered by
/// the story's fixtures ([FixtureUploads]) and never reach the network.
///
/// It reads the chosen file (its name and, for a small text file, its content, with a
/// `FileReader`, which is asynchronous) and then passes the upload, with its URL relative to the
/// host page (e.g. `importfile.rpc`), to the fixtures. Once disposed, a file still being read is
/// dropped, as is any later upload.
///
/// [CustomFileUpload]'s transport is static, so the most recently installed transport answers
/// every upload; a harness uninstalls its own when the story renders again, restoring Stroom's
/// default.
public final class StoryUploadTransport implements FileUploadTransport {

    private static StoryUploadTransport installed;

    private final FixtureUploads uploads;
    private boolean disposed;

    /// @param uploads The rendering's fake uploads.
    public StoryUploadTransport(final FixtureUploads uploads) {
        this.uploads = Objects.requireNonNull(uploads);
    }

    /// Makes every [CustomFileUpload] upload with this transport, until it is disposed or another
    /// is installed.
    public void install() {
        installed = this;
        CustomFileUpload.setUploadTransport(this);
    }

    /// Drops the files still being read and any later upload, and restores Stroom's default
    /// transport if this is the one installed.
    public void dispose() {
        disposed = true;
        uploads.dispose();
        if (installed == this) {
            installed = null;
            CustomFileUpload.setUploadTransport(null);
        }
    }

    /// Reads the file chosen in the input, then answers the upload from the fixtures. With no file
    /// chosen it fails at once, as Stroom's transport does.
    ///
    /// @param url              The upload endpoint, e.g. `ImportUtil.getImportFileURL()`.
    /// @param fileInputElement The `<input type="file">` holding the chosen file.
    /// @param callback         Stroom's callback for the upload.
    @Override
    public void upload(final String url, final Element fileInputElement, final FileUploadCallback callback) {
        final String relativeUrl = StoryLocationManager.relativeTo(url, GWT.getHostPageBaseURL());
        readFile(fileInputElement, RecordedUpload.MAX_CONTENT_BYTES, new PendingRead(relativeUrl, callback));
    }

    // Reads the first file of the input as text, if it is small enough, then calls back. FileReader
    // replaces bytes that aren't UTF-8 with U+FFFD, which RecordedUpload.textContent looks for.
    private static native void readFile(Element input, int maxBytes, PendingRead pendingRead) /*-{
        var files = input.files;
        if (!files || files.length === 0) {
            pendingRead.@stroom.gwt.workbench.client.app.screen.StoryUploadTransport.PendingRead::onNoFile()();
            return;
        }
        var file = files[0];
        var done = $entry(function (text) {
            pendingRead.@stroom.gwt.workbench.client.app.screen.StoryUploadTransport.PendingRead::onRead(*)(
                file.name, text, file.size);
        });
        if (file.size > maxBytes) {
            done(null);
            return;
        }
        var reader = new $wnd.FileReader();
        reader.onload = function () {
            done(reader.result);
        };
        reader.onerror = function () {
            done(null);
        };
        reader.readAsText(file);
    }-*/;

    // --------------------------------------------------------------------------------


    /// An upload whose file is being read.
    private final class PendingRead {

        private final String url;
        private final FileUploadCallback callback;

        private PendingRead(final String url, final FileUploadCallback callback) {
            this.url = url;
            this.callback = callback;
        }

        // As Stroom's transport, which fails when no file is chosen
        private void onNoFile() {
            if (!disposed) {
                callback.onUploadFailure("No file selected");
            }
        }

        private void onRead(final String fileName, final String text, final double size) {
            // A disposed FixtureUploads reports the upload as dropped
            uploads.upload(new RecordedUpload(url, fileName, RecordedUpload.textContent(text, size)), callback);
        }
    }
}
