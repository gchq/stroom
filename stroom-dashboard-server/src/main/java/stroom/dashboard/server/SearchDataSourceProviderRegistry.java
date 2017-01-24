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

package stroom.dashboard.server;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import stroom.query.SearchDataSourceProvider;
import stroom.util.spring.StroomBeanStore;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
public class SearchDataSourceProviderRegistry implements InitializingBean {
    private final Map<String, SearchDataSourceProvider> providers = new HashMap<String, SearchDataSourceProvider>();

    @Resource
    private StroomBeanStore stroomBeanStore;

    public SearchDataSourceProvider getProvider(final String type) {
        final SearchDataSourceProvider provider = providers.get(type);
        if (provider != null) {
            return provider;
        }
        return null;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        final Set<String> beanNames = stroomBeanStore.getStroomBeanByType(SearchDataSourceProvider.class);
        if (beanNames != null) {
            for (final String beanName : beanNames) {
                final Object bean = stroomBeanStore.getBean(beanName);
                if (bean != null) {
                    final SearchDataSourceProvider dataSourceProvider = (SearchDataSourceProvider) bean;
                    providers.put(dataSourceProvider.getEntityType(), dataSourceProvider);
                }
            }
        }
    }
}
