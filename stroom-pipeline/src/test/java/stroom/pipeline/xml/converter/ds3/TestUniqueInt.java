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

package stroom.pipeline.xml.converter.ds3;


import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestUniqueInt extends StroomUnitTest {

    @Test
    void test() {
        final UniqueInt uniqueInt = new UniqueInt();

        assertThat(uniqueInt.getMax()).isEqualTo(-1);
        assertThat(uniqueInt.getArr()).isNull();
        assertThat(uniqueInt.toString()).isEqualTo("");

        uniqueInt.add(3);
        uniqueInt.add(1);
        uniqueInt.add(5);
        uniqueInt.add(10);
        uniqueInt.add(0);

        assertThat(uniqueInt.getMax()).isEqualTo(10);
        assertThat(uniqueInt.getArr().length).isEqualTo(5);
        assertThat(uniqueInt.toString()).isEqualTo("0,1,3,5,10");

        uniqueInt.add(4);
        uniqueInt.add(10);

        assertThat(uniqueInt.getMax()).isEqualTo(10);
        assertThat(uniqueInt.getArr().length).isEqualTo(6);
        assertThat(uniqueInt.toString()).isEqualTo("0,1,3,4,5,10");

        uniqueInt.add(22);
        uniqueInt.add(10);

        assertThat(uniqueInt.getMax()).isEqualTo(22);
        assertThat(uniqueInt.getArr().length).isEqualTo(7);
        assertThat(uniqueInt.toString()).isEqualTo("0,1,3,4,5,10,22");
    }
}
