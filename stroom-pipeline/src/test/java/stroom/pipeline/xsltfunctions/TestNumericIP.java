/*
 * Copyright 2016 Crown Copyright
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

package stroom.pipeline.xsltfunctions;


import org.junit.jupiter.api.Test;
import stroom.util.test.StroomUnitTest;

import static org.assertj.core.api.Assertions.assertThat;

class TestNumericIP extends StroomUnitTest {
    @Test
    void test() {
        final NumericIP numericIP = new NumericIP();

        String out = numericIP.convert("192.168.1.1");
        System.out.println(out);
        assertThat(out).isEqualTo("3232235777");
        out = numericIP.convert("192.168.1.2");
        System.out.println(out);
        assertThat(out).isEqualTo("3232235778");
        out = numericIP.convert("255.255.255.255");
        System.out.println(out);
        assertThat(out).isEqualTo("4294967295");
        out = numericIP.convert("0.0.0.0");
        System.out.println(out);
        assertThat(out).isEqualTo("0");
        out = numericIP.convert("1.1.1.1");
        System.out.println(out);
        assertThat(out).isEqualTo("16843009");
    }
}
