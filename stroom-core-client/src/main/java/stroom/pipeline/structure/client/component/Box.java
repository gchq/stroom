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

package stroom.pipeline.structure.client.component;

import com.google.gwt.user.client.ui.Composite;

public abstract class Box<E> extends Composite {

    public abstract void setSelected(boolean selected);

    public abstract void showHotspot(boolean show);

    /// Shows (or stops showing) that the box refuses what is being dragged over it, e.g. as an
    /// element can't have a child of its type. By default nothing is shown.
    ///
    /// @param refused Whether it refuses it.
    public void showRefused(final boolean refused) {
    }

    /// Shows (or stops showing) that the box's item is detached, or after the item detached, to be
    /// attached to another parent with the keyboard (see [TreeKeyboard]). By default nothing is
    /// shown.
    ///
    /// @param detached Whether it is.
    public void setDetached(final boolean detached) {
    }

    /// Says (or stops saying) for assistive technology whether the box's item can take an item
    /// detached to be attached to another parent (see [TreeKeyboard]). By default nothing is said.
    ///
    /// @param detachedName The detached item's name, or null when none is detached (or the box's
    ///                     item is it, or after it).
    /// @param canTake      Whether the box's item can take it.
    public void setDetachedName(final String detachedName, final boolean canTake) {
    }

    public abstract E getItem();
}
