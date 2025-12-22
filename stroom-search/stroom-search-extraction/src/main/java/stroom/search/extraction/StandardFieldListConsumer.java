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

package stroom.search.extraction;

import stroom.query.api.QueryKey;
import stroom.query.common.v2.StringFieldValue;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValuesConsumer;

import java.util.List;


public class StandardFieldListConsumer implements FieldListConsumer {

    private final FieldValueExtractor fieldValueExtractor;
    private QueryKey queryKey;
    private ValuesConsumer receiver;
    private FieldIndex fieldIndex;

    public StandardFieldListConsumer(final FieldValueExtractor fieldValueExtractor) {
        this.fieldValueExtractor = fieldValueExtractor;
    }

    @Override
    public void acceptFieldValues(final List<FieldValue> fieldValues) {
        final Val[] values = new Val[fieldIndex.size()];
        for (final FieldValue fieldValue : fieldValues) {
            final Integer pos = fieldIndex.getPos(fieldValue.field().getFldName());
            if (pos != null) {
                values[pos] = fieldValue.value();
            }
        }
        receiver.accept(Val.of(values));
    }

    @Override
    public void acceptStringValues(final List<StringFieldValue> stringFieldValues) {
        final Val[] values = new Val[fieldIndex.size()];
        for (final StringFieldValue stringFieldValue : stringFieldValues) {
            final Integer pos = fieldIndex.getPos(stringFieldValue.fieldName());
            if (pos != null) {
                final FieldValue fieldValue = fieldValueExtractor.convert(pos, stringFieldValue.fieldValue());
                values[pos] = fieldValue.value();
            }
        }
        receiver.accept(Val.of(values));
    }

    public QueryKey getQueryKey() {
        return queryKey;
    }

    public void setQueryKey(final QueryKey queryKey) {
        this.queryKey = queryKey;
    }

    public void setReceiver(final ValuesConsumer receiver) {
        this.receiver = receiver;
    }

    public void setFieldIndex(final FieldIndex fieldIndex) {
        this.fieldIndex = fieldIndex;
    }
}
