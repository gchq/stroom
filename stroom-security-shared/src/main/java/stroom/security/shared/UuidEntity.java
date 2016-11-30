/*
 * Copyright 2016 Crown Copyright
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

package stroom.security.shared;

import org.hibernate.annotations.GenericGenerator;
import stroom.entity.shared.Entity;
import stroom.entity.shared.HasUuid;
import stroom.entity.shared.SQLNameConstants;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import java.beans.Transient;

@MappedSuperclass
public abstract class UuidEntity extends Entity implements HasUuid {
    public static final String UUID = SQLNameConstants.UUID;
    private static final long serialVersionUID = -2776331251851326084L;
    private String uuid;

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = UUID, unique = true, nullable = false)
    public String getUuid() {
        return uuid;
    }

    public void setUuid(final String uuid) {
        this.uuid = uuid;
    }

    @Transient
    @Override
    public final boolean isPersistent() {
        return uuid != null;
    }

    @Transient
    @Override
    public final Object getPrimaryKey() {
        return uuid;
    }

    protected void toString(final StringBuilder sb) {
        sb.append(getType());
        sb.append(" (uuid=");
        sb.append(uuid);
//        sb.append(", version=");
//        sb.append(version);
    }

    /**
     * @return Nice looking string.
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        toString(sb);
        sb.append(")");
        return sb.toString();
    }
}
