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
