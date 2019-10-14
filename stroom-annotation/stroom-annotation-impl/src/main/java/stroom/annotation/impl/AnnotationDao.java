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

package stroom.annotation.impl;

import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.AnnotationDetail;
import stroom.annotation.shared.AnnotationEntry;
import stroom.annotation.shared.CreateEntryRequest;
import stroom.dashboard.expression.v1.Val;
import stroom.datasource.api.v2.DataSourceField;
import stroom.entity.shared.ExpressionCriteria;

import java.util.function.Consumer;

public interface AnnotationDao {
    Annotation get(long annotationId);

    AnnotationDetail getDetail(long annotationId);

    Annotation get(long metaId, long eventId);

    AnnotationDetail getDetail(long metaId, long eventId);

    AnnotationDetail createEntry(CreateEntryRequest request, String user);

    void search(ExpressionCriteria criteria, DataSourceField[] fields, Consumer<Val[]> consumer);
}
