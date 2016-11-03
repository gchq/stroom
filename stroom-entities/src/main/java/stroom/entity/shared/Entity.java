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

@MappedSuperclass
public abstract class Entity implements HasType, SharedObject {
    private static final long serialVersionUID = 2405151110726276049L;

    // Standard data types. Unfortunately HSQLDB doesn't have unsigned data
    // types so we do not set these to unsigned here.
    public static final String TINYINT_UNSIGNED = "TINYINT";
    public static final String SMALLINT_UNSIGNED = "SMALLINT";
    public static final String INT_UNSIGNED = "INT";
    public static final String BIGINT_UNSIGNED = "BIGINT";

    // Shame HSQLDB does not like keys smaller than int.
    public static final String NORMAL_KEY_DEF = INT_UNSIGNED;
    public static final String BIG_KEY_DEF = BIGINT_UNSIGNED;

    public static final String VERSION = "VER";
    public static final String ID = "ID";

    protected static final String FK_PREFIX = "FK_";
    protected static final String ID_SUFFIX = "_ID";
    protected static final String SEP = "_";

    @Transient
    public abstract boolean isPersistent();

    @Transient
    public abstract Object getPrimaryKey();
}
