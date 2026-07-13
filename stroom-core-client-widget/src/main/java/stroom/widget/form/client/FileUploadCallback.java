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

/**
 * Callback for an asynchronous file upload performed by {@link FileUploadSubmitter}.
 */
interface FileUploadCallback {

    /**
     * Called just before the upload request is sent.
     */
    void onUploadStart();

    /**
     * Called when the server responds with a success (2xx) status.
     *
     * @param responseText The raw response body returned by the upload servlet.
     */
    void onUploadSuccess(String responseText);

    /**
     * Called when the upload could not be completed, e.g. a non-2xx response or a network error.
     *
     * @param message A human-readable description of the failure.
     */
    void onUploadFailure(String message);
}
