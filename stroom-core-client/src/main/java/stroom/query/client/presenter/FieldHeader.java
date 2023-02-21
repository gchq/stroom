/*
 * Copyright 2017 Crown Copyright
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

package stroom.query.client.presenter;

import stroom.query.api.v2.Field;

import com.google.gwt.user.cellview.client.Header;

public class FieldHeader extends Header<Field> {

    private final Field field;

    public FieldHeader(final Field field) {
        super(FieldCell.create());
        this.field = field;
    }

    @Override
    public Field getValue() {
        return field;
    }
}