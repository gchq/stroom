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

package stroom.query.api;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;

@JsonPropertyOrder({"enabled", "priority", "direction"})
@XmlType(name = "visSort", propOrder = {"enabled", "priority", "direction"})
public class VisSort implements Serializable {
    private static final long serialVersionUID = 1272545271946712570L;

    private String enabled;
    private String priority;
    private String direction;

    @XmlElement
    public String getEnabled() {
        return enabled;
    }

    public void setEnabled(final String enabled) {
        this.enabled = enabled;
    }

    @XmlElement
    public String getPriority() {
        return priority;
    }

    public void setPriority(final String priority) {
        this.priority = priority;
    }

    @XmlElement
    public String getDirection() {
        return direction;
    }

    public void setDirection(final String direction) {
        this.direction = direction;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof VisSort)) return false;

        final VisSort visSort = (VisSort) o;

        if (enabled != null ? !enabled.equals(visSort.enabled) : visSort.enabled != null) return false;
        if (priority != null ? !priority.equals(visSort.priority) : visSort.priority != null) return false;
        return direction != null ? direction.equals(visSort.direction) : visSort.direction == null;
    }

    @Override
    public int hashCode() {
        int result = enabled != null ? enabled.hashCode() : 0;
        result = 31 * result + (priority != null ? priority.hashCode() : 0);
        result = 31 * result + (direction != null ? direction.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "VisSort{" +
                "enabled='" + enabled + '\'' +
                ", priority='" + priority + '\'' +
                ", direction='" + direction + '\'' +
                '}';
    }
}