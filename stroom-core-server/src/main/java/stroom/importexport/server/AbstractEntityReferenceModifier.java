/*
 * Copyright 2017 Crown Copyright
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

package stroom.importexport.server;

import javassist.Modifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.BaseEntity;
import stroom.query.api.v1.DocRef;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Collection;

public abstract class AbstractEntityReferenceModifier {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractEntityReferenceModifier.class);

    private static final String MATCH_CLASSES = "stroom.";

    public boolean modify(final BaseEntity relative, final Object obj) {
        return deepModify(relative, obj, 0);
    }

    @SuppressWarnings("unchecked")
    private boolean deepModify(final BaseEntity relative, final Object obj, final int depth) {
        boolean modified = false;

        try {
            if (obj != null) {
                Class<?> clazz = obj.getClass();
                while (clazz != null) {
                    for (final Field field : clazz.getDeclaredFields()) {
                        if (!(Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers()))) {
                            field.setAccessible(true);
                            try {
                                final Object o = field.get(obj);
                                if (o != null && !o.getClass().isPrimitive()) {
                                    if (o instanceof Collection<?>) {
                                        final Collection<Object> collection = (Collection<Object>) o;
                                        for (final Object item : collection) {
                                            if (!item.getClass().isPrimitive()) {
                                                if (deepModify(relative, item, depth + 1)) {
                                                    modified = true;
                                                }
                                            }
                                        }
                                    } else if (!(o instanceof String) && field.getType().isArray()) {
                                        for (int i = 0; i < Array.getLength(o); i++) {
                                            final Object item = Array.get(o, i);
                                            if (!item.getClass().isPrimitive()) {
                                                if (deepModify(relative, item, depth + 1)) {
                                                    modified = true;
                                                }
                                            }
                                        }
                                    } else if (o instanceof DocRef) {
                                        final DocRef docRef = (DocRef) o;
                                        final DocRef result = processReference(relative, docRef);
                                        if (result != null) {
                                            field.set(obj, result);
                                            modified = true;
                                        }
                                    } else {
                                        final String className = o.getClass().getName();
                                        if (className.contains(MATCH_CLASSES)) {
                                            if (deepModify(relative, o, depth + 1)) {
                                                modified = true;
                                            }
                                        }
                                    }
                                }
                            } catch (final Exception e) {
                                LOGGER.error(e.getMessage(), e);
                            }
                        }
                    }
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

        return modified;
    }

    protected abstract DocRef processReference(BaseEntity relative, DocRef docRef);
}
