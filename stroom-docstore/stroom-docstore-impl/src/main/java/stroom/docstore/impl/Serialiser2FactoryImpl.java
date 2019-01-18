package stroom.docstore.impl;

import stroom.docstore.Serialiser2;
import stroom.docstore.Serialiser2Factory;

public class Serialiser2FactoryImpl implements Serialiser2Factory {

    @Override
    public <D> Serialiser2<D> createSerialiser(final Class<D> clazz) {
        return new JsonSerialiser2<>(clazz);
    }
}
