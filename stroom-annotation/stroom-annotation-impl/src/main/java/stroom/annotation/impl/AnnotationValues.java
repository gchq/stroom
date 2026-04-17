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

import stroom.annotation.shared.AnnotationIdentity;
import stroom.query.api.datasource.QueryField;
import stroom.query.language.functions.Val;
import stroom.util.shared.NullSafe;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AnnotationValues {

    private volatile AnnotationIdentity annotationIdentity;
    private volatile boolean deleted;
    private final Map<QueryField, Val> values = new ConcurrentHashMap<>();

    public String getUuid() {
        return NullSafe.get(annotationIdentity, AnnotationIdentity::getUuid);
    }

    public long getAnnotationId() {
        return NullSafe.get(annotationIdentity, AnnotationIdentity::getId);
    }

    public void setAnnotationIdentity(final AnnotationIdentity annotationIdentity) {
        this.annotationIdentity = annotationIdentity;
    }

    public Map<QueryField, Val> getValues() {
        return values;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void markDeleted() {
        deleted = true;
        values.clear();
    }

//    public void setDeleted(final boolean deleted) {
//        this.deleted = deleted;
//        if (deleted) {
//            values.clear();
//        }
//    }

    @Override
    public String toString() {
        return "AnnotationValues{" +
               "uuid='" + annotationIdentity + '\'' +
               ", values=" + values +
               ", deleted=" + deleted +
               '}';
    }
}
