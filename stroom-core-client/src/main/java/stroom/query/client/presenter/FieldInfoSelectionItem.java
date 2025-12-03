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

package stroom.query.client.presenter;

import stroom.item.client.SelectionItem;
import stroom.query.api.datasource.QueryField;
import stroom.svg.shared.SvgImage;

import java.util.Objects;

public class FieldInfoSelectionItem implements SelectionItem {

    private final QueryField field;

    public FieldInfoSelectionItem(final QueryField field) {
        this.field = field;
    }

    @Override
    public String getLabel() {
        if (field == null) {
            return "[ none ]";
        }
        return field.getFldName();
    }

    @Override
    public SvgImage getIcon() {
        return null;
    }

    @Override
    public boolean isHasChildren() {
        return false;
    }

    public QueryField getField() {
        return field;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FieldInfoSelectionItem)) {
            return false;
        }
        final FieldInfoSelectionItem that = (FieldInfoSelectionItem) o;
        return Objects.equals(field, that.field);
    }

    @Override
    public int hashCode() {
        return Objects.hash(field);
    }

    @Override
    public String toString() {
        return "FieldInfoSelectionItem{" +
                "field=" + field +
                '}';
    }
}
