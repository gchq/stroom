package stroom.refdata.onheapstore;

import stroom.refdata.offheapstore.MapDefinition;

import java.util.Comparator;
import java.util.Objects;

class KeyValueMapKey implements Comparable<KeyValueMapKey> {

   private static final Comparator<KeyValueMapKey> COMPARATOR = Comparator
           .comparing(KeyValueMapKey::getMapDefinition)
           .thenComparing(KeyValueMapKey::getKey);

   private final MapDefinition mapDefinition;
   private final String key;

   KeyValueMapKey(final MapDefinition mapDefinition, final String key) {
       this.mapDefinition = mapDefinition;
       this.key = key;
   }

   MapDefinition getMapDefinition() {
       return mapDefinition;
   }

   String getKey() {
       return key;
   }

   @Override
   public int compareTo(final KeyValueMapKey that) {
       return COMPARATOR.compare(this, that);
   }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final KeyValueMapKey that = (KeyValueMapKey) o;
        return Objects.equals(mapDefinition, that.mapDefinition) &&
                Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {

        return Objects.hash(mapDefinition, key);
    }

    @Override
    public String toString() {
        return "KeyValueMapKey{" +
                "mapDefinition=" + mapDefinition +
                ", key='" + key + '\'' +
                '}';
    }
}
