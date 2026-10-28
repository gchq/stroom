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

package stroom.pipeline.structure.client.component;

import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.svg.shared.SvgImage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestPipelineElementBoxFactory {

    @Test
    void testGetIconName_displayValue() {
        assertThat(PipelineElementBoxFactory.getIconName(elementType("XSLTFilter", "XSLT Filter")))
                .isEqualTo("XSLT Filter");
    }

    @Test
    void testGetIconName_noDisplayValue() {
        assertThat(PipelineElementBoxFactory.getIconName(elementType("XSLTFilter", null)))
                .isEqualTo("XSLTFilter");
        assertThat(PipelineElementBoxFactory.getIconName(elementType("XSLTFilter", " ")))
                .isEqualTo("XSLTFilter");
    }

    @Test
    void testGetIconName_noType() {
        assertThat(PipelineElementBoxFactory.getIconName(null))
                .isNull();
    }

    private static PipelineElementType elementType(final String type, final String displayValue) {
        return new PipelineElementType(
                type, displayValue, Category.FILTER, new String[0], SvgImage.PIPELINE_XSLT);
    }
}
