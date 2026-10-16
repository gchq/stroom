/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.planb.impl;

import com.google.inject.BindingAnnotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Guice binding annotation for the {@link java.util.Set} of document-type
 * strings that produce {@link stroom.planb.shared.PlanBDocument} instances.
 *
 * <p>Each module that owns a {@link stroom.planb.shared.PlanBDocument} subtype
 * contributes its type string to the multibinding keyed by this annotation
 * (e.g. {@link stroom.planb.shared.PlanBDoc#TYPE} from {@code PlanBModule}
 * and {@link stroom.pathways.shared.TracesDoc#TYPE} from {@code PathwaysModule}).
 *
 * <p>The injected {@code Set<String>} is consumed by
 * {@link PlanBDocCacheImpl#getAll()} to enumerate all live PlanB store
 * documents without hardcoding type names in the cache implementation.
 */
@BindingAnnotation
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface PlanBDocumentTypes {

}
