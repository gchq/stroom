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

package stroom.config.global.shared;

import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class TestOverrideValue {

    // No LOGGER due to gwt

    @Test
    void testSerde_withValueString() throws IOException {
        doSerdeTest(OverrideValue.with("someValue"), OverrideValue.class);
    }

    @Test
    void testSerde_withValueInt() throws IOException {
        doSerdeTest(OverrideValue.with(123), OverrideValue.class);
    }

    @Test
    void testSerde_withNull() throws IOException {
        doSerdeTest(OverrideValue.with(null), OverrideValue.class);
    }

    @Test
    void testSerde_unset() throws IOException {
        doSerdeTest(OverrideValue.unSet(String.class), OverrideValue.class);
    }

    private <T> void doSerdeTest(final T entity, final Class<T> clazz) throws IOException {

        final JsonMapper mapper = JsonUtil.getMapper();
        final String json = mapper.writeValueAsString(entity);
        System.out.println("\n" + json);

        final T entity2 = (T) mapper.readValue(json, clazz);

        assertThat(entity2).isEqualTo(entity);
    }
}
