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

package stroom.statistics.impl.sql.client;

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
import stroom.statistics.impl.sql.client.presenter.StatisticsDataSourcePresenter;
import stroom.statistics.impl.sql.shared.CustomRollUpMask;
import stroom.statistics.impl.sql.shared.StatisticField;
import stroom.statistics.impl.sql.shared.StatisticResource;
import stroom.statistics.impl.sql.shared.StatisticRollUpType;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticType;
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
public class StatisticsPlugin extends DocumentPlugin<StatisticStoreDoc> {

    private static final StatisticResource STATISTIC_RESOURCE = GWT.create(StatisticResource.class);

    private final Provider<StatisticsDataSourcePresenter> editorProvider;
    private final RestFactory restFactory;

    @Inject
    public StatisticsPlugin(final EventBus eventBus,
                            final Provider<StatisticsDataSourcePresenter> editorProvider,
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
        return StatisticStoreDoc.TYPE;
    }

    @Override
    protected DocRef getDocRef(final StatisticStoreDoc document) {
        return DocRefUtil.create(document);
    }


    @Override
    protected DocumentEditPresenter<?, ?> createEditor() {
        return editorProvider.get();
    }

    @Override
    public void save(final DocumentTabData tabData) {
        if (tabData instanceof DocumentEditPresenter<?, ?>) {
            final DocumentEditPresenter<?, StatisticStoreDoc> presenter =
                    (DocumentEditPresenter<?, StatisticStoreDoc>) tabData;
            if (presenter.isDirty()) {
                final StatisticStoreDoc entity = presenter.getEntity();

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

    private void doConfirmSave(final DocumentEditPresenter<?, StatisticStoreDoc> presenter,
                               final StatisticStoreDoc entity,
                               final StatisticStoreDoc entityFromDb,
                               final TaskMonitorFactory taskMonitorFactory) {
        // get the persisted versions of the fields we care about
        final StatisticType prevType = entityFromDb.getStatisticType();
        final StatisticRollUpType prevRollUpType = entityFromDb.getRollUpType();
        final Long prevInterval = entityFromDb.getPrecision();
        final List<StatisticField> prevFieldList = entityFromDb.getStatisticFields();
        final Set<CustomRollUpMask> prevMaskSet = entityFromDb.getCustomRollUpMasks();

        final StatisticStoreDoc writtenEntity = presenter.write(entity);

        // if one of a select list of attributes has changed then warn the user
        // only need a null check on the engine name as the rest will never be
        // null
        if (!prevType.equals(writtenEntity.getStatisticType())
                || !prevRollUpType.equals(writtenEntity.getRollUpType())
                || !prevInterval.equals(writtenEntity.getPrecision())
                || !prevFieldList.equals(writtenEntity.getStatisticFields())
                || !prevMaskSet.equals(writtenEntity.getCustomRollUpMasks())) {
            ConfirmEvent.fireWarn(
                    this,
                    SafeHtmlUtils.fromTrustedString("Changes to the following attributes of a statistic data " +
                            "source:<br/><br/>Engine Name<br/>Statistic Type<br/>Precision<br/>Rollup Type<br/>" +
                            "Field list<br/>Custom roll-ups<br/><br/>can potentially cause corruption of the " +
                            "existing statistics data. Please ensure you understand the full consequences of the " +
                            "change.<br/><br/>" + "Do you wish to continue?"),
                    result -> {
                        if (result) {
                            doSave(presenter, writtenEntity, taskMonitorFactory);
                        } else {
                            // Re-enable popup buttons.
                        }
                    });
        } else {
            // user has changed some attributes we don't care about so just do
            // the save
            doSave(presenter, writtenEntity, taskMonitorFactory);
        }
    }

    private void doSave(final DocumentEditPresenter<?, StatisticStoreDoc> presenter,
                        final StatisticStoreDoc entity,
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
                     final Consumer<StatisticStoreDoc> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(STATISTIC_RESOURCE)
                .method(res -> res.fetch(docRef.getUuid()))
                .onSuccess(resultConsumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    @Override
    public void save(final DocRef docRef,
                     final StatisticStoreDoc document,
                     final Consumer<StatisticStoreDoc> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(STATISTIC_RESOURCE)
                .method(res -> res.update(document.getUuid(), document))
                .onSuccess(resultConsumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }
}
