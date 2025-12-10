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

package stroom.receive.common;


import stroom.meta.api.AttributeMap;
import stroom.util.date.DateUtil;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestStreamFactory {

    @Test
    void testSimple() {
        final String testDate = "2000-01-01T00:00:00.000Z";
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("effectivetime", testDate);

        final Long time = StreamFactory.getReferenceEffectiveTime(attributeMap, true);

        assertThat(DateUtil.createNormalDateTimeString(time)).isEqualTo(testDate);
    }
}
