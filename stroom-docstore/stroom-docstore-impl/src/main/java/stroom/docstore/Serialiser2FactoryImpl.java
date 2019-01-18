package stroom.docstore;

public class Serialiser2FactoryImpl implements Serialiser2Factory {

    @Override
    public <D> Serialiser2<D> createSerialiser(final Class<D> clazz) {
        return new JsonSerialiser2<>(clazz);
    }
}
