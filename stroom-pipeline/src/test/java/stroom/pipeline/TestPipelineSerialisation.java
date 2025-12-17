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


import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestPipelineSerialisation {

    private static final PipelineElementType ELEM_TYPE = new PipelineElementType(
            "TestElement",
            "Test Element",
            null,
            new String[]{PipelineElementType.ROLE_TARGET, PipelineElementType.ROLE_HAS_TARGETS}, null);

    @Test
    void testEmpty() {
        final PipelineData pipelineData = new PipelineDataBuilder().build();
        final String string = JsonUtil.writeValueAsString(pipelineData);
        assertThat(string.trim()).isEqualTo("{ }");
    }

    @Test
    void testElements() {
        final PipelineData pipelineData = new PipelineDataBuilder()
                .addElement(new PipelineElement("test1", ELEM_TYPE.getType(), "test1Name", null))
                .build();
        final String string = JsonUtil.writeValueAsString(pipelineData);
        assertThat(string.trim()).isEqualTo("""
                {
                  "elements" : {
                    "add" : [ {
                      "id" : "test1",
                      "type" : "TestElement",
                      "name" : "test1Name"
                    } ]
                  }
                }""");
    }
}
