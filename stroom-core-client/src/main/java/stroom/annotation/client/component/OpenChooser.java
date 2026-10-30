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

package stroom.annotation.client.component;

import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;

import com.google.gwt.dom.client.Element;

/// The chooser a screen has open ([ChooserController] or [MultiChooserController]) and the element
/// that opened it, so that a second click on that element closes the chooser rather than opening
/// another over it, as a chooser shown again did.
///
/// ```java
/// if (openChooser.closeIfOpenedFrom(element)) {
///     return;
/// }
/// ...
/// chooser.show(this, element, "Choose Status", ...);
/// openChooser.opened(chooser, element);
/// ```
public final class OpenChooser {

    private Controller chooser;
    private Element opener;

    /// Closes the chooser if it is open and was opened from an element.
    ///
    /// @param element The element clicked, e.g. the setting the chooser is for.
    /// @return Whether it closed it, in which case the click needs nothing more.
    public boolean closeIfOpenedFrom(final Element element) {
        final boolean open = chooser != null && !chooser.isDisposed() && opener == element;
        if (open) {
            // As the popup manager hid a popup asked to show again
            ControllerDialog.hide(chooser, true, false);
        }
        chooser = null;
        opener = null;
        return open;
    }

    /// Records the chooser just shown.
    ///
    /// @param chooser The chooser.
    /// @param element The element that opened it.
    public void opened(final Controller chooser, final Element element) {
        this.chooser = chooser;
        this.opener = element;
    }
}
