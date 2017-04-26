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

package stroom.node.server;

import stroom.entity.server.NamedEntityServiceImpl;
import stroom.entity.server.QueryAppender;
import stroom.entity.server.util.StroomEntityManager;
import stroom.entity.server.util.BaseEntityUtil;
import stroom.entity.shared.BaseResultList;
import stroom.node.shared.FindGlobalPropertyCriteria;
import stroom.node.shared.GlobalProperty;
import stroom.node.shared.GlobalPropertyService;
import stroom.security.Secured;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import stroom.util.config.StroomProperties;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Transactional
@Secured(GlobalProperty.MANAGE_PROPERTIES_PERMISSION)
@Component
public class GlobalPropertyServiceImpl extends NamedEntityServiceImpl<GlobalProperty, FindGlobalPropertyCriteria>
        implements GlobalPropertyService {

    @Inject
    GlobalPropertyServiceImpl(final StroomEntityManager entityManager) {
        super(entityManager);
    }

    @Override
    public GlobalProperty save(final GlobalProperty entity) throws RuntimeException {
        final GlobalProperty prop = super.save(entity);

        // Update the local property so that this node at least sees the latest value.
        if (prop != null) {
            StroomProperties.setProperty(prop.getName(), prop.getValue(), StroomProperties.Source.DB);
        }

        return prop;
    }

    @Override
    public BaseResultList<GlobalProperty> find(final FindGlobalPropertyCriteria criteria) throws RuntimeException {
        final Map<String, GlobalProperty> allDatabase = BaseEntityUtil
                .toNameMap(super.find(new FindGlobalPropertyCriteria()));
        final Map<String, GlobalProperty> allDefault = GlobalProperties.getInstance().getGlobalProperties();

        final Set<String> keySet = new HashSet<>();
        keySet.addAll(allDefault.keySet().stream()
                .filter(defaultKey -> criteria.getName() == null || criteria.getName().isMatch(defaultKey))
                .collect(Collectors.toList()));

        keySet.addAll(allDatabase.keySet().stream()
                .filter(databaseKey -> criteria.getName() == null || criteria.getName().isMatch(databaseKey))
                .collect(Collectors.toList()));

        final List<String> keyList = new ArrayList<>();
        keyList.addAll(keySet);
        Collections.sort(keyList);

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

        return BaseResultList.createCriterialBasedList(rtnList, criteria, Long.valueOf(keyList.size()));
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

    private static class GlobalPropertyQueryAppender extends QueryAppender<GlobalProperty, FindGlobalPropertyCriteria> {
        public GlobalPropertyQueryAppender(final StroomEntityManager entityManager) {
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
