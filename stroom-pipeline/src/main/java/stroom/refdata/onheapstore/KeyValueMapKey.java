package stroom.refdata.onheapstore;

import stroom.refdata.offheapstore.MapDefinition;

import java.util.Objects;

class KeyValueMapKey {

   private final MapDefinition mapDefinition;
   private final String key;
   private final int hashCode;

   KeyValueMapKey(final MapDefinition mapDefinition, final String key) {
       this.mapDefinition = mapDefinition;
       this.key = key;
       // pre compute the hash
       this.hashCode = Objects.hash(mapDefinition, key);
   }

   MapDefinition getMapDefinition() {
       return mapDefinition;
   }

   String getKey() {
       return key;
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
        return hashCode;
    }

    @Override
    public String toString() {
        return "KeyValueMapKey{" +
                "mapDefinition=" + mapDefinition +
                ", key='" + key + '\'' +
                '}';
    }
}
