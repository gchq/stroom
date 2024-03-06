package stroom.docref;

/**
 * Implementing classes can be represented using a {@link DocRef} however they may not
 * all feature in the explorer tree.
 */
public interface HasDocRef extends HasName, HasUuid, HasType {

    default DocRef asDocRef() {
        return DocRef.builder()
                .type(getType())
                .name(getName())
                .uuid(getUuid())
                .build();
    }
}
