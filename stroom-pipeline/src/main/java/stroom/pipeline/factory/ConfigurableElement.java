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

package stroom.pipeline.factory;

import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.svg.shared.SvgImage;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// TODO 04/02/2022 AT: Consider using markdown for the description
// and then using a markdown->HTML lib, e.g. commonmark-java to produce
// html for help in the UI.

/**
 * These fields and their values will be used by
 * GeneratePipelineElementsDoc to generate stroom-docs
 * documentation for the pipeline elements.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigurableElement {

    String type();

    String displayValue() default "";

    Category category() default Category.INTERNAL;

    String[] roles();

    SvgImage icon();

    /**
     * A description of what this pipeline element does.
     * For paragraph breaks use double <pre>\n\n</pre>.
     * Only add single new lines for a new sentence, don't add hard breaks.
     * No HTML tags as this may be used in both the UI and markdown
     * docs.
     */
    String description() default "";
}
