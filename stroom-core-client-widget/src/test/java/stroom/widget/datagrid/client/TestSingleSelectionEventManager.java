/*
 * Copyright 2026 Crown Copyright
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

package stroom.widget.datagrid.client;

import com.google.gwt.view.client.SingleSelectionModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TestSingleSelectionEventManager {

    // The keys don't use the grid (only a press does, which needs a browser), so it is null here

    @Mock
    private SingleSelectionModel<String> selectionModel;

    @Test
    void theSelectionFollowsTheFocus() {
        new SingleSelectionEventManager<>(null, selectionModel).onFocusRow("b", false, false);

        verify(selectionModel).setSelected("b", true);
    }

    @Test
    void ctrlMovesTheFocusWithoutSelecting() {
        new SingleSelectionEventManager<>(null, selectionModel).onFocusRow("b", false, true);

        verify(selectionModel, never()).setSelected(any(), anyBoolean());
    }

    @Test
    void spaceAndEnterSelectTheRow() {
        final SingleSelectionEventManager<String> manager = new SingleSelectionEventManager<>(null, selectionModel);

        manager.onSpace("a", false, false);
        manager.onEnter("c");

        verify(selectionModel).setSelected("a", true);
        verify(selectionModel).setSelected("c", true);
    }

    @Test
    void noRowSelectsNothing() {
        final SingleSelectionEventManager<String> manager = new SingleSelectionEventManager<>(null, selectionModel);

        manager.onFocusRow(null, false, false);
        manager.onSpace(null, false, false);
        manager.onEnter(null);
        manager.onSelectAll();

        verify(selectionModel, never()).setSelected(any(), anyBoolean());
    }
}
