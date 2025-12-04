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

package stroom.pipeline;

import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.util.json.JsonUtil;

public final class PipelineTestUtil {

    private PipelineTestUtil() {
    }

    public static PipelineDoc createBasicPipeline(final String data) {
        final PipelineDoc.Builder builder = PipelineDoc.builder()
                .uuid("test")
                .name("test")
                .description("test");
        if (data != null) {
            final PipelineData pipelineData = JsonUtil.readValue(data, PipelineData.class);
            builder.pipelineData(pipelineData);
        }
        return builder.build();
    }

    public static DocRef createTestPipeline(final PipelineStore pipelineStore, final String data) {
        return createTestPipeline(pipelineStore, "test", "test", data);
    }

    public static DocRef createTestPipeline(final PipelineStore pipelineStore, final String name,
                                            final String description, final String data) {
        final DocRef docRef = pipelineStore.createDocument(name);
        return createTestPipeline(pipelineStore, docRef, name, description, data);
    }

    public static DocRef createTestPipeline(final PipelineStore pipelineStore,
                                            final DocRef docRef,
                                            final String name,
                                            final String description,
                                            final String data) {
        final PipelineDoc pipelineDoc = pipelineStore.readDocument(docRef);
        pipelineDoc.setName(name);
        pipelineDoc.setDescription(description);
        if (data != null) {
            final PipelineData pipelineData = JsonUtil.readValue(data, PipelineData.class);
            pipelineDoc.setPipelineData(pipelineData);
        }
        pipelineStore.writeDocument(pipelineDoc);
        return docRef;
    }
}
