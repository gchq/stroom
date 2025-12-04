/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.statistics.impl.hbase.client;

import stroom.alert.client.event.ConfirmEvent;
import stroom.core.client.ContentManager;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.document.client.DocumentPlugin;
import stroom.document.client.DocumentPluginEventManager;
import stroom.document.client.DocumentTabData;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.security.client.api.ClientSecurityContext;
import stroom.statistics.impl.hbase.client.presenter.StroomStatsStorePresenter;
import stroom.statistics.impl.hbase.shared.CustomRollUpMask;
import stroom.statistics.impl.hbase.shared.EventStoreTimeIntervalEnum;
import stroom.statistics.impl.hbase.shared.StatisticField;
import stroom.statistics.impl.hbase.shared.StatisticRollUpType;
import stroom.statistics.impl.hbase.shared.StatisticType;
import stroom.statistics.impl.hbase.shared.StatsStoreResource;
import stroom.statistics.impl.hbase.shared.StroomStatsStoreDoc;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.inject.Singleton;

@Singleton
public class StroomStatsStorePlugin extends DocumentPlugin<StroomStatsStoreDoc> {

    private static final StatsStoreResource STATS_STORE_RESOURCE = GWT.create(StatsStoreResource.class);

    private final Provider<StroomStatsStorePresenter> editorProvider;
    private final RestFactory restFactory;

    @Inject
    public StroomStatsStorePlugin(final EventBus eventBus,
                                  final Provider<StroomStatsStorePresenter> editorProvider,
                                  final RestFactory restFactory,
                                  final ContentManager contentManager,
                                  final DocumentPluginEventManager entityPluginEventManager,
                                  final ClientSecurityContext securityContext) {
        super(eventBus, contentManager, entityPluginEventManager, securityContext);
        this.editorProvider = editorProvider;
        this.restFactory = restFactory;
    }

    @Override
    public String getType() {
        return StroomStatsStoreDoc.TYPE;
    }

    @Override
    protected DocRef getDocRef(final StroomStatsStoreDoc document) {
        return DocRefUtil.create(document);
    }

    @Override
    protected DocumentEditPresenter<?, ?> createEditor() {
        return editorProvider.get();
    }

    @Override
    public void save(final DocumentTabData tabData) {
        if (tabData instanceof DocumentEditPresenter<?, ?>) {
            final DocumentEditPresenter<?, StroomStatsStoreDoc> presenter =
                    (DocumentEditPresenter<?, StroomStatsStoreDoc>) tabData;
            if (presenter.isDirty()) {
                final StroomStatsStoreDoc entity = presenter.getEntity();

                // re-load the entity from the database so we have the
                // persistent version, and not one that has had
                // fields added/removed/changed
                load(DocRefUtil.create(entity),
                        entityFromDb -> doConfirmSave(presenter, entity, entityFromDb, presenter),
                        throwable -> {
                        },
                        presenter);
            }
        }
    }

    private void doConfirmSave(final DocumentEditPresenter<?, StroomStatsStoreDoc> presenter,
                               final StroomStatsStoreDoc entity,
                               final StroomStatsStoreDoc entityFromDb,
                               final TaskMonitorFactory taskMonitorFactory) {
        // get the persisted versions of the fields we care about
        final StatisticType prevType = entityFromDb.getStatisticType();
        final StatisticRollUpType prevRollUpType = entityFromDb.getRollUpType();
        final EventStoreTimeIntervalEnum prevInterval = entityFromDb.getPrecision();
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
            ConfirmEvent.fireWarn(
                    this,
                    SafeHtmlUtils.fromTrustedString("Changes to the following attributes of a statistic data " +
                            "source:<br/><br/>Engine Name<br/>Statistic Type<br/>Precision<br/>Rollup Type<br/>" +
                            "Field list<br/>Custom roll-ups<br/><br/>can potentially cause corruption of the " +
                            "existing statistics data. Please ensure you understand the full consequences of " +
                            "the change.<br/><br/>" + "Do you wish to continue?"),
                    result -> {
                        if (result) {
                            doSave(presenter, entity, taskMonitorFactory);
                        } else {
                            // Re-enable popup buttons.
                        }
                    });
        } else {
            // user has changed some attributes we don't care about so just do
            // the save
            doSave(presenter, entity, taskMonitorFactory);
        }
    }

    private void doSave(final DocumentEditPresenter<?, StroomStatsStoreDoc> presenter,
                        final StroomStatsStoreDoc entity,
                        final TaskMonitorFactory taskMonitorFactory) {
        save(DocRefUtil.create(entity), entity, doc ->
                        presenter.read(DocRefUtil.create(doc), doc,
                                presenter.isReadOnly()),
                throwable -> {
                },
                taskMonitorFactory);
    }

    @Override
    public void load(final DocRef docRef,
                     final Consumer<StroomStatsStoreDoc> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(STATS_STORE_RESOURCE)
                .method(res -> res.fetch(docRef.getUuid()))
                .onSuccess(resultConsumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    @Override
    public void save(final DocRef docRef,
                     final StroomStatsStoreDoc document,
                     final Consumer<StroomStatsStoreDoc> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(STATS_STORE_RESOURCE)
                .method(res -> res.update(document.getUuid(), document))
                .onSuccess(resultConsumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }
}
