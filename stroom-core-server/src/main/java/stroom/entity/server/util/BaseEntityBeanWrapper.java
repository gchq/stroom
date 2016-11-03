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

package stroom.entity.server.util;

import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.DocumentEntity;
import stroom.entity.shared.NamedEntity;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.core.convert.TypeDescriptor;

import java.util.HashSet;
import java.util.Set;

public class BaseEntityBeanWrapper {
    private BeanWrapper beanWrapper;

    public BaseEntityBeanWrapper(BaseEntity baseEntity) {
        beanWrapper = new BeanWrapperImpl(baseEntity);
    }

    public BaseEntity getBaseEntity() {
        return (BaseEntity) beanWrapper.getWrappedInstance();
    }

    public boolean isPropertyBaseEntitySet(String propertyName) {
        Class<?> clazz = beanWrapper.getPropertyType(propertyName);
        return Set.class.isAssignableFrom(clazz);
    }

    public boolean isPropertyBaseEntity(String propertyName) {
        Class<?> clazz = beanWrapper.getPropertyType(propertyName);
        return BaseEntity.class.isAssignableFrom(clazz);
    }

    public boolean isPropertyGroupedEntity(String propertyName) {
        Class<?> clazz = beanWrapper.getPropertyType(propertyName);
        return DocumentEntity.class.isAssignableFrom(clazz);
    }

    @SuppressWarnings("unchecked")
    public Class<? extends NamedEntity> getPropertyBaseEntityType(String propertyName) {
        if (isPropertyBaseEntitySet(propertyName)) {
            TypeDescriptor typeDescriptor = beanWrapper.getPropertyTypeDescriptor(propertyName);
            return (Class<NamedEntity>) typeDescriptor.getElementTypeDescriptor().getType();

        }
        return (Class<NamedEntity>) beanWrapper.getPropertyType(propertyName);
    }

    @SuppressWarnings("rawtypes")
    public void clearPropertySet(String propertyName) {
        beanWrapper.setPropertyValue(propertyName, new HashSet());
    }

    @SuppressWarnings({ "unchecked" })
    public <T extends BaseEntity> void addToPropertySet(String propertyName, T baseEntity) {
        Set<T> set = (Set<T>) beanWrapper.getPropertyValue(propertyName);
        set.add(baseEntity);
    }

    public <T extends BaseEntity> void setPropertyBaseEntity(String propertyName, T baseEntity) {
        beanWrapper.setPropertyValue(propertyName, baseEntity);
    }

    public void setPropertyValue(String propertyName, Object value) {
        beanWrapper.setPropertyValue(propertyName, value);
    }

    public Object getPropertyValue(String propertyName) {
        return beanWrapper.getPropertyValue(propertyName);
    }

    public Class<?> getEntityClass() {
        return beanWrapper.getWrappedClass();
    }

    public String getEntityType() {
        return ((BaseEntity) beanWrapper.getWrappedInstance()).getType();
    }

}
