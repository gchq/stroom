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
 *
 */

package stroom.properties;


import stroom.entity.NamedEntityServiceImpl;
import stroom.entity.QueryAppender;
import stroom.entity.StroomEntityManager;
import stroom.entity.shared.BaseResultList;
import stroom.node.shared.FindGlobalPropertyCriteria;
import stroom.node.shared.GlobalProperty;
import stroom.security.Security;
import stroom.security.shared.PermissionNames;
import stroom.util.config.StroomProperties;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
public class GlobalPropertyServiceImpl extends NamedEntityServiceImpl<GlobalProperty, FindGlobalPropertyCriteria>
        implements GlobalPropertyService {

    @Inject
    GlobalPropertyServiceImpl(final StroomEntityManager entityManager,
                              final Security security) {
        super(entityManager, security);
    }

    @Override
    public GlobalProperty save(final GlobalProperty entity) {
        final GlobalProperty prop = super.save(entity);

        // Update the local property so that this node at least sees the latest value.
        if (prop != null) {
            StroomProperties.setProperty(prop.getName(), prop.getValue(), StroomProperties.Source.DB);
        }

        return prop;
    }

    @Override
    public BaseResultList<GlobalProperty> find(final FindGlobalPropertyCriteria criteria) {
        final BaseResultList<GlobalProperty> allProperties = super.find(new FindGlobalPropertyCriteria());
        final Map<String, GlobalProperty> allDatabase = allProperties
                .stream()
                .collect(Collectors.toMap(GlobalProperty::getName, Function.identity()));
        final Map<String, GlobalProperty> allDefault = GlobalProperties.getInstance().getGlobalProperties();

        final Set<String> keySet = new HashSet<>();
        keySet.addAll(allDefault.keySet().stream()
                .filter(defaultKey -> criteria.getName() == null || criteria.getName().isMatch(defaultKey))
                .collect(Collectors.toList()));

        keySet.addAll(allDatabase.keySet().stream()
                .filter(databaseKey -> criteria.getName() == null || criteria.getName().isMatch(databaseKey))
                .collect(Collectors.toList()));

        final List<String> keyList = new ArrayList<>(keySet);
        keyList.sort(Comparator.naturalOrder());

        final List<GlobalProperty> rtnList = new ArrayList<>();
        for (int i = 0; i < keyList.size(); i++) {
            boolean include = true;
            if (criteria.getPageRequest() != null) {
                include = criteria.getPageRequest().getOffset() <= i
                        && criteria.getPageRequest().getOffset() + criteria.getPageRequest().getLength() > i;
            }

            if (include) {
                final String key = keyList.get(i);
                if (criteria.getName() == null || criteria.getName().isMatch(key)) {
                    final GlobalProperty defaultProperty = allDefault.get(key);
                    GlobalProperty globalProperty = allDatabase.get(key);

                    if (globalProperty == null) {
                        if (criteria.isAddDefault()) {
                            globalProperty = new GlobalProperty();
                            if (defaultProperty != null) {
                                globalProperty.setValue(defaultProperty.getValue());
                                globalProperty.setDefaultValue(defaultProperty.getValue());
                                globalProperty.setName(defaultProperty.getName());
                                globalProperty.copyTransients(defaultProperty);
                            }
                        }
                    } else {
                        if (defaultProperty != null) {
                            globalProperty.copyTransients(defaultProperty);
                            globalProperty.setSource(GlobalProperty.SOURCE_DB);
                        } else {
                            globalProperty.setSource(GlobalProperty.SOURCE_DB_DEPRECATED);
                        }
                    }
                    if (globalProperty != null) {
                        rtnList.add(globalProperty);
                    }
                }
            }
        }

        return BaseResultList.createCriterialBasedList(rtnList, criteria, (long) keyList.size());
    }

    @Override
    public Class<GlobalProperty> getEntityClass() {
        return GlobalProperty.class;
    }

    @Override
    public FindGlobalPropertyCriteria createCriteria() {
        return new FindGlobalPropertyCriteria();
    }

    @Override
    protected QueryAppender<GlobalProperty, FindGlobalPropertyCriteria> createQueryAppender(StroomEntityManager entityManager) {
        return new GlobalPropertyQueryAppender(entityManager);
    }

    @Override
    protected String permission() {
        return PermissionNames.MANAGE_PROPERTIES_PERMISSION;
    }

    private static class GlobalPropertyQueryAppender extends QueryAppender<GlobalProperty, FindGlobalPropertyCriteria> {
        GlobalPropertyQueryAppender(final StroomEntityManager entityManager) {
            super(entityManager);
        }

        @Override
        protected void postLoad(final GlobalProperty globalProperty) {
            if (globalProperty != null) {
                final GlobalProperty defaultProperty = GlobalProperties.getInstance()
                        .getGlobalProperty(globalProperty.getName());

                if (defaultProperty != null) {
                    globalProperty.copyTransients(defaultProperty);
                }
                globalProperty.setSource(GlobalProperty.SOURCE_DB);
            }

            super.postLoad(globalProperty);
        }
    }
}
