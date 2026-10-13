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

/// Sends the file chosen in a [CustomFileUpload] to the server. Stroom's own transport uploads it
/// with an `XMLHttpRequest` (see `FileUploadSubmitter`); another can be set with
/// [CustomFileUpload#setUploadTransport(FileUploadTransport)], e.g. to fake uploads in tests.
@FunctionalInterface
public interface FileUploadTransport {

    /// Uploads the file selected in a file input element. The transport must report the outcome
    /// to the callback with [FileUploadCallback#onUploadSuccess(String)] or
    /// [FileUploadCallback#onUploadFailure(String)], but not [FileUploadCallback#onUploadStart()],
    /// which the caller has already called.
    ///
    /// @param url              The upload endpoint.
    /// @param fileInputElement The `<input type="file">` element holding the selected file.
    /// @param callback         Notified of the success or failure of the upload.
    void upload(String url, Element fileInputElement, FileUploadCallback callback);
}
