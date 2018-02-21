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

package stroom.dashboard;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.Assert;
import org.junit.Test;
import stroom.dashboard.vis.VisSettings;
import stroom.test.StroomPipelineTestFileUtil;
import stroom.util.io.StreamUtil;

import java.nio.file.Files;
import java.nio.file.Path;

public class TestVisSettings {
    @Test
    public void test() throws Exception {
        final Path jsonFile = StroomPipelineTestFileUtil.getTestResourcesFile("TestVisSettings/settings.json");
        Assert.assertTrue(Files.isRegularFile(jsonFile));
        final String json = StreamUtil.fileToString(jsonFile);

        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
        mapper.setSerializationInclusion(Include.NON_NULL);
        final VisSettings visSettings = mapper.readValue(json, VisSettings.class);

        final String output = mapper.writeValueAsString(visSettings);

        String in = json.replaceAll("\\s*", "");
        String out = output.replaceAll("\\s*", "");

        // JSON should be the same up to 'interpolationMode'
        final int index = in.indexOf("interpolationMode");
        if (index != -1) {
            in = in.substring(0, index);
            out = out.substring(0, index);
        }

        System.out.println(in);
        System.out.println(out);

        Assert.assertEquals(in, out);
    }
}
