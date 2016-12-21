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

package stroom.explorer.client.presenter;

import com.google.gwt.user.cellview.client.HasSelection;
import com.google.gwt.view.client.SelectionModel.AbstractSelectionModel;
import stroom.explorer.shared.ExplorerData;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SingleSelectionModel<T> extends MultiSelectionModel<T> {
    @Override
    public void setSelected(final ExplorerData item, final boolean selected) {
        final boolean currentState = isSelected(item);
        if (currentState != selected) {
            changes.add(item);
            selectedItem = item;
            fireSelectionChangeEvent();
        }
    }
}
