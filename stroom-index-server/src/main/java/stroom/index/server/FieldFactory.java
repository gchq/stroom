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

package stroom.index.server;

import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongField;
import stroom.index.shared.IndexField;

public class FieldFactory {
    public static LongField create(final IndexField indexField, final long initialValue) {
        return new LongField(indexField.getFieldName(), initialValue, FieldTypeFactory.create(indexField));
    }

    public static Field create(final IndexField indexField, final String initialValue) {
        return new Field(indexField.getFieldName(), initialValue, FieldTypeFactory.create(indexField));
    }
}
