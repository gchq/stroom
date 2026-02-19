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

package stroom.activity.shared;

import stroom.util.shared.AbstractBuilder;
import stroom.util.shared.HasAuditInfoBuilder;
import stroom.util.shared.HasAuditInfoGetters;
import stroom.util.shared.HasAuditableUserIdentity;
import stroom.util.shared.HasIntegerId;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@JsonInclude(Include.NON_NULL)
public class Activity implements HasAuditInfoGetters, HasIntegerId {

    public static final String ENTITY_TYPE = "Activity";

    @JsonProperty
    private final Integer id;
    @JsonProperty
    private final Integer version;
    @JsonProperty
    private final Long createTimeMs;
    @JsonProperty
    private final String createUser;
    @JsonProperty
    private final Long updateTimeMs;
    @JsonProperty
    private final String updateUser;
    @JsonProperty
    private final UserRef userRef;
    @JsonProperty
    private final ActivityDetails details;

    public Activity(final Integer id,
                    final Integer version,
                    final Long createTimeMs,
                    final String createUser,
                    final Long updateTimeMs,
                    final String updateUser,
                    final UserRef userRef) {
        this.id = id;
        this.version = version;
        this.createTimeMs = createTimeMs;
        this.createUser = createUser;
        this.updateTimeMs = updateTimeMs;
        this.updateUser = updateUser;
        this.userRef = userRef;
        this.details = new ActivityDetails(new ArrayList<>());
    }

    @JsonCreator
    public Activity(@JsonProperty("id") final Integer id,
                    @JsonProperty("version") final Integer version,
                    @JsonProperty("createTimeMs") final Long createTimeMs,
                    @JsonProperty("createUser") final String createUser,
                    @JsonProperty("updateTimeMs") final Long updateTimeMs,
                    @JsonProperty("updateUser") final String updateUser,
                    @JsonProperty("userRef") final UserRef userRef,
                    @JsonProperty("details") final ActivityDetails details) {
        this.id = id;
        this.version = version;
        this.createTimeMs = createTimeMs;
        this.createUser = createUser;
        this.updateTimeMs = updateTimeMs;
        this.updateUser = updateUser;
        this.userRef = userRef;
        this.details = details;
    }

    @Override
    public Integer getId() {
        return id;
    }

    public Integer getVersion() {
        return version;
    }

    @Override
    public Long getCreateTimeMs() {
        return createTimeMs;
    }

    @Override
    public String getCreateUser() {
        return createUser;
    }

    @Override
    public Long getUpdateTimeMs() {
        return updateTimeMs;
    }

    @Override
    public String getUpdateUser() {
        return updateUser;
    }

    public UserRef getUserRef() {
        return userRef;
    }

    public ActivityDetails getDetails() {
        return details;
    }

    @Override
    public String toString() {
        if (details != null) {
            return details.toString();
        } else {
            return "Undefined Activity Details";
        }
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }


    // --------------------------------------------------------------------------------


    public static class Builder
            extends AbstractBuilder<Activity, Activity.Builder>
            implements HasAuditInfoBuilder<Activity, Activity.Builder> {

        private Integer id;
        private Integer version;
        private Long createTimeMs;
        private String createUser;
        private Long updateTimeMs;
        private String updateUser;
        private UserRef userRef;
        private ActivityDetails details;

        private Builder() {
            details = new ActivityDetails(new ArrayList<>());
        }

        private Builder(final Activity activity) {
            this.id = activity.id;
            this.version = activity.version;
            this.createTimeMs = activity.createTimeMs;
            this.createUser = activity.createUser;
            this.updateTimeMs = activity.updateTimeMs;
            this.updateUser = activity.updateUser;
            this.userRef = activity.userRef;
            this.details = activity.details;
        }

        public Builder id(final Integer id) {
            this.id = id;
            return self();
        }

        public Builder version(final Integer version) {
            this.version = version;
            return self();
        }

        @Override
        public Builder createTimeMs(final Long createTimeMs) {
            this.createTimeMs = createTimeMs;
            return self();
        }

        @Override
        public Builder createUser(final String createUser) {
            this.createUser = createUser;
            return self();
        }

        @Override
        public Builder updateTimeMs(final Long updateTimeMs) {
            this.updateTimeMs = updateTimeMs;
            return self();
        }

        @Override
        public Builder updateUser(final String updateUser) {
            this.updateUser = updateUser;
            return self();
        }

        public Builder userRef(final UserRef userRef) {
            this.userRef = userRef;
            return self();
        }

        public Builder details(final ActivityDetails details) {
            this.details = details;
            return self();
        }

        public final Builder stampAudit(final HasAuditableUserIdentity hasAuditableUserIdentity) {
            return stampAudit(hasAuditableUserIdentity.getUserIdentityForAudit());
        }

        public final Builder stampAudit(final String user) {
            final long now = System.currentTimeMillis();
            if (createTimeMs == null) {
                this.createTimeMs = now;
            }
            if (createUser == null) {
                this.createUser = user;
            }
            updateTimeMs = now;
            updateUser = user;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public Activity build() {
            return new Activity(
                    id,
                    version,
                    createTimeMs,
                    createUser,
                    updateTimeMs,
                    updateUser,
                    userRef,
                    details);
        }
    }


    // --------------------------------------------------------------------------------


    @JsonInclude(Include.NON_NULL)
    public static class ActivityDetails {

        @JsonProperty
        private final List<Prop> properties;

        @JsonCreator
        public ActivityDetails(@JsonProperty("properties") final List<Prop> properties) {
            this.properties = properties;
        }

        public List<Prop> getProperties() {
            return properties;
        }

        public void add(final Prop prop) {
            properties.add(prop);
        }

        public String value(final String propertyId) {
            for (final Prop prop : properties) {
                if (prop.getId() != null && prop.getId().equals(propertyId)) {
                    return prop.getValue();
                }
            }
            return null;
        }

        public String valueByName(final String propertyName) {
            for (final Prop prop : properties) {
                if (prop.getName() != null && prop.getName().equals(propertyName)) {
                    return prop.getValue();
                }
            }
            return null;
        }

        @Override
        public String toString() {
            return properties.stream().map(prop -> prop.value).collect(Collectors.joining(" - "));
        }
    }


    // --------------------------------------------------------------------------------


    @JsonInclude(Include.NON_NULL)
    public static class Prop {

        @JsonProperty
        private final String id;
        @JsonProperty
        private final String name;
        @JsonProperty
        private final String validation;
        @JsonProperty
        private final String validationMessage;
        @JsonProperty
        private final String value;
        @JsonProperty
        private final Boolean showInSelection;
        @JsonProperty
        private final Boolean showInList;

//        public Prop() {
//            setDefaultValues();
//        }

        @JsonCreator
        public Prop(@JsonProperty("id") final String id,
                    @JsonProperty("name") final String name,
                    @JsonProperty("validation") final String validation,
                    @JsonProperty("validationMessage") final String validationMessage,
                    @JsonProperty("value") final String value,
                    @JsonProperty("showInSelection") final Boolean showInSelection,
                    @JsonProperty("showInList") final Boolean showInList) {
            this.id = id;
            this.name = name;
            this.validation = validation;
            this.validationMessage = validationMessage;
            this.value = value;
            this.showInSelection = NullSafe.requireNonNullElse(showInSelection, true);
            this.showInList = NullSafe.requireNonNullElse(showInList, true);
        }

//        private void setDefaultValues() {
//            if (showInSelection == null) {
//                showInSelection = true;
//            }
//            if (showInList == null) {
//                showInList = true;
//            }
//        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getValidation() {
            return validation;
        }

        public String getValidationMessage() {
            return validationMessage;
        }

        public String getValue() {
            return value;
        }

        public boolean isShowInSelection() {
            return showInSelection;
        }

        public boolean isShowInList() {
            return showInList;
        }

        public static Builder builder() {
            return new Builder();
        }

        public Builder copy() {
            return new Builder(this);
        }

        public static class Builder extends AbstractBuilder<Prop, Prop.Builder> {

            private String id;
            private String name;
            private String validation;
            private String validationMessage;
            private String value;
            private Boolean showInSelection;
            private Boolean showInList;

            private Builder() {
            }

            private Builder(final Prop prop) {
                this.id = prop.id;
                this.name = prop.name;
                this.validation = prop.validation;
                this.validationMessage = prop.validationMessage;
                this.value = prop.value;
                this.showInSelection = prop.showInSelection;
                this.showInList = prop.showInList;
            }

            public Builder id(final String id) {
                this.id = id;
                return self();
            }

            public Builder name(final String name) {
                this.name = name;
                return self();
            }

            public Builder validation(final String validation) {
                this.validation = validation;
                return self();
            }

            public Builder validationMessage(final String validationMessage) {
                this.validationMessage = validationMessage;
                return self();
            }

            public Builder value(final String value) {
                this.value = value;
                return self();
            }

            public Builder showInSelection(final Boolean showInSelection) {
                this.showInSelection = showInSelection;
                return self();
            }

            public Builder showInList(final Boolean showInList) {
                this.showInList = showInList;
                return self();
            }

            @Override
            protected Prop.Builder self() {
                return this;
            }

            public Prop build() {
                return new Prop(id, name, validation, validationMessage, value, showInSelection, showInList);
            }
        }
    }
}

