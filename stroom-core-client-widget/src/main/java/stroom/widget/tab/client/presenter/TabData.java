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

package stroom.widget.tab.client.presenter;

import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.widget.util.client.KeyBinding.Action;

import java.util.Objects;
import java.util.Optional;

public interface TabData {

    SvgImage getIcon();

    default IconColour getIconColour() {
        return IconColour.BLUE;
    }

    String getLabel();

    default Optional<String> getTooltip() {
        return Optional.empty();
    }

    boolean isCloseable();

    String getType();

    /**
     * @param action
     * @return True if the {@link Action} was consumed, i.e. it should not propagate
     * to parent elements
     */
    default boolean handleKeyAction(final Action action) {
        // override as required
        return false;
    }

    static String createDocumentationTooltip(final String documentType) {
        Objects.requireNonNull(documentType);
        return "Documentation to describe the content/purpose of this " + documentType + ".";
    }
}
