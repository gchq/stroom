package stroom.cache.shared;

import stroom.util.shared.PropertyPath;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class CacheIdentity implements Comparable<CacheIdentity> {

    // Note: it is possible to have two caches with the same config, e.g. StatisticsDataSourceCacheImpl
    @JsonProperty
    private final String cacheName;
    @JsonProperty
    private final PropertyPath basePropertyPath;

    @JsonCreator
    public CacheIdentity(@JsonProperty("cacheName") final String cacheName,
                         @JsonProperty("basePropertyPath") final PropertyPath basePropertyPath) {
        this.cacheName = cacheName;
        this.basePropertyPath = basePropertyPath;
    }

    public String getCacheName() {
        return cacheName;
    }

    public PropertyPath getBasePropertyPath() {
        return basePropertyPath;
    }

    @Override
    public String toString() {
        return "CacheIdentity{" +
                "cacheName='" + cacheName + '\'' +
                ", basePropertyPath=" + basePropertyPath +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CacheIdentity that = (CacheIdentity) o;
        return Objects.equals(cacheName, that.cacheName) && Objects.equals(basePropertyPath,
                that.basePropertyPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cacheName, basePropertyPath);
    }

    @Override
    public int compareTo(final CacheIdentity other) {
        final int compareResult = this.cacheName.compareTo(other.cacheName);
        if (compareResult != 0) {
            return compareResult;
        } else {
            return this.basePropertyPath.compareTo(other.basePropertyPath);
        }
    }
}
