package stroom.refdata.onheapstore;

import stroom.refdata.offheapstore.MapDefinition;

import java.util.Comparator;

class KeyValueStoreKey implements Comparable<KeyValueStoreKey> {

   private static final Comparator<KeyValueStoreKey> COMPARATOR = Comparator
           .comparing(KeyValueStoreKey::getMapDefinition)
           .thenComparing(KeyValueStoreKey::getKey);

   private final MapDefinition mapDefinition;
   private final String key;

   KeyValueStoreKey(final MapDefinition mapDefinition, final String key) {
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
   public int compareTo(final KeyValueStoreKey that) {
       return COMPARATOR.compare(this, that);
   }
}
