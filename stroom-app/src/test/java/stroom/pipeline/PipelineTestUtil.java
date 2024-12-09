/*
 * Copyright 2017 Crown Copyright
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
import stroom.docstore.impl.Serialiser2FactoryImpl;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;

public final class PipelineTestUtil {

    private static final PipelineSerialiser SERIALISER = new PipelineSerialiser(new Serialiser2FactoryImpl());

    private PipelineTestUtil() {
    }

    public static PipelineDoc createBasicPipeline(final String data) {
        PipelineDoc pipelineDoc = new PipelineDoc();
        pipelineDoc.setName("test");
        pipelineDoc.setDescription("test");
        if (data != null) {
            final PipelineData pipelineData = SERIALISER.getPipelineDataFromXml(data);
            pipelineDoc.setPipelineData(pipelineData);
        }
        return pipelineDoc;
    }

    public static DocRef createTestPipeline(final PipelineStore pipelineStore, final String data) {
        return createTestPipeline(pipelineStore, "test", "test", data);
    }

    public static DocRef createTestPipeline(final PipelineStore pipelineStore, final String name,
                                            final String description, final String data) {
        final PipelineDoc pipelineDoc = pipelineStore.createDocument();
        pipelineDoc.setName(name);
        return createTestPipeline(pipelineStore, pipelineDoc, name, description, data);
    }

    public static DocRef createTestPipeline(final PipelineStore pipelineStore,
                                            final PipelineDoc pipelineDoc,
                                            final String name,
                                            final String description,
                                            final String data) {
        pipelineDoc.setName(name);
        pipelineDoc.setDescription(description);
        if (data != null) {
            final PipelineData pipelineData = SERIALISER.getPipelineDataFromXml(data);
            pipelineDoc.setPipelineData(pipelineData);
        }
        return pipelineStore.writeDocument(pipelineDoc).asDocRef();
    }

}
