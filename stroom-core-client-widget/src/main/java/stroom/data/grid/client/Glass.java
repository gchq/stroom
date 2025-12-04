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

package stroom.data.grid.client;

import stroom.widget.util.client.Rect;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.Window;

public class Glass {

    private final String className;
    private final String visibleClassName;

    // The element that masks the screen so we can catch mouse events over
    // iframes.
    private Element glassElem;
    private boolean glassAttached;

    public Glass(final String className) {
        this(className, null);
    }

    public Glass(final String className, final String visibleClassName) {
        this.className = className;
        this.visibleClassName = visibleClassName;
    }

    public void show() {
        // Resize glassElem to take up the entire scrollable window area, which
        // is the greater of the scroll size and the client size.
        final double width = Math.max(Window.getClientWidth(), Document.get().getScrollWidth());
        final double height = Math.max(Window.getClientHeight(), Document.get().getScrollHeight());

        show(0, 0, width, height);
    }

    public void show(final Rect rect) {
        show(
                rect.getLeft(),
                rect.getTop(),
                rect.getWidth(),
                rect.getHeight());
    }

    public void show(final double left, final double top, final double width, final double height) {
        if (!glassAttached) {
            glassAttached = true;
            ensureGlass();
            glassElem.getStyle().setLeft(left, Unit.PX);
            glassElem.getStyle().setTop(top, Unit.PX);
            glassElem.getStyle().setWidth(width, Unit.PX);
            glassElem.getStyle().setHeight(height, Unit.PX);
            Document.get().getBody().appendChild(glassElem);
        } else {
            glassElem.getStyle().setLeft(left, Unit.PX);
            glassElem.getStyle().setTop(top, Unit.PX);
            glassElem.getStyle().setWidth(width, Unit.PX);
            glassElem.getStyle().setHeight(height, Unit.PX);
        }

        if (visibleClassName != null) {
            glassElem.addClassName(visibleClassName);
        }
    }

    public void hide() {
        if (glassElem != null) {
            if (visibleClassName != null) {
                glassElem.removeClassName(visibleClassName);
            }
            glassElem.removeFromParent();
        }
        glassAttached = false;
    }

    private void ensureGlass() {
        if (glassElem == null) {
            glassElem = Document.get().createDivElement();
            glassElem.setClassName(className);

            final Element glassElemMarker = Document.get().createDivElement();
            glassElemMarker.setClassName("marker");
            glassElem.appendChild(glassElemMarker);
        }
    }

    public Element getElement() {
        ensureGlass();
        return glassElem;
    }
}
