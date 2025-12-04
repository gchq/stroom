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

package stroom.dashboard.impl;

import stroom.dashboard.impl.vis.VisSettings;
import stroom.util.io.StreamUtil;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class TestVisSettings {

    @Test
    void test() throws Exception {
        final Path jsonFile = Paths.get(
                getClass().getClassLoader().getResource("TestVisSettings/settings.json").toURI());
        assertThat(Files.isRegularFile(jsonFile))
                .isTrue();
        final String json = StreamUtil.fileToString(jsonFile);

        final VisSettings visSettings = JsonUtil.readValue(json, VisSettings.class);
        final String output = JsonUtil.writeValueAsString(visSettings);

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

        assertThat(out)
                .isEqualTo(in);
    }
}
