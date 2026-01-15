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

package stroom.item.client;

import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

public interface SelectionItem {

    /**
     * @return The plain text label for the item
     */
    String getLabel();

    /**
     * @return The descriptive text for the item, i.e. for use in a tooltip
     */
    default String getDescription() {
        return null;
    }

    /**
     * @return The rendered HTML form of the label, which by default is simply the label text with no markup.
     * Implementations can choose to render the label how they like.
     */
    default SafeHtml getRenderedLabel() {
        final String label = getLabel();
        if (NullSafe.isBlankString(label)) {
            return SafeHtmlUtils.EMPTY_SAFE_HTML;
        } else {
            final String description = getDescription();
            if (NullSafe.isNonBlankString(description)) {
                return new HtmlBuilder()
                        .span(spanBuilder ->
                                        spanBuilder.append(label),
                                Attribute.title(description))
                        .toSafeHtml();
            } else {
                return SafeHtmlUtils.fromString(getLabel());
            }
        }
    }

    default boolean hasRenderedLabel() {
        return NullSafe.isNonBlankString(getDescription());
    }

    SvgImage getIcon();

    default String getIconTooltip() {
        return null;
    }

    boolean isHasChildren();
}
