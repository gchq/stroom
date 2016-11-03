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

package stroom.util.config;

import java.io.File;

import stroom.util.test.StroomUnitTest;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import stroom.util.test.StroomJUnit4ClassRunner;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestConfigure extends StroomUnitTest {
    @Test
    public void testNotOK() {
        final Parameter parameter = new Parameter();
        parameter.setValue("some.value");
        parameter.setRegEx("[a-zA-Z0-9-]+");
        Assert.assertFalse(parameter.validate());
    }

    @Test
    public void test_marshal() {
        final ParameterFile list = new ParameterFile();
        list.getParameter().add(new Parameter("param1", "param2", "param3", null));
        list.getParameter().add(new Parameter("param4", "param5", "param6", "A"));

        final Configure configure = new Configure();
        configure.marshal(list, System.out);
    }

    @Test
    public void test_Main() throws Exception {
        final File testFile = new File(getCurrentTestDir(), "TestConfigure_server.xml");
        final File sourceFile = new File("./src/test/resources/stroom/util/config/server.xml");
        testFile.delete();
        FileUtils.copyFile(sourceFile, testFile);

        Configure.main(new String[] {
                "parameterFile=./src/test/resources/stroom/util/config/ConfigureTestParameters.xml",
                "processFile=" + testFile.getAbsolutePath(), "readParameter=false", "exitOnError=false" });
    }
}
