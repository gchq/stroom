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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.docstore.shared.Doc;

import java.util.Objects;
import java.util.Properties;

@JsonPropertyOrder({"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser", "description", "properties"})
@JsonInclude(Include.NON_EMPTY)
public class KafkaConfigDoc extends Doc {
    private static final long serialVersionUID = 4519634323788508083L;

    public static final String DOCUMENT_TYPE = "KAFKA_CONFIG";

    private static final String BOOTSTRAP_SERVERS_CONFIG = "bootstrap.servers";
    private static final String BATCH_SIZE_CONFIG = "batch.size";
    private static final String ACKS_CONFIG = "acks";
    private static final String LINGER_MS_CONFIG = "linger.ms";
    private static final String BUFFER_MEMORY_CONFIG = "buffer.memory";
    private static final String RETRIES_CONFIG = "retries";
    private static final String KEY_SERIALIZER_CLASS_CONFIG = "key.serializer";
    private static final String VALUE_SERIALIZER_CLASS_CONFIG = "value.serializer";

    private String description;
    private String kafkaVersion = "0.10.0.1";
    private Properties properties;

    public KafkaConfigDoc() {
        properties = new Properties();

        // Set some useful defaults.
        properties.put(BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        properties.put(ACKS_CONFIG, "all");
        properties.put(RETRIES_CONFIG, 0);
        properties.put(BATCH_SIZE_CONFIG, 16384);
        properties.put(LINGER_MS_CONFIG, 1);
        properties.put(BUFFER_MEMORY_CONFIG, 33554432);

        // Serializers are hard coded as we have to specify the types when creating the
        properties.put(KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        properties.put(VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArraySerializer");
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

    public void setKafkaVersion(final String kafkaVersion) {
        this.kafkaVersion = kafkaVersion;
    }

    public Properties getProperties() {
        return properties;
    }

    public void setProperties(final Properties properties) {
        this.properties = properties;
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
