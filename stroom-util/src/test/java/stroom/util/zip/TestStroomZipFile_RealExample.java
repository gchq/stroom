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

package stroom.util.zip;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

import org.junit.Test;
import org.junit.runner.RunWith;

import stroom.util.test.StroomJUnit4ClassRunner;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestStroomZipFile_RealExample {
    @Test
    public void testRealZip1() throws IOException {
        File sourceFile = new File("./src/test/resources/stroom/util/zip/BlankZip.zip");
        StroomZipFile stroomZipFile = new StroomZipFile(sourceFile);

        ArrayList<String> list = new ArrayList<>(stroomZipFile.getStroomZipNameSet().getBaseNameList());
        Collections.sort(list);

        stroomZipFile.close();
    }

}
