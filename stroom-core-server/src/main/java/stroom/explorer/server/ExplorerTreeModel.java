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

package stroom.explorer.server;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import stroom.entity.server.MarshalOptions;
import stroom.entity.server.event.EntityEvent;
import stroom.entity.server.event.EntityEventBus;
import stroom.security.Insecure;
import stroom.util.spring.StroomBeanStore;
import stroom.util.task.TaskScopeRunnable;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class ExplorerTreeModel implements InitializingBean {
    private static final long TEN_MINUTES = 1000 * 60 * 10;
    private final Map<String, ExplorerDataProvider> providerTypeMap = new HashMap<>();
    private final List<ExplorerDataProvider> providers = new ArrayList<>();
    private final StroomBeanStore stroomBeanStore;
    private final EntityEventBus eventBus;
    private final ReentrantLock treeBuildLock = new ReentrantLock();

    private volatile TreeModel treeModel;
    private volatile long lastBuildTime;
    private volatile boolean rebuildRequired;

    @Inject
    public ExplorerTreeModel(final StroomBeanStore stroomBeanStore, final EntityEventBus eventBus) {
        this.stroomBeanStore = stroomBeanStore;
        this.eventBus = eventBus;
    }

    @Insecure
    public TreeModel getModel() {
        // If the tree is more than 10 minutes old then rebuild it.
        if (!rebuildRequired && lastBuildTime < System.currentTimeMillis() - TEN_MINUTES) {
            rebuildRequired = true;
            treeModel = null;
        }

        TreeModel model = treeModel;
        if (model == null || rebuildRequired) {
            // Try and get the map under lock.
            treeBuildLock.lock();
            try {
                model = treeModel;
                while (model == null || rebuildRequired) {
                    // Record the last time we built the full tree.
                    lastBuildTime = System.currentTimeMillis();
                    rebuildRequired = false;
                    model = createModel();
                }

                // Record the last time we built the full tree.
                lastBuildTime = System.currentTimeMillis();
                treeModel = model;
            } finally {
                treeBuildLock.unlock();
            }
        }

        return model;
    }

    private TreeModel createModel() {
        final TreeModel treeModel = new TreeModelImpl();
        final TaskScopeRunnable runnable = new TaskScopeRunnable(null) {
            @Override
            protected void exec() {
                // We don't need to do marshaling.
                final MarshalOptions marshalOptions = stroomBeanStore.getBean(MarshalOptions.class);
                marshalOptions.setDisabled(true);

                // Add explorer items from all providers to the new map.
                for (final ExplorerDataProvider provider : providers) {
                    final ExplorerDataProvider dataProvider = stroomBeanStore.getBean(provider.getClass());
                    dataProvider.addItems(treeModel);
                }
            }
        };

        runnable.run();
        return treeModel;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        final EntityEvent.Handler handler = new EntityEvent.Handler() {
            @Override
            public void onChange(final EntityEvent event) {
                // Remember that we need to rebuild the tree.
                rebuildRequired = true;
                treeModel = null;
            }
        };

        for (final String beanName : stroomBeanStore.getStroomBean(ProvidesExplorerData.class)) {
            final Object bean = stroomBeanStore.getBean(beanName);

            if (!(bean instanceof ExplorerDataProvider)) {
                throw new RuntimeException(
                        "Class '" + beanName + "' annotated with ProvidesExplorerData is not an ExplorerDataProvider");
            }

            final ExplorerDataProvider provider = (ExplorerDataProvider) bean;

            // If all checked out then store this provider.
            final ExplorerDataProvider existing = providerTypeMap.put(provider.getType(), provider);

            if (existing != null) {
                throw new RuntimeException(
                        "A provider has already been registered for type '" + provider.getType() + "' existing="
                                + existing.getClass().getSimpleName() + " new=" + provider.getClass().getSimpleName());
            }

            providers.add(provider);

            // Listen to changes to entities that are provided in the tree.
            eventBus.addHandler(handler, provider.getType());
        }

        // Sort the providers so nodes are ordered.
        final Comparator<ExplorerDataProvider> comparator = new Comparator<ExplorerDataProvider>() {
            @Override
            public int compare(final ExplorerDataProvider o1, final ExplorerDataProvider o2) {
                return Integer.compare(o1.getPriority(), o2.getPriority());
            }
        };
        Collections.sort(providers, comparator);
    }

    public ExplorerDataProvider getProvider(final String type) {
        return providerTypeMap.get(type);
    }

    public Collection<String> getTypes() {
        return providerTypeMap.keySet();
    }
}
