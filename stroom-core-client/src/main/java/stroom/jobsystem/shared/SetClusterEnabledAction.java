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

package stroom.jobsystem.shared;

import stroom.dispatch.shared.Action;
import stroom.util.shared.SharedBoolean;

public class SetClusterEnabledAction extends Action<SharedBoolean> {
    private static final long serialVersionUID = -6228856208209223590L;

    private boolean enabled;

    public SetClusterEnabledAction(boolean enabled) {
        this.enabled = enabled;
    }

    public SetClusterEnabledAction() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String getTaskName() {
        return "Cluster Enabled";
    }
}
