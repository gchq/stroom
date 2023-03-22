/*
 * Copyright 2020 Crown Copyright
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

package stroom.alert.api;


import stroom.query.api.v2.TableSettings;

import java.util.Map;

public class AlertDefinition {

    private final Map<String, String> attributes;
    private final TableSettings tableSettings;
    private boolean disabled = false;

    public AlertDefinition(final TableSettings tableSettings,
                           final Map<String, String> attributes) {
        this.tableSettings = tableSettings;
        if (attributes != null) {
            this.attributes = attributes;
        } else {
            this.attributes = Map.of();
        }
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public final TableSettings getTableSettings() {
        return tableSettings;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(final boolean disabled) {
        this.disabled = disabled;
    }
}
