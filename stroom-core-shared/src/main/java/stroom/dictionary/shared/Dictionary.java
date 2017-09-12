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

package stroom.dictionary.shared;

import stroom.entity.shared.DocumentEntity;
import stroom.entity.shared.ExternalFile;
import stroom.entity.shared.SQLNameConstants;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;

/**
 * <p>
 * Used to hold system wide groups in the application.
 * </p>
 */
@Entity
@Table(name = "DICT")
public class Dictionary extends DocumentEntity {
    public static final String TABLE_NAME = SQLNameConstants.DICTIONARY;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final String DATA = SQLNameConstants.DATA;
    public static final String ENTITY_TYPE = "Dictionary";

    private static final long serialVersionUID = -4208920620555926044L;

    private String data;

    @Column(name = DATA, length = Integer.MAX_VALUE)
    @Lob
    @ExternalFile("txt")
    public String getData() {
        return data;
    }

    public void setData(final String data) {
        this.data = data;
    }

    @Transient
    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }
}
