package stroom.meta.api;

@FunctionalInterface
public interface AttributeMapper {

    AttributeMapper IDENTITY = attributeMap -> attributeMap;

    /**
     * Return an {@link AttributeMap} instance containing all entries from attributeMap.
     * Those values that need to be hashed will have been hashed.
     * If any values are hashed, a new {@link AttributeMap} will be returned.
     *
     * @param attributeMap Will not be modified but may be returned unchanged.
     */
    AttributeMap mapAttributes(final AttributeMap attributeMap);

    /**
     * @return An {@link AttributeMapper} that does not modify the {@link AttributeMap} in
     * any way.
     */
    static AttributeMapper identity() {
        return IDENTITY;
    }
}
