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

import org.junit.Assert;
import org.junit.Test;
import stroom.util.test.StroomUnitTest;

public class TestNumericIP extends StroomUnitTest {
    @Test
    public void test() {
        final NumericIP numericIP = new NumericIP();

        String out = numericIP.convert("192.168.1.1");
        System.out.println(out);
        Assert.assertEquals("3232235777", out);
        out = numericIP.convert("192.168.1.2");
        System.out.println(out);
        Assert.assertEquals("3232235778", out);
        out = numericIP.convert("255.255.255.255");
        System.out.println(out);
        Assert.assertEquals("4294967295", out);
        out = numericIP.convert("0.0.0.0");
        System.out.println(out);
        Assert.assertEquals("0", out);
        out = numericIP.convert("1.1.1.1");
        System.out.println(out);
        Assert.assertEquals("16843009", out);
    }
}
