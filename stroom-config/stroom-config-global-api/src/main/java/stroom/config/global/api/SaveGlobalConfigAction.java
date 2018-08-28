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

package stroom.config.global.api;

import stroom.task.shared.Action;

public class SaveGlobalConfigAction extends Action<ConfigProperty> {
    private static final long serialVersionUID = 6083235358421128201L;

    private ConfigProperty configProperty;

    public SaveGlobalConfigAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public SaveGlobalConfigAction(final ConfigProperty configProperty) {
        this.configProperty = configProperty;
    }

    public ConfigProperty getConfigProperty() {
        return configProperty;
    }

    @Override
    public String getTaskName() {
        return "Save Global Property";
    }
}
