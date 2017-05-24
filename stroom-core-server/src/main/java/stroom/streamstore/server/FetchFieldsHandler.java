/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.streamstore.server;

import org.springframework.context.annotation.Scope;
import stroom.query.shared.IndexField;
import stroom.query.shared.IndexFields;
import stroom.streamstore.shared.FetchFieldsAction;
import stroom.streamstore.shared.StreamAttributeConstants;
import stroom.streamstore.shared.StreamAttributeFieldUse;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.spring.StroomScope;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

@TaskHandlerBean(task = FetchFieldsAction.class)
@Scope(StroomScope.TASK)
public class FetchFieldsHandler extends AbstractTaskHandler<FetchFieldsAction, IndexFields> {
    @Override
    public IndexFields exec(final FetchFieldsAction task) {
        final Set<IndexField> set = new HashSet<>();

        set.add(IndexField.createField("Feed"));
        set.add(IndexField.createField("StreamType"));
        set.add(IndexField.createField("Pipeline"));
        set.add(IndexField.createNumericField("StreamId"));
        set.add(IndexField.createNumericField("ParentStreamId"));
        set.add(IndexField.createDateField("Created"));
        set.add(IndexField.createDateField("Effective"));

        for (final Entry<String, StreamAttributeFieldUse> entry : StreamAttributeConstants.SYSTEM_ATTRIBUTE_FIELD_TYPE_MAP.entrySet()) {
            final IndexField indexField = create(entry.getKey(), entry.getValue());
            if (indexField != null) {
                set.add(indexField);
            }
        }

        final List<IndexField> list = set.stream().sorted(Comparator.comparing(IndexField::getFieldName)).collect(Collectors.toList());
        return new IndexFields(list);
    }

    private IndexField create(final String name, final StreamAttributeFieldUse streamAttributeFieldUse) {
        switch (streamAttributeFieldUse) {
            case FIELD:
                return IndexField.createField(name);
            case ID:
                return IndexField.createIdField(name);
            case DURATION_FIELD:
                return IndexField.createNumericField(name);
            case COUNT_IN_DURATION_FIELD:
                return IndexField.createNumericField(name);
            case NUMERIC_FIELD:
                return IndexField.createNumericField(name);
            case DATE_FIELD:
                return IndexField.createDateField(name);
            case SIZE_FIELD:
                return IndexField.createNumericField(name);
        }

        return null;
    }
}
