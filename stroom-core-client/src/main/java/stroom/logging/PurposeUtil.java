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

package stroom.logging;

import event.logging.Data;
import event.logging.Purpose;
import stroom.activity.shared.Activity;
import stroom.activity.shared.Activity.ActivityDetails;

public class PurposeUtil {
    public static Purpose create(final Activity activity) {
        if (activity != null && activity.getDetails() != null) {
            final Purpose purpose = new Purpose();
            final ActivityDetails activityDetails = activity.getDetails();
            activityDetails.getNames().forEach(name -> {
                final String value = activityDetails.getProperties().get(name);
                final Data data = new Data();
                data.setName(name);
                data.setValue(value);
                purpose.getData().add(data);
            });
            return purpose;
        }
        return null;
    }
}
