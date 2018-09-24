/*
 * Copyright 2018 Crown Copyright
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

package stroom.activity.server;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.activity.shared.Activity;
import stroom.activity.shared.Activity.ActivityDetails;

import java.io.IOException;

class ActivitySerialiser {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActivitySerialiser.class);

    static void serialise(final Activity activity) {
        try {
            if (activity != null) {
                if (activity.getDetails() == null) {
                    activity.setJson(null);
                } else {
                    final ObjectMapper mapper = createMapper(true);
                    final String json = mapper.writeValueAsString(activity.getDetails());
                    activity.setJson(json);
                }
            }
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    static void deserialise(final Activity activity) {
        try {
            if (activity != null) {
                if (activity.getJson() == null) {
                    activity.setDetails(null);
                } else {
                    final ObjectMapper mapper = createMapper(true);
                    final ActivityDetails data = mapper.readValue(activity.getJson(), ActivityDetails.class);
                    activity.setDetails(data);
                }
            }
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private static ObjectMapper createMapper(final boolean indent) {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, indent);
        mapper.setSerializationInclusion(Include.NON_NULL);

        return mapper;
    }
}
