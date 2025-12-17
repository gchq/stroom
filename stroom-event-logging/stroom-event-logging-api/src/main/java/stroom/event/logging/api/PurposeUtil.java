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

package stroom.event.logging.api;

import stroom.activity.shared.Activity;
import stroom.activity.shared.Activity.ActivityDetails;

import event.logging.Data;
import event.logging.Purpose;

import java.util.List;
import java.util.stream.Collectors;

public class PurposeUtil {

    public static Purpose create(final Activity activity) {
        if (activity != null && activity.getDetails() != null) {
            return Purpose.builder()
                    .addData(activity.getDetails()
                            .getProperties()
                            .stream()
                            .map(prop -> Data.builder()
                                    .withName(prop.getId())
                                    .withValue(prop.getValue())
                                    .build())
                            .collect(Collectors.toList()))
                    .build();
        }
        return null;
    }

    public static void addData(final List<Data> list, final Activity activity) {

        if (activity != null && activity.getDetails() != null) {
            final ActivityDetails activityDetails = activity.getDetails();
            activityDetails.getProperties().forEach(p ->
                    list.add(Data.builder()
                            .withName(p.getId())
                            .withValue(p.getValue())
                            .build()));
        }
    }
}
