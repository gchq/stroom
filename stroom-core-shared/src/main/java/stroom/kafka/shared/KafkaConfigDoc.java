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

package stroom.kafka.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.docstore.shared.Doc;
import stroom.util.shared.HasData;

import java.util.Objects;


@JsonPropertyOrder({"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser", "description", "data"})
@JsonInclude(Include.NON_EMPTY)
public class KafkaConfigDoc extends Doc implements HasData {

    private static final long serialVersionUID = -334504830584034766L;

    public static final String DOCUMENT_TYPE = "KafkaConfig";

    @JsonProperty
    private String description = "";

    @JsonProperty
    private String data = "";

    public KafkaConfigDoc() {
    }

    public KafkaConfigDoc(final String type, final String uuid, final String name) {
        super(type, uuid, name);
    }

    @JsonCreator
    public KafkaConfigDoc(@JsonProperty("type") final String type,
                         @JsonProperty("uuid") final String uuid,
                         @JsonProperty("name") final String name,
                         @JsonProperty("version") final String version,
                         @JsonProperty("createTime") final Long createTime,
                         @JsonProperty("updateTime") final Long updateTime,
                         @JsonProperty("createUser") final String createUser,
                         @JsonProperty("updateUser") final String updateUser,
                         @JsonProperty("description") final String description,
                         @JsonProperty("data") final String data) {
        super(type, uuid, name, version, createTime, updateTime, createUser, updateUser);
        this.description = description;
        this.data = data;
    }

    public String getDescription() {
        return description;
    }

    @SuppressWarnings("unused")
    public void setDescription(final String description) {
        this.description = description;
    }

    @Override
    public String getData() {
        return data;
    }

    @Override
    public void setData(final String data) {
        this.data = data;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        final KafkaConfigDoc other = (KafkaConfigDoc) o;
        return Objects.equals(description, other.description) &&
                Objects.equals(data, other.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), description, data);
    }

}
