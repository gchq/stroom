/*
 * Copyright 2017 Crown Copyright
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

package stroom.config.global.shared;

import stroom.task.shared.Action;

public class FetchGlobalConfigAction extends Action<ConfigProperty> {
    private static final long serialVersionUID = 6083235358421128201L;

    private int configId;

    public FetchGlobalConfigAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public FetchGlobalConfigAction(final int configId) {
        this.configId = configId;
    }

    public int getConfigId() {
        return configId;
    }

    @Override
    public String getTaskName() {
        return "Load Global Property";
    }
}
