package stroom.config.global.api;

import stroom.docref.DocRef;
import stroom.util.shared.AbstractConfig;

public interface GlobalConfig {

    /**
     * Set a doc ref property value.
     *
     * @param config       The config object to update.
     * @param propertyName The specific property name to update.
     * @param docRef       The new value.
     */
    void setDocRef(final AbstractConfig config,
                   final String propertyName,
                   final DocRef docRef);

    /**
     * Set an integer property value.
     *
     * @param config       The config object to update.
     * @param propertyName The specific property name to update.
     * @param i            The new value.
     */
    void setInt(final AbstractConfig config,
                final String propertyName,
                final int i);

    /**
     * Set a string property value.
     *
     * @param config       The config object to update.
     * @param propertyName The specific property name to update.
     * @param string       The new value.
     */
    void setString(final AbstractConfig config,
                   final String propertyName,
                   final String string);

    /**
     * Store a whole config.
     *
     * @param config The config object to update.
     */
    void update(AbstractConfig config);
}
