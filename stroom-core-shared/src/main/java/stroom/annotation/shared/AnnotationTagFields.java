/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.annotation.shared;

import stroom.query.api.datasource.QueryField;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface AnnotationTagFields {

    String ID = "Id";
    String UUID = "UUID";
    String NAME = "Name";
    String TYPE_ID = "TypeId";

    QueryField ID_FIELD = QueryField.createId(ID);
    QueryField UUID_FIELD = QueryField.createId(UUID);
    QueryField NAME_FIELD = QueryField.createDate(NAME);
    QueryField TYPE_ID_FIELD = QueryField.createText(TYPE_ID);

    List<QueryField> FIELDS = Arrays.asList(
            ID_FIELD,
            UUID_FIELD,
            NAME_FIELD,
            TYPE_ID_FIELD);
    Map<String, QueryField> FIELD_MAP = FIELDS.stream()
            .collect(Collectors.toMap(QueryField::getFldName, Function.identity()));
}
