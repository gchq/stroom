/*
 * Copyright 2016 Crown Copyright
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

package stroom.util.spring;

import java.lang.annotation.Annotation;

public class StroomClassUtil {
    public static final <A extends Annotation> A getAnnotation(Object bean, final Class<A> annotation) {
        if (bean == null) {
            return null;
        }
        return getAnnotation(bean.getClass(), annotation);
    }

    public static final <A extends Annotation> A getAnnotation(Class<?> clazz, final Class<A> annotation) {
        if (clazz == null) {
            return null;
        }
        A annotationValue = clazz.getAnnotation(annotation);

        if (annotationValue != null) {
            return annotationValue;
        }
        return getAnnotation(clazz.getSuperclass(), annotation);
    }
}
