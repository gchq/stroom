package stroom.docstore;

public interface Serialiser2Factory {

    <D> Serialiser2<D> createSerialiser(final Class<D> clazz);
}
