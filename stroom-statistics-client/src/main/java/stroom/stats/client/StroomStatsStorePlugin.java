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

package stroom.stats.client;

import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.alert.client.event.ConfirmEvent;
import stroom.core.client.ContentManager;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.EntityPlugin;
import stroom.entity.client.EntityPluginEventManager;
import stroom.entity.client.EntityTabData;
import stroom.entity.client.presenter.EntityEditPresenter;
import stroom.entity.shared.DocRefUtil;
import stroom.node.client.ClientPropertyCache;
import stroom.security.client.ClientSecurityContext;
import stroom.statistics.shared.StatisticType;
import stroom.stats.client.presenter.StroomStatsStorePresenter;
import stroom.stats.shared.CustomRollUpMask;
import stroom.stats.shared.StatisticField;
import stroom.stats.shared.StatisticRollUpType;
import stroom.stats.shared.StroomStatsStoreEntity;

import java.util.List;
import java.util.Set;

public class StroomStatsStorePlugin extends EntityPlugin<StroomStatsStoreEntity> {
    private final Provider<StroomStatsStorePresenter> editorProvider;

    @Inject
    public StroomStatsStorePlugin(final EventBus eventBus, final Provider<StroomStatsStorePresenter> editorProvider,
                                  final ClientDispatchAsync dispatcher, final ClientSecurityContext securityContext,
                                  final ContentManager contentManager, final EntityPluginEventManager entityPluginEventManager,
                                  final ClientPropertyCache clientPropertyCache) {
        super(eventBus, dispatcher, securityContext, contentManager, entityPluginEventManager);
        this.editorProvider = editorProvider;
    }

    @Override
    public String getType() {
        return StroomStatsStoreEntity.ENTITY_TYPE;
    }

    @Override
    protected EntityEditPresenter<?, ?> createEditor() {
        return editorProvider.get();
    }

    @Override
    public void save(final EntityTabData tabData) {
        if (tabData != null && tabData instanceof EntityEditPresenter<?, ?>) {
            final EntityEditPresenter<?, StroomStatsStoreEntity> presenter = (EntityEditPresenter<?, StroomStatsStoreEntity>) tabData;
            if (presenter.isDirty()) {
                final StroomStatsStoreEntity entity = presenter.getEntity();

                // re-load the entity from the database so we have the
                // persistent version, and not one that has had
                // fields added/removed/changed
                load(DocRefUtil.create(entity)).onSuccess(entityFromDb -> doConfirmSave(presenter, entity, entityFromDb));
            }
        }
    }

    private void doConfirmSave(final EntityEditPresenter<?, StroomStatsStoreEntity> presenter,
                               final StroomStatsStoreEntity entity, final StroomStatsStoreEntity entityFromDb) {
        // get the persisted versions of the fields we care about
        final StatisticType prevType = entityFromDb.getStatisticType();
        final StatisticRollUpType prevRollUpType = entityFromDb.getRollUpType();
        final String prevInterval = entityFromDb.getPrecision();
        final List<StatisticField> prevFieldList = entityFromDb.getStatisticFields();
        final Set<CustomRollUpMask> prevMaskSet = entityFromDb.getCustomRollUpMasks();

        presenter.write(entity);

        // if one of a select list of attributes has changed then warn the user
        // only need a null check on the engine name as the rest will never be
        // null
        if (entityFromDb != null && (
                !prevType.equals(entity.getStatisticType()) ||
                        !prevRollUpType.equals(entity.getRollUpType()) ||
                        !prevInterval.equals(entity.getPrecision()) ||
                        !prevFieldList.equals(entity.getStatisticFields()) ||
                        !prevMaskSet.equals(entity.getCustomRollUpMasks()))) {
            ConfirmEvent.fireWarn(this, SafeHtmlUtils
                            .fromTrustedString("Changes to the following attributes of a statistic data source:<br/><br/>"
                                    + "Engine Name<br/>Statistic Type<br/>Precision<br/>Rollup Type<br/>Field list<br/>Custom roll-ups<br/><br/>"
                                    + "can potentially cause corruption of the existing statistics data. Please ensure you "
                                    + "understand the full consequences of the change.<br/><br/>" + "Do you wish to continue?"),
                    result -> {
                        if (result) {
                            doSave(presenter, entity);
                        } else {
                            // Re-enable popup buttons.
                        }
                    });
        } else {
            // user has changed some attributes we don't care about so just do
            // the save
            doSave(presenter, entity);
        }
    }

    private void doSave(final EntityEditPresenter<?, StroomStatsStoreEntity> presenter,
                        final StroomStatsStoreEntity entity) {
        save(entity).onSuccess(presenter::read);
    }
}
