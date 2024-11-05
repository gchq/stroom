/*
 * Copyright 2024 Crown Copyright
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

package stroom.item.client;

import stroom.svg.shared.SvgImage;
import stroom.util.shared.GwtNullSafe;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

public interface SelectionItem {

    /**
     * @return The plain text label for the item
     */
    String getLabel();

    /**
     * @return The rendered HTML form of the label, which by default is simply the label text with no markup.
     * Implementations can choose to render the label how they like.
     */
    default SafeHtml getRenderedLabel() {
        final String label = getLabel();
        if (GwtNullSafe.isBlankString(label)) {
            return SafeHtmlUtils.EMPTY_SAFE_HTML;
        } else {
            return SafeHtmlUtils.fromString(getLabel());
        }
    }

    default boolean hasRenderedLabel() {
        return false;
    }

    SvgImage getIcon();

    default String getIconTooltip() {
        return null;
    }

    boolean isHasChildren();
}
