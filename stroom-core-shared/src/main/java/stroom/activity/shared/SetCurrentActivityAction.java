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

package stroom.activity.shared;

import stroom.dispatch.shared.Action;

public class SetCurrentActivityAction extends Action<Activity> {
    private static final long serialVersionUID = 1451964889275627717L;

    private Activity activity;

    public SetCurrentActivityAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public Activity getActivity() {
        return activity;
    }

    public void setActivity(final Activity activity) {
        this.activity = activity;
    }

    @Override
    public String getTaskName() {
        return "Set activity";
    }
}
