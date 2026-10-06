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


package stroom.gwt.workbench.client.widgets.popuppositioner;

import stroom.gwt.workbench.client.widgets.StoryPopups;
import stroom.widget.popup.client.presenter.PopupPosition.PopupLocation;

import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/// One popup of the React `CASES` registry: how to render it and drive it, and what GWT does for
/// each clause of the contract (which is where these differ from the React registry's flags).
final class PopupCase {

    private final String name;
    private final Function<StoryPopups, Widget> render;
    private String opener;
    private final List<String> partners = new ArrayList<>();
    private boolean partnersExempt = true;
    private String panel;
    private String insideTarget;
    private boolean triggerToggles = true;
    private boolean escapeCloses = true;
    private boolean reopenAfterAutoHide;
    private boolean reactTriggerToggles = true;
    private boolean reactEscapeClosesViaHook = true;
    private Placement placement;

    private PopupCase(final String name, final Function<StoryPopups, Widget> render) {
        this.name = name;
        this.render = render;
    }

    /// @param name   The name used in assertion messages and as the container's `data-case`.
    /// @param render Renders the popup's widget, closed.
    /// @return The case, to configure.
    static PopupCase of(final String name, final Function<StoryPopups, Widget> render) {
        return new PopupCase(name, render);
    }

    /// @param opener The selector of the element that opens the popup, within the case's container.
    /// @return This.
    PopupCase opener(final String opener) {
        this.opener = opener;
        return this;
    }

    /// @param partners The selectors (within the case's container) of the elements React's
    ///                 registry lists as auto-hide partners, including inner children.
    /// @return This.
    PopupCase partners(final String... partners) {
        Collections.addAll(this.partners, partners);
        return this;
    }

    /// @param exempt Whether a mousedown on the partners leaves the popup open in GWT, i.e. GWT
    ///               registers them with `addAutoHidePartner`.
    /// @return This.
    PopupCase partnersExempt(final boolean exempt) {
        this.partnersExempt = exempt;
        return this;
    }

    /// @param panel The selector of the open popup, resolved document-wide.
    /// @return This.
    PopupCase panel(final String panel) {
        this.panel = panel;
        return this;
    }

    /// @param insideTarget The selector (document-wide) of inert chrome inside the panel.
    /// @return This.
    PopupCase insideTarget(final String insideTarget) {
        this.insideTarget = insideTarget;
        return this;
    }

    /// @param triggerToggles Whether, in GWT, a second click on the trigger closes the popup and
    ///                       leaves it closed.
    /// @return This.
    PopupCase triggerToggles(final boolean triggerToggles) {
        this.triggerToggles = triggerToggles;
        return this;
    }

    /// @param escapeCloses Whether, in GWT, Escape closes the popup.
    /// @return This.
    PopupCase escapeCloses(final boolean escapeCloses) {
        this.escapeCloses = escapeCloses;
        return this;
    }

    /// The React registry's flags, which say which cases a story runs over.
    ///
    /// @param triggerToggles      React's `triggerToggles`.
    /// @param escapeClosesViaHook React's `escapeClosesViaHook`.
    /// @return This.
    PopupCase react(final boolean triggerToggles, final boolean escapeClosesViaHook) {
        this.reactTriggerToggles = triggerToggles;
        this.reactEscapeClosesViaHook = escapeClosesViaHook;
        return this;
    }

    /// @param reopenAfterAutoHide Whether the first click on the opener after the popup has been
    ///                            auto-hidden only resets the widget, so a second is needed.
    /// @return This.
    PopupCase reopenAfterAutoHide(final boolean reopenAfterAutoHide) {
        this.reopenAfterAutoHide = reopenAfterAutoHide;
        return this;
    }

    /// @param anchor   The selector (within the container) of the element GWT positions against.
    /// @param location The `PopupLocation` the GWT call site asks for.
    /// @param shadow   GWT's `relativeRect.grow(shadow)` at the call site, or 0.
    /// @param gwt      The GWT call site, for the failure message.
    /// @return This.
    PopupCase placement(final String anchor, final PopupLocation location, final int shadow, final String gwt) {
        this.placement = new Placement(anchor, location, shadow, gwt);
        return this;
    }

    /// @return The case's name.
    String getName() {
        return name;
    }

    /// Renders the case's widget, closed.
    ///
    /// @param popups The story's popups.
    /// @return The widget.
    Widget render(final StoryPopups popups) {
        return render.apply(popups);
    }

    /// @param selector A selector within the case.
    /// @return The selector, scoped to the case's container.
    String within(final String selector) {
        return "[data-case='" + name + "'] " + selector;
    }

    /// @return The opener's selector, scoped to the case's container.
    String getOpener() {
        return within(opener);
    }

    /// @return React's partners' selectors, scoped to the case's container.
    List<String> getPartners() {
        final List<String> list = new ArrayList<>();
        for (final String partner : partners) {
            list.add(within(partner));
        }
        return list;
    }

    /// @return Whether GWT leaves the popup open on a mousedown on the partners.
    boolean isPartnersExempt() {
        return partnersExempt;
    }

    /// @return The open popup's selector.
    String getPanel() {
        return panel;
    }

    /// @return The selector of inert chrome inside the popup.
    String getInsideTarget() {
        return insideTarget;
    }

    /// @return Whether a second click on the trigger closes the popup in GWT.
    boolean isTriggerToggles() {
        return triggerToggles;
    }

    /// @return Whether Escape closes the popup in GWT.
    boolean isEscapeCloses() {
        return escapeCloses;
    }

    /// @return Whether the first click after an auto-hide only resets the widget.
    boolean isReopenAfterAutoHide() {
        return reopenAfterAutoHide;
    }

    /// @return React's `triggerToggles`, which says whether the trigger story runs over the case.
    boolean isReactTriggerToggles() {
        return reactTriggerToggles;
    }

    /// @return React's `escapeClosesViaHook`, which says whether the Escape story runs over the case.
    boolean isReactEscapeClosesViaHook() {
        return reactEscapeClosesViaHook;
    }

    /// @return Where GWT places the popup, or null if it isn't placed with a `PopupLocation`.
    Placement getPlacement() {
        return placement;
    }


    // --------------------------------------------------------------------------------


    /// React's `ExpectedPlacement`.
    static final class Placement {

        private final String anchor;
        private final PopupLocation location;
        private final int shadow;
        private final String gwt;

        private Placement(final String anchor, final PopupLocation location, final int shadow, final String gwt) {
            this.anchor = anchor;
            this.location = location;
            this.shadow = shadow;
            this.gwt = gwt;
        }

        /// @return The anchor's selector, within the case's container.
        String getAnchor() {
            return anchor;
        }

        /// @return The location the call site asks for.
        PopupLocation getLocation() {
            return location;
        }

        /// @return How much the call site grows the anchor by.
        int getShadow() {
            return shadow;
        }

        /// @return The GWT call site, for failure messages.
        String getGwt() {
            return gwt;
        }
    }
}
