package stroom.resources;

public interface NamedResource {

    default String getName() {
        return this.getClass().getSimpleName();
    }
}
