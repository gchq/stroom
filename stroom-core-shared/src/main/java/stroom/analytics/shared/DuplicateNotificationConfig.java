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
import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class DuplicateNotificationConfig {

    private static final boolean DEFAULT_REMEMBER_NOTIFICATIONS = false;
    private static final boolean DEFAULT_SUPPRESS_DUPLICATE_NOTIFICATIONS = false;
    private static final boolean DEFAULT_CHOOSE_COLUMNS = false;

    @JsonProperty
    private final boolean rememberNotifications;
    @JsonProperty
    private final boolean suppressDuplicateNotifications;
    @JsonProperty
    private final boolean chooseColumns;
    @JsonProperty
    private final List<String> columnNames;

    public DuplicateNotificationConfig() {
        rememberNotifications = DEFAULT_REMEMBER_NOTIFICATIONS;
        suppressDuplicateNotifications = DEFAULT_SUPPRESS_DUPLICATE_NOTIFICATIONS;
        chooseColumns = DEFAULT_CHOOSE_COLUMNS;
        columnNames = new ArrayList<>();
    }

    @JsonCreator
    public DuplicateNotificationConfig(
            @JsonProperty("rememberNotifications") final Boolean rememberNotifications,
            @JsonProperty("suppressDuplicateNotifications") final Boolean suppressDuplicateNotifications,
            @JsonProperty("chooseColumns") final Boolean chooseColumns,
            @JsonProperty("columnNames") final List<String> columnNames) {

        this.rememberNotifications = Objects.requireNonNullElse(rememberNotifications,
                DEFAULT_REMEMBER_NOTIFICATIONS);
        this.suppressDuplicateNotifications = Objects.requireNonNullElse(suppressDuplicateNotifications,
                DEFAULT_SUPPRESS_DUPLICATE_NOTIFICATIONS);
        this.chooseColumns = Objects.requireNonNullElse(chooseColumns,
                DEFAULT_CHOOSE_COLUMNS);
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

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DuplicateNotificationConfig that = (DuplicateNotificationConfig) o;
        return rememberNotifications == that.rememberNotifications
               && suppressDuplicateNotifications == that.suppressDuplicateNotifications
               && chooseColumns == that.chooseColumns
               && Objects.equals(columnNames, that.columnNames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rememberNotifications, suppressDuplicateNotifications, chooseColumns, columnNames);
    }

    @Override
    public String toString() {
        return "DuplicateNotificationConfig{" +
               "rememberNotifications=" + rememberNotifications +
               ", suppressDuplicateNotifications=" + suppressDuplicateNotifications +
               ", chooseColumns=" + chooseColumns +
               ", columnNames=" + columnNames +
               '}';
    }
}
