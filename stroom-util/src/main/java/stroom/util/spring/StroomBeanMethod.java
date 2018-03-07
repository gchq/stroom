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

import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;

import java.lang.reflect.Method;

public class StroomBeanMethod {
    private final Method beanMethod;
    private Class<?> beanClass;

    public StroomBeanMethod(final Class<?> clazz, final Method beanMethod) {
        this.beanClass = clazz;
        this.beanMethod = beanMethod;
    }

    public Method getBeanMethod() {
        return beanMethod;
    }


    public Class<?> getBeanClass() {
        return beanClass;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append(beanClass.getName());
        builder.append(".");
        builder.append(beanMethod.getName());
        return builder.toString();
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof StroomBeanMethod)) {
            return false;
        }
        final StroomBeanMethod other = (StroomBeanMethod) obj;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(this.beanClass, other.beanClass);
        builder.append(this.beanMethod, other.beanMethod);
        return builder.isEquals();
    }

    @Override
    public int hashCode() {
        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(this.beanClass);
        builder.append(this.beanMethod);
        return builder.toHashCode();
    }
}
