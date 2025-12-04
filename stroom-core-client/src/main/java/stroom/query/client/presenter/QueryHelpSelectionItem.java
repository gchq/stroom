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

import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.item.client.SelectionItem;
import stroom.query.shared.QueryHelpRow;
import stroom.svg.shared.SvgImage;

import java.util.Objects;

public class QueryHelpSelectionItem implements SelectionItem {

    private final QueryHelpRow queryHelpRow;

    public QueryHelpSelectionItem(final QueryHelpRow queryHelpRow) {
        this.queryHelpRow = queryHelpRow;
    }

    @Override
    public String getLabel() {
        return queryHelpRow.getTitle();
    }

    @Override
    public SvgImage getIcon() {
        return DocumentTypeRegistry.getIcon(queryHelpRow.getDocumentType());
    }

    @Override
    public String getIconTooltip() {
        return queryHelpRow.getIconTooltip();
    }

    @Override
    public boolean isHasChildren() {
        return queryHelpRow.isHasChildren();
    }

    public QueryHelpRow getQueryHelpRow() {
        return queryHelpRow;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof QueryHelpSelectionItem)) {
            return false;
        }
        final QueryHelpSelectionItem that = (QueryHelpSelectionItem) o;
        return Objects.equals(queryHelpRow, that.queryHelpRow);
    }

    @Override
    public int hashCode() {
        return Objects.hash(queryHelpRow);
    }

    @Override
    public String toString() {
        return "QueryHelpSelectionItem{" +
               "fieldInfo=" + queryHelpRow +
               '}';
    }
}
