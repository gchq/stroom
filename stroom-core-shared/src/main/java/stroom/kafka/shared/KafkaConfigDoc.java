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
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import stroom.docstore.shared.Doc;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@JsonPropertyOrder({"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser", "description", "properties"})
@JsonInclude(Include.NON_NULL)
public class KafkaConfigDoc extends Doc {
    public static final String DOCUMENT_TYPE = "KafkaConfig";

    private static final String BOOTSTRAP_SERVERS_CONFIG = "bootstrap.servers";
    private static final String BATCH_SIZE_CONFIG = "batch.size";
    private static final String ACKS_CONFIG = "acks";
    private static final String LINGER_MS_CONFIG = "linger.ms";
    private static final String BUFFER_MEMORY_CONFIG = "buffer.memory";
    private static final String RETRIES_CONFIG = "retries";
    private static final String KEY_SERIALIZER_CLASS_CONFIG = "key.serializer";
    private static final String VALUE_SERIALIZER_CLASS_CONFIG = "value.serializer";

    @JsonProperty
    private String description;
    @JsonProperty
    private final String kafkaVersion;
    @JsonProperty
    private Map<String, Object> properties;

    public KafkaConfigDoc() {
        kafkaVersion = "2.2.1";
        properties = new HashMap<>();

        // Set some useful defaults.
        properties.put(BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        properties.put(ACKS_CONFIG, "all");
        properties.put(RETRIES_CONFIG, 0);
        properties.put(BATCH_SIZE_CONFIG, 16384);
        properties.put(LINGER_MS_CONFIG, 1);
        properties.put(BUFFER_MEMORY_CONFIG, 33554432);

        // TODO not sure if these should be strings or Class objects (the latter will be awkward)
        // Serializers are hard coded as we have to specify the types when creating the
        properties.put(KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        properties.put(VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArraySerializer");
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
                          @JsonProperty("kafkaVersion") final String kafkaVersion,
                          @JsonProperty("properties") final Map<String, Object> properties) {
        super(type, uuid, name, version, createTime, updateTime, createUser, updateUser);
        this.description = description;
        this.kafkaVersion = kafkaVersion;
        this.properties = properties;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public String getKafkaVersion() {
        return kafkaVersion;
    }

    // Kafka expects typed property values so jackson needs to know what
    // types to de-serialise as using a white list of aliased types
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_ARRAY)
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Boolean.class, name = "booleanType"),
            @JsonSubTypes.Type(value = Integer.class, name = "integerType"),
            @JsonSubTypes.Type(value = Short.class, name = "shortType"),
            @JsonSubTypes.Type(value = Long.class, name = "longType"),
            @JsonSubTypes.Type(value = String.class, name = "stringType"),
            @JsonSubTypes.Type(value = Class.class, name = "classType")
    })
    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(final Map<String, Object> properties) {
        this.properties = properties;
    }

    public void addProperty(final String key, final Object value) {
        properties.put(key, value);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        final KafkaConfigDoc that = (KafkaConfigDoc) o;
        return Objects.equals(description, that.description) &&
                Objects.equals(kafkaVersion, that.kafkaVersion) &&
                Objects.equals(properties, that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), description, kafkaVersion, properties);
    }
}
