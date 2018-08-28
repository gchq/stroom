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

package stroom.config.app;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookupFactory;
import stroom.util.io.StreamUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class YamlUtil {
    private YamlUtil() {
        // Utility
    }

    public static AppConfig read(final InputStream inputStream) throws IOException {
        final String string = StreamUtil.streamToString(inputStream, StandardCharsets.UTF_8);
        final StringSubstitutor substitutor = new StringSubstitutor(StringLookupFactory.INSTANCE.environmentVariableStringLookup());
        final String substituted = substitutor.replace(string);
        final InputStream substitutedInputStream = new ByteArrayInputStream(substituted.getBytes(StandardCharsets.UTF_8));

        final YAMLFactory yf = new YAMLFactory();
        final ObjectMapper mapper = new ObjectMapper(yf);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        final WrapperConfig config = mapper.readerFor(WrapperConfig.class).readValue(substitutedInputStream);
        final AppConfig appConfig = config.getAppConfig();


//        final YAMLFactory yf = new YAMLFactory();
//        final ObjectMapper mapper = new ObjectMapper(yf);
//        final AppConfig config = mapper.readerFor(AppConfig.class).readValue(inputStream);
//        FieldMapper.copy(config, appConfig);

        return appConfig;
    }

    public static void write(final AppConfig appConfig, final OutputStream outputStream) throws IOException {
        final YAMLFactory yf = new YAMLFactory();
        final ObjectMapper mapper = new ObjectMapper(yf);
        mapper.writeValue(outputStream, appConfig);
    }
}
