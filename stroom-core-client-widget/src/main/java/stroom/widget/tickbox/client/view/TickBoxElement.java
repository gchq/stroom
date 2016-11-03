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

package stroom.widget.tickbox.client.view;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.ImageElement;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.user.client.ui.AbstractImagePrototype;

import stroom.cell.tickbox.shared.TickBoxState;

public final class TickBoxElement extends ImageElement {
    private static final Resources RESOURCES = GWT.create(Resources.class);

    interface Resources extends ClientBundle {
        ImageResource tick();

        ImageResource halfTick();

        ImageResource untick();
    }

    private static final String IMAGE_TICK = AbstractImagePrototype.create(RESOURCES.tick()).getHTML();
    private static final String IMAGE_HALF_TICK = AbstractImagePrototype.create(RESOURCES.halfTick()).getHTML();
    private static final String IMAGE_UNTICK = AbstractImagePrototype.create(RESOURCES.untick()).getHTML();

    protected TickBoxElement() {
    }

    public final void setState(final TickBoxState state) {
        switch (state) {
        case TICK:
            getParentElement().setInnerHTML(IMAGE_TICK);
            break;
        case HALF_TICK:
            getParentElement().setInnerHTML(IMAGE_HALF_TICK);
            break;
        case UNTICK:
            getParentElement().setInnerHTML(IMAGE_UNTICK);
            break;
        }
    }
}
