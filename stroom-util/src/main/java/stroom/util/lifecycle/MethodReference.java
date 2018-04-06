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

package stroom.util.lifecycle;

import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;

import java.lang.reflect.Method;

public class MethodReference {
    private Class<?> clazz;
    private final Method method;

    public MethodReference(final Class<?> clazz, final Method method) {
        this.clazz = clazz;
        this.method = method;
    }

    public Class<?> getClazz() {
        return clazz;
    }

    public Method getMethod() {
        return method;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append(clazz.getName());
        builder.append(".");
        builder.append(method.getName());
        return builder.toString();
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof MethodReference)) {
            return false;
        }
        final MethodReference other = (MethodReference) obj;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(this.clazz, other.clazz);
        builder.append(this.method, other.method);
        return builder.isEquals();
    }

    @Override
    public int hashCode() {
        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(this.clazz);
        builder.append(this.method);
        return builder.toHashCode();
    }
}
