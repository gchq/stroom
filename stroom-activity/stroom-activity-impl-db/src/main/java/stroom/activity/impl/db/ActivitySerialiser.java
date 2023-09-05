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

package stroom.activity.impl.db;

import stroom.activity.shared.Activity;
import stroom.activity.shared.Activity.ActivityDetails;
import stroom.util.json.JsonUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ActivitySerialiser {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActivitySerialiser.class);

    static Activity serialise(final Activity activity) {
        try {
            if (activity != null) {
                if (activity.getDetails() == null) {
                    activity.setJson(null);
                } else {
                    final String json = JsonUtil.writeValueAsString(activity.getDetails());
                    activity.setJson(json);
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return activity;
    }

    static Activity deserialise(final Activity activity) {
        try {
            if (activity != null) {
                if (activity.getJson() == null) {
                    activity.setDetails(null);
                } else {
                    final ActivityDetails data = JsonUtil.readValue(activity.getJson(), ActivityDetails.class);
                    activity.setDetails(data);
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return activity;
    }
}
