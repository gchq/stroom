package stroom.db.util;

import stroom.config.common.DbConfig;

import java.util.Objects;

public class DataSourceKey {

    private final DbConfig config;
    private final String name;
    private final boolean unique;

    public DataSourceKey(final DbConfig config,
                         final String name,
                         final boolean unique) {
        this.config = config;
        this.name = name;
        this.unique = unique;
    }

    public DbConfig getConfig() {
        return config;
    }

    public String getPoolName() {
        return "hikari-" + name;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DataSourceKey key = (DataSourceKey) o;
        if (unique) {
            return config.equals(key.config) && name.equals(key.name);
        }
        return config.equals(key.config);
    }

    @Override
    public int hashCode() {
        if (unique) {
            return Objects.hash(config, name);
        }
        return Objects.hash(config);
    }

    @Override
    public String toString() {
        return "Key{" +
                "config=" + config +
                ", name='" + name + '\'' +
                ", unique=" + unique +
                '}';
    }
}
