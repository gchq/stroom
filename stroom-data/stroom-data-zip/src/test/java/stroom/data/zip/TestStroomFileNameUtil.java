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

package stroom.data.zip;


import stroom.meta.api.AttributeMap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestStroomFileNameUtil {

    @Test
    void testPad() {
        assertThat(StroomFileNameUtil.getIdPath(1))
                .isEqualTo("001");
        assertThat(StroomFileNameUtil.getIdPath(999))
                .isEqualTo("999");
        assertThat(StroomFileNameUtil.getIdPath(1000))
                .isEqualTo("001/001000");
        assertThat(StroomFileNameUtil.getIdPath(1999))
                .isEqualTo("001/001999");
        assertThat(StroomFileNameUtil.getIdPath(9111999))
                .isEqualTo("009/111/009111999");
    }

    @Test
    void testConstructFilename() {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("feed", "myFeed");
        attributeMap.put("var1", "myVar1");

        final String standardTemplate = "${pathId}/${id}";
        final String staticTemplate = "${pathId}/${id} someStaticText";
        final String dynamicTemplate = "${id} ${var1} ${feed}";

        final String extension1 = ".zip";
        final String extension2 = ".bad";

        assertThat(StroomFileNameUtil.constructFilename(
                null, 1, standardTemplate, attributeMap, extension1, extension2))
                .isEqualTo("001.zip.bad");
        assertThat(StroomFileNameUtil.constructFilename(
                null, 3000, standardTemplate, attributeMap, extension1))
                .isEqualTo("003/003000.zip");
        assertThat(StroomFileNameUtil.constructFilename(
                null, 3000, dynamicTemplate, attributeMap, extension1))
                .isEqualTo("003000_myVar1_myFeed.zip");
        assertThat(StroomFileNameUtil.constructFilename(
                null, 3000, staticTemplate, attributeMap, extension1))
                .isEqualTo("003/003000_someStaticText.zip");
        assertThat(StroomFileNameUtil.constructFilename(
                null, 3000, staticTemplate, attributeMap))
                .isEqualTo("003/003000_someStaticText");
    }
}
