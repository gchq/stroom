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
import stroom.util.shared.HasDescription;
import stroom.util.shared.NullSafe;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import java.util.Objects;

public class SimpleSelectionItemWrapper<T> implements SelectionItem {

    private final String label;
    private final T item;
    private final RenderFunction<T> renderFunction;

    public SimpleSelectionItemWrapper(final String label,
                                      final T item,
                                      final RenderFunction<T> renderFunction) {
        this.label = label;
        this.item = item;
        this.renderFunction = renderFunction;
    }

    public SimpleSelectionItemWrapper(final String label,
                                      final T item) {
        this.label = label;
        this.item = item;
        this.renderFunction = null;
    }

    public T getItem() {
        return item;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public String getDescription() {
        if (item instanceof final HasDescription hasDescription) {
            return hasDescription.getDescription();
        } else {
            return SelectionItem.super.getDescription();
        }
    }

    @Override
    public SafeHtml getRenderedLabel() {
        if (renderFunction != null) {
            final SafeHtml safeHtml = renderFunction.apply(label, item);
            return NullSafe.requireNonNullElse(safeHtml, SafeHtmlUtils.EMPTY_SAFE_HTML);
        } else {
            // No render func so let the default method handle it as simple text with no markup
            return SelectionItem.super.getRenderedLabel();
        }
    }

    @Override
    public boolean hasRenderedLabel() {
        return renderFunction != null || NullSafe.isNonBlankString(getDescription());
    }

    @Override
    public SvgImage getIcon() {
        return null;
    }

    @Override
    public boolean isHasChildren() {
        return false;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SimpleSelectionItemWrapper<?>)) {
            return false;
        }
        final SimpleSelectionItemWrapper<?> that = (SimpleSelectionItemWrapper<?>) o;
        return Objects.equals(item, that.item);
    }

    @Override
    public int hashCode() {
        return Objects.hash(item);
    }

    @Override
    public String toString() {
        return "SimpleSelectionItemWrapper{" +
               "label='" + label + '\'' +
               ", item=" + item +
               '}';
    }


    // --------------------------------------------------------------------------------


    @FunctionalInterface
    public interface RenderFunction<T> {

        SafeHtml apply(String label, T item);
    }
}
