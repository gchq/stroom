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
import stroom.util.shared.HasDisplayValue;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;

@JsonPropertyOrder({"order", "direction"})
@XmlType(name = "Sort", propOrder = {"order", "direction"})
public class Sort implements Serializable {
    private static final long serialVersionUID = 4530846367973824427L;

    private int order;
    private SortDirection direction;

    public Sort() {
    }

    public Sort(final int order, final SortDirection direction) {
        this.order = order;
        this.direction = direction;
    }

    @XmlElement
    public int getOrder() {
        return order;
    }

    public void setOrder(final int order) {
        this.order = order;
    }

    @XmlElement
    public SortDirection getDirection() {
        return direction;
    }

    public void setDirection(final SortDirection direction) {
        this.direction = direction;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof Sort)) return false;

        final Sort sort = (Sort) o;

        if (order != sort.order) return false;
        return direction == sort.direction;
    }

    @Override
    public int hashCode() {
        int result = order;
        result = 31 * result + (direction != null ? direction.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Sort{" +
                "order=" + order +
                ", direction=" + direction +
                '}';
    }

    public enum SortDirection implements HasDisplayValue {
        ASCENDING("Ascending"), DESCENDING("Descending");

        private final String displayValue;

        SortDirection(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }
}
