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

package stroom.planb.impl.db;

import java.util.Objects;

public class SchemaInfo {
    private final int schemaVersion;
    private final String keySchema;
    private final String valueSchema;

    public SchemaInfo(final int schemaVersion,
                      final String keySchema,
                      final String valueSchema) {
        this.schemaVersion = schemaVersion;
        this.keySchema = keySchema;
        this.valueSchema = valueSchema;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public String getKeySchema() {
        return keySchema;
    }

    public String getValueSchema() {
        return valueSchema;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SchemaInfo that = (SchemaInfo) o;
        return schemaVersion == that.schemaVersion && Objects.equals(keySchema,
                that.keySchema) && Objects.equals(valueSchema, that.valueSchema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schemaVersion, keySchema, valueSchema);
    }

    @Override
    public String toString() {
        return "SchemaInfo{" +
               "schemaVersion=" + schemaVersion +
               ", keySchema='" + keySchema + '\'' +
               ", valueSchema='" + valueSchema + '\'' +
               '}';
    }
}
