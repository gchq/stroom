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

import stroom.util.shared.PropertyMap;
import stroom.util.shared.ResourceKey;
import stroom.widget.form.client.FileUploadCallback;

import java.util.Objects;

/// The fake reply to a file upload, the upload equivalent of [RestReply]. Stroom's
/// `CustomFileUpload` posts the chosen file to the import file servlet
/// (`ImportUtil.getImportFileURL()`, `importfile.rpc`), which replies with a `PropertyMap` arg line
/// (`#PM#success=true name=a.zip key=k1#PM#`) that Stroom's `FileUploadResultHandler` reads into a
/// `ResourceKey`, or with `success=false` and the server's exception message. Replies are
/// immutable, so can be shared by stories.
///
/// * [#success(String, String)]: the file was stored, with this resource key;
/// * [#failure(String)]: the servlet failed (`success=false`), so the screen's failure handler is
///   given the exception message;
/// * [#networkError(String)] and [#httpError(int)]: the upload never reached the servlet, as
///   Stroom's `XMLHttpRequest` transport reports them.
public final class UploadReply {

    private static final String EXCEPTION = "exception";

    private final Kind kind;
    private final String text;
    private final int delayMillis;

    private UploadReply(final Kind kind, final String text, final int delayMillis) {
        this.kind = kind;
        this.text = text;
        this.delayMillis = delayMillis;
    }

    /// @param key  The resource key the servlet gives the stored file, e.g. `res-1`.
    /// @param name The stored file's name, usually the uploaded file's, e.g. `import.zip`.
    /// @return The servlet's reply for a stored file.
    public static UploadReply success(final String key, final String name) {
        final PropertyMap propertyMap = new PropertyMap();
        propertyMap.setSuccess(true);
        propertyMap.put(ResourceKey.NAME, Objects.requireNonNull(name, "name"));
        propertyMap.put(ResourceKey.KEY, Objects.requireNonNull(key, "key"));
        return new UploadReply(Kind.RESPONSE, propertyMap.toArgLine(), 0);
    }

    /// @param exceptionMessage The message of the exception the servlet caught, which Stroom shows
    ///                         to the user.
    /// @return The servlet's reply when storing the file failed.
    public static UploadReply failure(final String exceptionMessage) {
        final PropertyMap propertyMap = new PropertyMap();
        propertyMap.setSuccess(false);
        propertyMap.put(EXCEPTION, Objects.requireNonNull(exceptionMessage, "exceptionMessage"));
        return new UploadReply(Kind.RESPONSE, propertyMap.toArgLine(), 0);
    }

    /// @param message The message Stroom's failure handler is given, e.g. Stroom's own
    ///                `Network error during upload`.
    /// @return A reply for an upload that never reached the server.
    public static UploadReply networkError(final String message) {
        return new UploadReply(Kind.FAILURE, Objects.requireNonNull(message, "message"), 0);
    }

    /// @param status A status other than 2xx, e.g. `413`.
    /// @return A reply for an upload the server refused, which Stroom's transport reports as
    /// `Upload failed (HTTP <status>)`.
    public static UploadReply httpError(final int status) {
        if (status >= 200 && status < 300) {
            throw new IllegalArgumentException("A 2xx status is a successful upload: use success or failure");
        }
        return networkError("Upload failed (HTTP " + status + ")");
    }

    /// @param millis How long to wait before replying, e.g. to show the upload in progress.
    /// @return A copy of this reply, delayed.
    public UploadReply delayed(final int millis) {
        if (millis < 0) {
            throw new IllegalArgumentException("The delay can't be negative: " + millis);
        }
        return new UploadReply(kind, text, millis);
    }

    /// Gives the reply to the upload's callback, as Stroom's transport does: the servlet's
    /// response text, or the failure's message.
    ///
    /// @param callback The callback of the upload.
    public void deliverTo(final FileUploadCallback callback) {
        if (kind == Kind.RESPONSE) {
            callback.onUploadSuccess(text);
        } else {
            callback.onUploadFailure(text);
        }
    }

    /// @return The servlet's response text (a `PropertyMap` arg line), or null for an upload that
    /// never reached it.
    public String getResponseText() {
        return kind == Kind.RESPONSE
                ? text
                : null;
    }

    /// @return The failure message for an upload that never reached the servlet, or null.
    public String getFailureMessage() {
        return kind == Kind.FAILURE
                ? text
                : null;
    }

    /// @return How long to wait before replying.
    public int getDelayMillis() {
        return delayMillis;
    }

    @Override
    public String toString() {
        return kind == Kind.RESPONSE
                ? "UploadReply " + text
                : "UploadReply failure: " + text;
    }

    // --------------------------------------------------------------------------------


    /// Whether the servlet replied or the upload failed before reaching it.
    private enum Kind {
        RESPONSE,
        FAILURE
    }
}
