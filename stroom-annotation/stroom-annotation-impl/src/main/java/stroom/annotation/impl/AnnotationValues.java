/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.annotation.impl;

import stroom.annotation.shared.AnnotationDecorationFields;
import stroom.annotation.shared.AnnotationIdentity;
import stroom.query.api.datasource.QueryField;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.util.shared.NullSafe;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class AnnotationValues {

    private final AnnotationIdentity annotationIdentity;
    private final Map<QueryField, Val> values = new ConcurrentHashMap<>();
    private volatile boolean deleted;

    public AnnotationValues(final AnnotationIdentity annotationIdentity) {
        this.annotationIdentity = annotationIdentity;
    }

    public AnnotationIdentity getAnnotationIdentity() {
        return annotationIdentity;
    }

    public void put(final QueryField queryField, final Val val) {
        values.put(queryField, val);
    }

    public Val get(final QueryField queryField) {
        if (queryField.equals(AnnotationDecorationFields.ANNOTATION_ID_FIELD)) {
            return ValLong.create(annotationIdentity.getId());
        }
        if (queryField.equals(AnnotationDecorationFields.ANNOTATION_UUID_FIELD)) {
            return NullSafe.getOrElse(annotationIdentity.getUuid(), ValString::create, ValNull.INSTANCE);
        }
        return Objects.requireNonNullElse(values.get(queryField), ValNull.INSTANCE);
    }

    public boolean containsKey(final QueryField queryField) {
        return values.containsKey(queryField);
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void markDeleted() {
        deleted = true;
        values.clear();
    }

    @Override
    public String toString() {
        return "AnnotationValues{" +
               "uuid='" + annotationIdentity + '\'' +
               ", values=" + values +
               ", deleted=" + deleted +
               '}';
    }
}
