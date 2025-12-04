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

package stroom.util.entityevent;

import stroom.docref.HasDisplayValue;

public enum EntityAction implements HasDisplayValue {
    /**
     * Fired after the node is created but before it is added to the explorer.
     */
    CREATE("Create"),
    /**
     * Fired after the explorer is updated on CREATE.
     */
    POST_CREATE("Post Create"),
    /**
     * Fired on rename (once), updateTags and move.
     */
    UPDATE("Update"),
    /**
     * Fired before the node is removed from the explorer.
     * Useful for when we need to know where the node is placed
     * in the explorer tree.
     */
    PRE_DELETE("Pre Delete"),
    /**
     * Fired after the node is removed from the explorer.
     * May be fired more than once.
     */
    DELETE("Delete"),
    CLEAR_CACHE("Clear Cache"),
    // Separate events for an explorer tree node update as the tree doesn't care about an update to the document
    // itself but does care about updates to the properties of the node, e.g. tags
    CREATE_EXPLORER_NODE("Create Explorer Node"),
    /**
     * Fired on:
     * <UL>
     *     <LI>Bulk move, after the move</LI>
     *     <LI>Rename of a node - twice</LI>
     *     <LI>Update of tags</LI>
     * </UL>
     */
    UPDATE_EXPLORER_NODE("Update Explorer Node"),
    DELETE_EXPLORER_NODE("Delete Explorer Node");

    private final String displayValue;

    EntityAction(final String displayValue) {
        this.displayValue = displayValue;
    }

    /**
     * @return string used in drop downs.
     */
    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
