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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestUploadReply {

    @Test
    void testSuccess_isTheServletsArgLine() {
        final UploadReply reply = UploadReply.success("res-1", "my import.zip");

        // As FileUploadResultHandler reads it
        final PropertyMap propertyMap = new PropertyMap();
        propertyMap.loadArgLine(reply.getResponseText());
        assertThat(propertyMap.isSuccess()).isTrue();
        final ResourceKey resourceKey = new ResourceKey(propertyMap);
        assertThat(resourceKey.getKey()).isEqualTo("res-1");
        // Spaces and '=' are escaped, as the servlet's PropertyMap does
        assertThat(resourceKey.getName()).isEqualTo("my import.zip");
        assertThat(reply.getResponseText()).startsWith(PropertyMap.MAGIC_MARKER).contains("my\\simport.zip");
        assertThat(reply.getFailureMessage()).isNull();
    }

    @Test
    void testFailure_isTheServletsFailureArgLine() {
        final UploadReply reply = UploadReply.failure("Disk full = no space");

        final PropertyMap propertyMap = new PropertyMap();
        propertyMap.loadArgLine(reply.getResponseText());
        assertThat(propertyMap.isSuccess()).isFalse();
        assertThat(propertyMap.get("exception")).isEqualTo("Disk full = no space");
    }

    @Test
    void testDeliverTo() {
        final List<String> calls = new ArrayList<>();
        final FileUploadCallback callback = new RecordingCallback(calls);

        UploadReply.success("k1", "a.txt").deliverTo(callback);
        UploadReply.failure("Bad").deliverTo(callback);
        UploadReply.networkError("Network error during upload").deliverTo(callback);
        UploadReply.httpError(413).deliverTo(callback);

        assertThat(calls).hasSize(4);
        assertThat(calls.get(0)).startsWith("success #PM#");
        assertThat(calls.get(1)).startsWith("success #PM#").contains("exception=Bad");
        assertThat(calls.get(2)).isEqualTo("failure Network error during upload");
        assertThat(calls.get(3)).isEqualTo("failure Upload failed (HTTP 413)");
    }

    @Test
    void testNetworkError_hasNoResponseText() {
        final UploadReply reply = UploadReply.networkError("Offline");

        assertThat(reply.getResponseText()).isNull();
        assertThat(reply.getFailureMessage()).isEqualTo("Offline");
    }

    @Test
    void testDelayed() {
        final UploadReply reply = UploadReply.success("k1", "a.txt");
        final UploadReply delayed = reply.delayed(500);

        assertThat(reply.getDelayMillis()).isZero();
        assertThat(delayed.getDelayMillis()).isEqualTo(500);
        assertThat(delayed.getResponseText()).isEqualTo(reply.getResponseText());
    }

    @Test
    void testInvalidArguments() {
        assertThatThrownBy(() -> UploadReply.httpError(200)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UploadReply.success("k1", "a").delayed(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UploadReply.success(null, "a")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> UploadReply.failure(null)).isInstanceOf(NullPointerException.class);
    }

    // --------------------------------------------------------------------------------


    /// Records what an upload's callback is told.
    static final class RecordingCallback implements FileUploadCallback {

        private final List<String> calls;

        RecordingCallback(final List<String> calls) {
            this.calls = calls;
        }

        @Override
        public void onUploadStart() {
            calls.add("start");
        }

        @Override
        public void onUploadSuccess(final String responseText) {
            calls.add("success " + responseText);
        }

        @Override
        public void onUploadFailure(final String message) {
            calls.add("failure " + message);
        }
    }
}
