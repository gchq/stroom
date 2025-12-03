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

package stroom.data.client.event;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;

public class DataSelectionEvent<I> extends GwtEvent<DataSelectionEvent.DataSelectionHandler<I>> {

    /**
     * Handler type.
     */
    private static Type<DataSelectionHandler<?>> TYPE;
    private final I selectedItem;
    private final boolean doubleSelect;

    /**
     * Creates a new selection event.
     *
     * @param selectedItem selected item
     */
    private DataSelectionEvent(final I selectedItem, final boolean doubleSelect) {
        this.selectedItem = selectedItem;
        this.doubleSelect = doubleSelect;
    }

    /**
     * Fires a selection event on all registered handlers in the handler
     * manager.If no such handlers exist, this method will do nothing.
     *
     * @param <I>          the selected item type
     * @param source       the source of the handlers
     * @param selectedItem the selected item
     */
    public static <I> void fire(final HasDataSelectionHandlers<I> source,
                                final I selectedItem,
                                final boolean doubleSelect) {
        if (TYPE != null) {
            final DataSelectionEvent<I> event = new DataSelectionEvent<>(selectedItem, doubleSelect);
            source.fireEvent(event);
        }
    }

    /**
     * Gets the type associated with this event.
     *
     * @return returns the handler type
     */
    public static Type<DataSelectionHandler<?>> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    // The instance knows its BeforeSelectionHandler is of type I, but the TYPE
    // field itself does not, so we have to do an unsafe cast here.
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public final Type<DataSelectionHandler<I>> getAssociatedType() {
        return (Type) TYPE;
    }

    /**
     * Gets the selected item.
     *
     * @return the selected item
     */
    public I getSelectedItem() {
        return selectedItem;
    }

    public boolean isDoubleSelect() {
        return doubleSelect;
    }

    public boolean isSingleSelect() {
        return !doubleSelect;
    }

    @Override
    protected void dispatch(final DataSelectionHandler<I> handler) {
        handler.onSelection(this);
    }


    // --------------------------------------------------------------------------------


    public interface DataSelectionHandler<I> extends EventHandler {

        void onSelection(DataSelectionEvent<I> event);
    }
}
