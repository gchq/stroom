package stroom.docstore.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({
        "type",
        "uuid",
        "name",
        "version",
        "createTimeMs",
        "updateTimeMs",
        "createUser",
        "updateUser"})
@JsonInclude(Include.NON_NULL)
public abstract class AbstractSingletonDoc extends Doc {

    public AbstractSingletonDoc() {
        super();
        setUuid(getSingletonUuid());
    }

    public AbstractSingletonDoc(final String type, final String uuid, final String name) {
        super(type, uuid, name);
        validateUUID(uuid);
    }

    @JsonCreator
    public AbstractSingletonDoc(@JsonProperty("type") final String type,
                                @JsonProperty("uuid") final String uuid,
                                @JsonProperty("name") final String name,
                                @JsonProperty("version") final String version,
                                @JsonProperty("createTimeMs") final Long createTimeMs,
                                @JsonProperty("updateTimeMs") final Long updateTimeMs,
                                @JsonProperty("createUser") final String createUser,
                                @JsonProperty("updateUser") final String updateUser) {
        super(type,
                uuid,
                name,
                version,
                createTimeMs,
                updateTimeMs,
                createUser,
                updateUser);
        validateUUID(uuid);
    }

    /**
     * @return The fixed UUID for the single instance of this document type
     */
    @JsonIgnore
    protected abstract String getSingletonUuid();

    @Override
    public void setUuid(final String uuid) {
        super.setUuid(validateUUID(uuid));
    }

    /**
     * @return True if the type of this document is a singleton,
     * i.e. there is only ever zero or one document of this type, never more than one.
     * <p>
     * Examples of a singleton docs would are
     * {@link stroom.data.retention.shared.DataRetentionRules}
     * or {@link stroom.receive.rules.shared.ReceiveDataRules}.
     * </p>
     */
    @JsonIgnore
    public boolean isSingleton() {
        return true;
    }

    protected String validateUUID(final String uuid) {
        return validateUUID(getSingletonUuid(), uuid);
    }

    protected static String validateUUID(final String singletonUuid, final String uuid) {
        if (!Objects.equals(singletonUuid, uuid)) {
            throw new IllegalArgumentException(
                    "This document is a singleton. " +
                    "Supplied uuid '" + uuid + "' must match the singleton UUID '" +
                    singletonUuid + "'");
        } else {
            return uuid;
        }
    }

    @Override
    public boolean equals(final Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public String toString() {
        return super.toString();
    }


    // --------------------------------------------------------------------------------


    public abstract static class AbstractBuilder<
            T extends AbstractSingletonDoc,
            B extends AbstractSingletonDoc.AbstractBuilder<T, ?>> {

        protected final String type = getType();
        protected String uuid = getUuid();
        protected String name;
        protected String version;
        protected Long createTimeMs;
        protected Long updateTimeMs;
        protected String createUser;
        protected String updateUser;

        public AbstractBuilder() {
        }

        public AbstractBuilder(final AbstractSingletonDoc doc) {
            this.uuid = validateUUID(getUuid(), doc.getUuid());
            this.name = doc.getName();
            this.version = doc.getVersion();
            this.createTimeMs = doc.getCreateTimeMs();
            this.updateTimeMs = doc.getUpdateTimeMs();
            this.createUser = doc.getCreateUser();
            this.updateUser = doc.getUpdateUser();
        }

        public B withName(final String name) {
            this.name = name;
            return self();
        }

        public B withVersion(final String version) {
            this.version = version;
            return self();
        }

        public B withCreateTimeMs(final Long createTimeMs) {
            this.createTimeMs = createTimeMs;
            return self();
        }

        public B withUpdateTimeMs(final Long updateTimeMs) {
            this.updateTimeMs = updateTimeMs;
            return self();
        }

        public B withCreateUser(final String createUser) {
            this.createUser = createUser;
            return self();
        }

        public B withUpdateUser(final String updateUser) {
            this.updateUser = updateUser;
            return self();
        }

        protected abstract B self();

        /**
         * @return The fixed UUID for the single instance of this document type
         */
        protected abstract String getUuid();

        /**
         * @return The document's type
         */
        protected abstract String getType();

        public abstract T build();
    }
}
