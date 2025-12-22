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

package stroom.util.config;


import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestParameter {

    @Test
    void testNodeName() {
        final Parameter parameter = new Parameter();
        parameter.setRegEx("[a-zA-Z0-9-_]+");

        parameter.setValue("");
        assertThat(parameter.validate()).isFalse();

        parameter.setValue("bad.value");
        assertThat(parameter.validate()).isFalse();

        parameter.setValue("goodValue");
        assertThat(parameter.validate()).isTrue();

        parameter.setValue("goodValue123");
        assertThat(parameter.validate()).isTrue();

        parameter.setValue("UNUSUAL-123_goodValue");
        assertThat(parameter.validate()).isTrue();

    }

    @Test
    void testPortPrefix() {
        final Parameter parameter = new Parameter();
        parameter.setRegEx("[0-9]{2}");

        parameter.setValue("1");
        assertThat(parameter.validate()).isFalse();

        parameter.setValue("999");
        assertThat(parameter.validate()).isFalse();

        parameter.setValue("12");
        assertThat(parameter.validate()).isTrue();

        parameter.setValue("99");
        assertThat(parameter.validate()).isTrue();

    }

}
