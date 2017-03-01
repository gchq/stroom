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

package stroom.entity.client;

import stroom.query.api.DocRef;
import stroom.item.client.ItemListBox;

import java.util.Set;

public class EntityItemListBox extends ItemListBox<DocRef> {
    public EntityItemListBox() {
    }

    public EntityItemListBox(final String nonSelectString) {
        super(nonSelectString);
    }

    public EntityItemListBox(final String nonSelectString, final boolean multiSelect) {
        super(nonSelectString, multiSelect);
    }

    public void readEntityIdSet(final Set<DocRef> criteria) {
        if (criteria != null) {
            for (final DocRef item : getItems()) {
                setItemSelected(item, criteria.contains(item));
            }
        } else {
            setSelectedItem(null);
        }
    }

    public void writeEntityIdSet(final Set<DocRef> criteria) {
        criteria.clear();
        if (getSelectedItems() != null) {
            criteria.addAll(getSelectedItems());
        }
    }
}
