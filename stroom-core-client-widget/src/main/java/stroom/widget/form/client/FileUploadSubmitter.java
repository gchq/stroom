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

package stroom.widget.form.client;

import com.google.gwt.dom.client.Element;

/**
 * Uploads the file selected in a native {@code <input type="file">} element via
 * {@code XMLHttpRequest} using a multipart {@code FormData} body.
 * <p>
 * Unlike a native HTML form submission (as done by GWT's {@code FormPanel}), an
 * {@code XMLHttpRequest} lets us attach the custom {@code X-CSRF} header that the server's
 * security filter requires for session-authenticated, state-changing requests. Cross-site
 * scripts cannot set custom headers, so the header proves the request came from our own UI.
 */
final class FileUploadSubmitter {

    /**
     * Must match {@code ImportFileServlet.FILE_UPLOAD_PROP_NAME} on the server.
     */
    private static final String FILE_FIELD_NAME = "fileUpload";
    private static final String CSRF_HEADER = "X-CSRF";
    private static final String CSRF_VALUE = "1";

    private FileUploadSubmitter() {
        // Utility class.
    }

    /**
     * Upload the file currently selected in the given file input element to the given URL.
     *
     * @param url              The upload endpoint (same-origin as the app).
     * @param fileInputElement The {@code <input type="file">} element holding the selected file.
     * @param callback         Notified of the start, success or failure of the upload.
     */
    static void submit(final String url,
                       final Element fileInputElement,
                       final FileUploadCallback callback) {
        callback.onUploadStart();
        doSubmit(url, fileInputElement, FILE_FIELD_NAME, CSRF_HEADER, CSRF_VALUE, callback);
    }

    private static native void doSubmit(final String url,
                                        final Element fileInput,
                                        final String fieldName,
                                        final String csrfHeader,
                                        final String csrfValue,
                                        final FileUploadCallback callback) /*-{
        var files = fileInput.files;
        if (!files || files.length === 0) {
            callback.@stroom.widget.form.client.FileUploadCallback::onUploadFailure(Ljava/lang/String;)(
                "No file selected");
            return;
        }

        var formData = new FormData();
        formData.append(fieldName, files[0]);

        var xhr = new XMLHttpRequest();
        xhr.open("POST", url, true);
        // Send the session cookie and attach the CSRF header the server requires.
        xhr.withCredentials = true;
        xhr.setRequestHeader(csrfHeader, csrfValue);

        xhr.onload = function () {
            if (xhr.status >= 200 && xhr.status < 300) {
                callback.@stroom.widget.form.client.FileUploadCallback::onUploadSuccess(Ljava/lang/String;)(
                    xhr.responseText);
            } else {
                callback.@stroom.widget.form.client.FileUploadCallback::onUploadFailure(Ljava/lang/String;)(
                    "Upload failed (HTTP " + xhr.status + ")");
            }
        };
        xhr.onerror = function () {
            callback.@stroom.widget.form.client.FileUploadCallback::onUploadFailure(Ljava/lang/String;)(
                "Network error during upload");
        };

        xhr.send(formData);
    }-*/;
}
