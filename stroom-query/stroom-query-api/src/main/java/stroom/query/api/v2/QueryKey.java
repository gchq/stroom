/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.api.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.Objects;

@JsonPropertyOrder({"uuid"})
@JsonInclude(Include.NON_NULL)
@XmlType(name = "QueryKey", propOrder = {"uuid"})
@XmlAccessorType(XmlAccessType.FIELD)
@ApiModel(description = "A unique key to identify the instance of the search by. This key is used to " +
        "identify multiple requests for the same search when running in incremental mode.")
public final class QueryKey implements Serializable {
    private static final long serialVersionUID = -3222989872764402068L;

    @XmlElement
    @ApiModelProperty(
            value = "The UUID that makes up the query key",
            example = "7740bcd0-a49e-4c22-8540-044f85770716",
            required = true)
    @JsonProperty
    private String uuid;

    public QueryKey() {
    }

    @JsonCreator
    public QueryKey(@JsonProperty("uuid") final String uuid) {
        this.uuid = uuid;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(final String uuid) {
        this.uuid = uuid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QueryKey queryKey = (QueryKey) o;
        return Objects.equals(uuid, queryKey.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }

    @Override
    public String toString() {
        return uuid;
    }
}