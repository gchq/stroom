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

package stroom.legacy.model_6_1;

import jakarta.xml.bind.annotation.XmlTransient;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;

/**
 * <p>
 * Used for tables with millions of rows.
 * </p>
 */
@MappedSuperclass
@Deprecated
public abstract class BaseEntityBig extends BaseEntity {
    private static final long serialVersionUID = -2776331251851326084L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = ID, columnDefinition = BIG_KEY_DEF)
    @XmlTransient
    public long getId() {
        return id;
    }

    public void setId(final long id) {
        this.id = id;
    }
}
