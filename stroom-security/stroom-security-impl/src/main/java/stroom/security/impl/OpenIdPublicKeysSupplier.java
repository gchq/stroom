package stroom.security.impl;

import org.jose4j.jwk.JsonWebKeySet;

import java.util.function.Supplier;

public class PublicKeysSupplier implements Supplier<JsonWebKeySet> {
    @Override
    public JsonWebKeySet get() {
        return null;
    }
}
