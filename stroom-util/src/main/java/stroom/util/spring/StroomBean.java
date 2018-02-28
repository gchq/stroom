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

public class StroomBean {
    private final String beanName;
    private final Class<?> beanClass;

    StroomBean(final String beanName, final Class<?> beanClass) {
        this.beanName = beanName;
        this.beanClass = beanClass;
    }

    public String getBeanName() {
        return beanName;
    }

    public Class<?> getBeanClass() {
        return beanClass;
    }

    @Override
    public String toString() {
        return beanName;
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof StroomBean)) {
            return false;
        }
        final StroomBean other = (StroomBean) obj;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(this.beanName, other.beanName);
        builder.append(this.beanClass, other.beanClass);
        return builder.isEquals();
    }

    @Override
    public int hashCode() {
        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(this.beanName);
        builder.append(this.beanClass);
        return builder.toHashCode();
    }
}
