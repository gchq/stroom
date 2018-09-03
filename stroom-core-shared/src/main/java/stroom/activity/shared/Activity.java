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

import stroom.entity.shared.AuditedEntity;
import stroom.util.shared.SharedObject;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.Transient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Entity
@Table(name = "ACTIVITY")
public class Activity extends AuditedEntity {
    public static final String TABLE_NAME = "ACTIVITY";
    public static final String USER_ID = "USER_ID";
    public static final String JSON = "JSON";
    public static final String ENTITY_TYPE = "Activity";

    private String userId;
    private String json;
    private ActivityDetails details = new ActivityDetails();

    public Activity() {
    }

    public Activity(final String userId, final ActivityDetails details) {
        this.userId = userId;
        this.details = details;
    }

    @Column(name = USER_ID, nullable = false)
    public String getUserId() {
        return userId;
    }

    public void setUserId(final String userId) {
        this.userId = userId;
    }

    @Column(name = JSON, nullable = false)
    @Lob
    public String getJson() {
        return json;
    }

    public void setJson(final String json) {
        this.json = json;
    }

    @Transient
    public ActivityDetails getDetails() {
        return details;
    }

    @Transient
    public void setDetails(final ActivityDetails details) {
        this.details = details;
    }

    @Transient
    @Override
    public String getType() {
        return ENTITY_TYPE;
    }

    @Override
    public String toString() {
        return details.toString();
    }

    public static class ActivityDetails implements SharedObject {
        private List<String> names = new ArrayList<>();
        private Map<String, String> properties = new HashMap<>();

        public ActivityDetails() {
        }

        public List<String> getNames() {
            return names;
        }

        public void setNames(final List<String> names) {
            this.names = names;
        }

        public Map<String, String> getProperties() {
            return properties;
        }

        public void setProperties(final Map<String, String> properties) {
            this.properties = properties;
        }

        public void addProperty(final String name, final String value) {
            names.add(name);
            properties.put(name, value);
        }

        @Override
        public String toString() {
            return names.stream().map(name -> properties.get(name)).collect(Collectors.joining(" - "));
        }
    }
}

