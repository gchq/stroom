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

package stroom.security.client.presenter;

import stroom.util.shared.NullSafe;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import java.util.ArrayList;
import java.util.List;

public class ClassNameBuilder {

    private final List<String> classes = new ArrayList<>();

    public ClassNameBuilder addClassName(final String className) {
        if (NullSafe.isNonBlankString(className)) {
            classes.add(className.trim());
        }
        return this;
    }

    public ClassNameBuilder addAll(final ClassNameBuilder classNameBuilder) {
        if (classNameBuilder != null) {
            classes.addAll(classNameBuilder.classes);
        }
        return this;
    }

    /**
     * @return Space separated classes or an empty string
     */
    public String build() {
        return String.join(" ", classes);
    }

    /**
     * @return An immutable copy of the list of classes.
     */
    public List<String> buildAsList() {
        return List.copyOf(classes);
    }

    /**
     * Probably should be using {@link SafeHtmlUtil#getTemplate()} instead as if you are using
     * this, you are probably breaking the contract of {@link SafeHtmlUtils#fromSafeConstant(String)}
     *
     * @return The class string enclosed in {@code class="..."}.
     */
    @Deprecated
    public String buildClassAttribute() {
        if (classes.isEmpty()) {
            return "";
        } else {
            return " class=\"" + build() + "\"";
        }
    }

    public boolean isEmpty() {
        return classes.isEmpty();
    }
}
