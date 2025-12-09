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

package stroom.cache.api;


import stroom.util.string.TemplateUtil;
import stroom.util.string.TemplateUtil.Template;

/**
 * A cache of {@link Template} instances, keyed by their {@link String} template.
 * The {@link String} templates contain named variables for replacement,
 * of the form {@code ${feed}_${type}}.
 */
public interface TemplatorCache {

    /**
     * Gets the {@link Template} for template.
     * {@link Template} is created using {@link TemplateUtil#parseTemplate(String)}.
     * If you need to create a {@link Template} with custom formatters then you can't use this
     * cache.
     *
     * @return The {@link Template} instance for the passed template. Will not be null.
     */
    Template getTemplate(final String template);

    /**
     * Evicts this template from the cache.
     */
    void evict(final String template);
}
