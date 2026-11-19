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

package stroom.widget.util.client;

import com.google.gwt.dom.client.Element;

/// Sets CSS custom properties on an element: the one way a value known only at runtime (a column
/// width, a splitter position, a colour from data) reaches the page. A class in the block's
/// stylesheet uses the property (`width: var(--block__width)`), so the element's own style holds
/// custom properties only, never ordinary inline styles.
///
/// Names follow the block they belong to: `--block__property`, e.g. `--data-grid__columns`.
public final class CssProperties {

    private CssProperties() {
    }

    /// Sets a custom property on an element.
    ///
    /// @param element The element.
    /// @param name    The property's name, with its leading `--`, e.g. `--data-grid__columns`.
    /// @param value   Its value, e.g. `120px`.
    public static native void set(Element element, String name, String value) /*-{
        element.style.setProperty(name, value);
    }-*/;

    /// Removes a custom property from an element, so the stylesheet's fallback applies again.
    ///
    /// @param element The element.
    /// @param name    The property's name, with its leading `--`.
    public static native void remove(Element element, String name) /*-{
        element.style.removeProperty(name);
    }-*/;
}
