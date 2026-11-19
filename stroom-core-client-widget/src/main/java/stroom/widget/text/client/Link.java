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

package stroom.widget.text.client;

/// A link to an address (an `a` with an `href`, e.g. a content pack's licence), Stroom's version of
/// GWT's `Anchor`: its class is `link`, not GWT's `gwt-Anchor`; it looks as the page's links do. For
/// something that acts in Stroom rather than going somewhere, use a `LinkButton`.
public class Link extends com.google.gwt.user.client.ui.Anchor {

    private static final String BLOCK = "link";

    /// Makes a link with no text or address.
    public Link() {
        setStyleName(BLOCK);
    }
}
