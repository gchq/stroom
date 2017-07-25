/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.entity.shared;

import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HasId;
import stroom.util.shared.HasType;
import stroom.util.shared.SharedObject;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Transient;
import javax.persistence.Version;
import javax.xml.bind.annotation.XmlTransient;

/**
 * <p>
 * Technical base class of all our entity beans to ensure we handle things like
 * id's audit fields (createDate etc) optimistic locking in a standard way.
 * </p>
 */
@MappedSuperclass
public abstract class BaseEntity extends Entity implements HasType, HasId, SharedObject, Comparable<BaseEntity> {
    private static final long serialVersionUID = 2405151110726276049L;

    // Value of a long to represent an undefined id.
    private static final long UNDEFINED_ID = -1;

    /**
     * This is a technical key (the primary key) You should consider your own
     * business key (e.g. string name) if required. You should not reference
     * this key in the application code or in any screen.
     */
    long id = UNDEFINED_ID;
    /**
     * Standard EJB3 optimistic locking
     */
    private byte version = -1;
    /**
     * Special non persistent marker to state that entity is a Stub. I.e. it was
     * lazy and the BaseEntityDeProxyProcessor replaced the lazy object with a
     * stub
     */
    private boolean stub;

    @Override
    @Transient
    @XmlTransient
    public abstract long getId();

    public abstract void setId(long id);

    /**
     * JPA hook.
     */
    @PrePersist
    public void prePersist() {
    }

    /**
     * JPA hook.
     */
    @PreUpdate
    public void preUpdate() {
    }

    /**
     * Have we saved yet?
     *
     * @return yes if we have
     */
    @Transient
    @Override
    public final boolean isPersistent() {
        return getId() != UNDEFINED_ID;
    }

    @Transient
    @Override
    public final Object getPrimaryKey() {
        if (!isPersistent()) {
            return null;
        }
        return Long.valueOf(getId());
    }

    //    @Transient
//    @Override
//    public final Object getValues() {
//        if (!isPersistent()) {
//            return null;
//        }
//        return Long.valueOf(getId());
//    }


    /**
     * Reset the entity id and version to its undefined state so that it is non
     * persistent. This is often useful when copying an entity using copy.
     */
    @Transient
    public void clearPersistence() {
        setId(UNDEFINED_ID);
        version = -1;
    }

    @Version
    @Column(name = VERSION, columnDefinition = TINYINT_UNSIGNED, nullable = false)
    @XmlTransient
    public byte getVersion() {
        return version;
    }

    public void setVersion(final byte version) {
        this.version = version;
    }

    @Transient
    @XmlTransient
    public boolean isStub() {
        return stub;
    }

    public void setStub(final boolean stub) {
        this.stub = stub;
    }

    public void setStub(final long stubId) {
        setId(stubId);
        setStub(true);
    }

    /**
     * Are 2 entities equal ?
     */
    public final boolean equalsEntity(final BaseEntity other) {
        // Other null?
        if (other == null) {
            return false;
        }

        // The same instance?
        if (other == this) {
            return true;
        }

        // If either object is not persistent then we can't check for equality.
        if (!this.isPersistent() || !other.isPersistent()) {
            return false;
        }

        // Both persistent so compare keys.
        if (this.getId() != other.getId()) {
            return false;
        }

        final String thisName = this.getType();
        final String otherName = other.getType();
        return thisName.equals(otherName);
    }

    /**
     * Are 2 entities the same and at the same version?
     */
    public final boolean equalsEntityVersion(final BaseEntity other) {
        if (equalsEntity(other)) {
            final EqualsBuilder equalsBuilder = new EqualsBuilder();
            equalsBuilder.append(getVersion(), other.getVersion());
            return equalsBuilder.isEquals();
        }

        return false;
    }

    /**
     * Standard equals just compares the same entity id
     */
    @Override
    public final boolean equals(final Object other) {
        if (other instanceof BaseEntity) {
            return equalsEntity((BaseEntity) other);
        }
        return false;
    }

    /**
     * Returns a hash code for this entity. The result is the exclusive OR of
     * the two halves of the primitive <code>long</code> id.
     */
    @Override
    public final int hashCode() {
        return (int) (getId() ^ (getId() >>> 32));
    }

    /**
     * Compares one base entity with another using the id of each entity. When
     * used with sorted collections entities will be ordered by ascending id.
     *
     * @param o The base entity to compare this one with.
     * @return An integer that is a result of this id - the comparing object id.
     */
    @Override
    public int compareTo(final BaseEntity o) {
        return Long.compare(id, o.id);
    }

    protected void toString(final StringBuilder sb) {
        sb.append(getType());
        sb.append(" (id=");
        sb.append(id);
        sb.append(", version=");
        sb.append(version);
    }

    /**
     * @return Nice looking string for entity beans.
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        toString(sb);
        sb.append(")");
        return sb.toString();
    }
}
