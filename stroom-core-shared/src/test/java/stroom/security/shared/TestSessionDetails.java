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

package stroom.security.shared;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.UserRef;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TestSessionDetails {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestSessionDetails.class);

    @Test
    void test() throws JsonProcessingException {
        final SessionDetails sessionDetails1 = new SessionDetails(
                UserRef
                        .builder()
                        .uuid(UUID.randomUUID().toString())
                        .subjectId(UUID.randomUUID().toString())
                        .displayName("jbloggs")
                        .fullName("Jow Bloggs")
                        .build(),
                0,
                0,
                "agent",
                "node1");

        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        final String json1 = objectMapper.writeValueAsString(sessionDetails1);

        LOGGER.info("json1:\n{}", json1);

        final SessionDetails sessionDetails2 = objectMapper.readValue(json1, SessionDetails.class);

        assertThat(sessionDetails2)
                .isEqualTo(sessionDetails1);

        final String json2 = objectMapper.writeValueAsString(sessionDetails2);

        assertThat(json2)
                .isEqualTo(json1);
    }
}
