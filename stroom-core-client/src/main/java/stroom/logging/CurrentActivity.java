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

import org.springframework.stereotype.Component;
import stroom.activity.shared.Activity;

import javax.inject.Inject;
import javax.inject.Provider;

@Component
public class CurrentActivity {
    private final Provider<CurrentActivitySession> currentActivitySessionProvider;

    @Inject
    public CurrentActivity(final Provider<CurrentActivitySession> currentActivitySessionProvider) {
        this.currentActivitySessionProvider = currentActivitySessionProvider;
    }

    public Activity getActivity() {
        return currentActivitySessionProvider.get().getActivity();
    }

    public void setActivity(final Activity activity) {
        currentActivitySessionProvider.get().setActivity(activity);
    }
}

