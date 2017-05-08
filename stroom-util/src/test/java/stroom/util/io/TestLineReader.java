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

package stroom.util.io;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import stroom.util.test.StroomJUnit4ClassRunner;

import java.io.ByteArrayInputStream;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestLineReader {
    @Test
    public void test() throws Exception {
        final StringBuilder in = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            in.append(i);
            in.append(" this is a line ");
            in.append(i);
            in.append("\n");
        }

        final ByteArrayInputStream inputStream = new ByteArrayInputStream(in.toString().getBytes("UTF-8"));
        final LineReader lineReader = new LineReader(inputStream, "UTF-8");
        String line = null;

        final StringBuilder out = new StringBuilder();
        while ((line = lineReader.nextLine()) != null) {
            out.append(line);
            out.append("\n");
        }

        Assert.assertEquals(in.toString(), out.toString());
    }
}
