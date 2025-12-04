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

package stroom.activity.api;

import stroom.activity.shared.Activity;
import stroom.activity.shared.ActivityValidationResult;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;
import stroom.util.shared.filter.FilterFieldDefinition;

import java.util.List;

public interface ActivityService {

    Activity create();

    Activity fetch(int id);

    Activity update(Activity activity);

    boolean deleteAllByOwner(int id);

    int deleteAllByOwner(UserRef ownerRef);

    ResultPage<Activity> find(final String filter);

    List<FilterFieldDefinition> listFieldDefinitions();

    ActivityValidationResult validate(Activity activity);
}
