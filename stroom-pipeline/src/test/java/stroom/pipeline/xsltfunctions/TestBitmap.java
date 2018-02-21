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

package stroom.pipeline.server.xsltfunctions;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;

import java.util.Arrays;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestBitmap extends StroomUnitTest {
    @Test
    public void testBitmap() {
        int value = 0x1001;
        int bit = 0;
        while (value > 0) {
            System.out.println("bit " + bit + " = " + (value & 1));
            value = value >> 1;
            bit++;
        }

        Assert.assertTrue(Arrays.equals(Bitmap.getBits(0x1001), new int[]{0, 12}));
    }
}
