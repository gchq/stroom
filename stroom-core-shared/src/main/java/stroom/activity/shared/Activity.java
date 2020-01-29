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

import com.fasterxml.jackson.annotation.JsonIgnore;
import stroom.docref.SharedObject;
import stroom.util.shared.HasAuditInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Activity implements HasAuditInfo {
    public static final String ENTITY_TYPE = "Activity";

    private Integer id;
    private Integer version;
    private Long createTimeMs;
    private String createUser;
    private Long updateTimeMs;
    private String updateUser;
    private String userId;
    private String json;
    private ActivityDetails details = new ActivityDetails();

    public Activity() {
    }

    public Activity(final String userId, final ActivityDetails details) {
        this.userId = userId;
        this.details = details;
    }

    public Integer getId() {
        return id;
    }

    public void setId(final Integer id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(final Integer version) {
        this.version = version;
    }

    @Override
    public Long getCreateTimeMs() {
        return createTimeMs;
    }

    public void setCreateTimeMs(final Long createTimeMs) {
        this.createTimeMs = createTimeMs;
    }

    @Override
    public String getCreateUser() {
        return createUser;
    }

    public void setCreateUser(final String createUser) {
        this.createUser = createUser;
    }

    @Override
    public Long getUpdateTimeMs() {
        return updateTimeMs;
    }

    public void setUpdateTimeMs(final Long updateTimeMs) {
        this.updateTimeMs = updateTimeMs;
    }

    @Override
    public String getUpdateUser() {
        return updateUser;
    }

    public void setUpdateUser(final String updateUser) {
        this.updateUser = updateUser;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(final String userId) {
        this.userId = userId;
    }

    public String getJson() {
        return json;
    }

    public void setJson(final String json) {
        this.json = json;
    }

    public ActivityDetails getDetails() {
        return details;
    }

    public void setDetails(final ActivityDetails details) {
        this.details = details;
    }

    @Override
    public String toString() {
        return details.toString();
    }

    public static class ActivityDetails {
        private List<Prop> properties = new ArrayList<>();

        public ActivityDetails() {
        }

        public List<Prop> getProperties() {
            return properties;
        }

        public void setProperties(final List<Prop> properties) {
            this.properties = properties;
        }

        @JsonIgnore
        public void add(final Prop prop, final String value) {
            prop.setValue(value);
            properties.add(prop);
        }

        @JsonIgnore
        public String value(final String propertyId) {
            if (properties != null) {
                for (final Prop prop : properties) {
                    if (prop.getId() != null && prop.getId().equals(propertyId)) {
                        return prop.getValue();
                    }
                }
            }
            return null;
        }

        @Override
        public String toString() {
            return properties.stream().map(prop -> prop.value).collect(Collectors.joining(" - "));
        }
    }

    public static class Prop {
        private String id;
        private String name;
        private String validation;
        private String validationMessage;
        private String value;
        private boolean showInSelection = true;
        private boolean showInList = true;

        public Prop() {
        }

        public String getId() {
            return id;
        }

        public void setId(final String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }

        public String getValidation() {
            return validation;
        }

        public void setValidation(final String validation) {
            this.validation = validation;
        }

        public String getValidationMessage() {
            return validationMessage;
        }

        public void setValidationMessage(String validationMessage) {
            this.validationMessage = validationMessage;
        }

        public String getValue() {
            return value;
        }

        public void setValue(final String value) {
            this.value = value;
        }

        public boolean isShowInSelection() {
            return showInSelection;
        }

        public void setShowInSelection(final boolean showInSelection) {
            this.showInSelection = showInSelection;
        }

        public boolean isShowInList() {
            return showInList;
        }

        public void setShowInList(final boolean showInList) {
            this.showInList = showInList;
        }
    }
}

