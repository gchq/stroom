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

package stroom.analytics.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class DuplicateNotificationConfig {

    @JsonProperty
    private final boolean rememberNotifications;
    @JsonProperty
    private final boolean suppressDuplicateNotifications;
    @JsonProperty
    private final boolean chooseColumns;
    @JsonProperty
    private final List<String> columnNames;

    public DuplicateNotificationConfig() {
        rememberNotifications = false;
        suppressDuplicateNotifications = false;
        chooseColumns = false;
        columnNames = new ArrayList<>();
    }

    @JsonCreator
    public DuplicateNotificationConfig(
            @JsonProperty("rememberNotifications") final boolean rememberNotifications,
            @JsonProperty("suppressDuplicateNotifications") final boolean suppressDuplicateNotifications,
            @JsonProperty("chooseColumns") final boolean chooseColumns,
            @JsonProperty("columnNames") final List<String> columnNames) {

        this.rememberNotifications = rememberNotifications;
        this.suppressDuplicateNotifications = suppressDuplicateNotifications;
        this.chooseColumns = chooseColumns;
        this.columnNames = columnNames;
    }

    public boolean isRememberNotifications() {
        return rememberNotifications;
    }

    public boolean isSuppressDuplicateNotifications() {
        return suppressDuplicateNotifications;
    }

    public boolean isChooseColumns() {
        return chooseColumns;
    }

    public List<String> getColumnNames() {
        return columnNames;
    }
}
