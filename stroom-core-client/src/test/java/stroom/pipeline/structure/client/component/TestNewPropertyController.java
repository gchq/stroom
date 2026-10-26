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

import stroom.pipeline.shared.data.PipelinePropertyType;
import stroom.pipeline.structure.client.component.NewProperty.ValueEditor;

import com.google.gwt.junit.GWTMockUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TestNewPropertyController {

    @BeforeEach
    void setUp() {
        // The controller's REST resources are made with GWT.create, which only works in a browser
        GWTMockUtilities.disarm();
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @ParameterizedTest
    @CsvSource({
            // The stream type and volume group are chosen by name, whatever their type
            "streamType, String, STREAM_TYPE",
            "volumeGroup, String, VOLUME_GROUP",
            "fixInvalidChars, boolean, BOOLEAN",
            "maxSize, int, NUMBER",
            "rollSize, long, NUMBER",
            "pathPattern, String, TEXT",
            "xslt, DocRef, DOCUMENT",
            "reference, PipelineReference, DOCUMENT"})
    void testEditorFor(final String name, final String type, final ValueEditor expected) {
        final PipelinePropertyType propertyType = PipelinePropertyType.builder()
                .name(name)
                .type(type)
                .build();
        assertThat(NewPropertyController.editorFor(propertyType)).isEqualTo(expected);
    }
}
