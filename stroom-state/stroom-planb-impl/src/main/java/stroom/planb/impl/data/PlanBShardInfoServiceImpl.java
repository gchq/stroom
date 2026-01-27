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

package stroom.planb.impl.data;

import stroom.cluster.task.api.NodeNotFoundException;
import stroom.cluster.task.api.NullClusterStateException;
import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.docstore.shared.AbstractDoc;
import stroom.entity.shared.ExpressionCriteria;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.planb.impl.PlanBDocStore;
import stroom.planb.impl.db.StatePaths;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.StateType;
import stroom.query.api.Column;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.Format;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactories;
import stroom.query.common.v2.FieldInfoResultPageFactory;
import stroom.query.common.v2.ValuesFunctionFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.Values;
import stroom.query.language.functions.ValuesConsumer;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.searchable.api.Searchable;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.io.FileUtil;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class PlanBShardInfoServiceImpl implements Searchable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PlanBShardInfoServiceImpl.class);

    private final SecurityContext securityContext;
    private final TaskContextFactory taskContextFactory;
    private final Provider<NodeService> nodeServiceProvider;
    private final TargetNodeSetFactory targetNodeSetFactory;
    private final Provider<WebTargetFactory> webTargetFactoryProvider;
    private final Provider<NodeInfo> nodeInfoProvider;
    private final FieldInfoResultPageFactory fieldInfoResultPageFactory;
    private final ExpressionPredicateFactory expressionPredicateFactory;
    private final StatePaths statePaths;
    private final PlanBDocStore planBDocStore;
    private final ShardManager shardManager;

    @Inject
    public PlanBShardInfoServiceImpl(final SecurityContext securityContext,
                                     final TaskContextFactory taskContextFactory,
                                     final Provider<NodeService> nodeServiceProvider,
                                     final TargetNodeSetFactory targetNodeSetFactory,
                                     final Provider<WebTargetFactory> webTargetFactoryProvider,
                                     final Provider<NodeInfo> nodeInfoProvider,
                                     final FieldInfoResultPageFactory fieldInfoResultPageFactory,
                                     final ExpressionPredicateFactory expressionPredicateFactory,
                                     final StatePaths statePaths,
                                     final PlanBDocStore planBDocStore,
                                     final ShardManager shardManager) {
        this.securityContext = securityContext;
        this.taskContextFactory = taskContextFactory;
        this.nodeServiceProvider = nodeServiceProvider;
        this.targetNodeSetFactory = targetNodeSetFactory;
        this.webTargetFactoryProvider = webTargetFactoryProvider;
        this.nodeInfoProvider = nodeInfoProvider;
        this.fieldInfoResultPageFactory = fieldInfoResultPageFactory;
        this.expressionPredicateFactory = expressionPredicateFactory;
        this.statePaths = statePaths;
        this.planBDocStore = planBDocStore;
        this.shardManager = shardManager;
    }

    @Override
    public String getDataSourceType() {
        return PlanBShardInfoFields.PLAN_B_SHARD_INFO_PSEUDO_DOC_REF.getType();
    }

    @Override
    public List<DocRef> getDataSourceDocRefs() {
        return Collections.singletonList(PlanBShardInfoFields.PLAN_B_SHARD_INFO_PSEUDO_DOC_REF);
    }

    @Override
    public ResultPage<QueryField> getFieldInfo(final FindFieldCriteria criteria) {
        if (!PlanBShardInfoFields.PLAN_B_SHARD_INFO_PSEUDO_DOC_REF.equals(criteria.getDataSourceRef())) {
            return ResultPage.empty();
        }
        return fieldInfoResultPageFactory.create(criteria, getFields());
    }

    private List<QueryField> getFields() {
        return PlanBShardInfoFields.FIELDS;
    }

    @Override
    public int getFieldCount(final DocRef docRef) {
        return NullSafe.size(getFields());
    }

    @Override
    public void search(final ExpressionCriteria criteria,
                       final FieldIndex fieldIndex,
                       final DateTimeSettings dateTimeSettings,
                       final ValuesConsumer valuesConsumer,
                       final ErrorConsumer errorConsumer) {
        LOGGER.logDurationIfInfoEnabled(
                () -> taskContextFactory.context("Querying Plan B Info", taskContext ->
                                doSearch(criteria,
                                        fieldIndex,
                                        dateTimeSettings,
                                        valuesConsumer,
                                        errorConsumer,
                                        taskContext))
                        .run(),
                "Querying Plan B Info");
    }

    private ValueFunctionFactories<Values> createValueFunctionFactories(final FieldIndex fieldIndex) {
        return fieldName -> {
            final Integer index = fieldIndex.getPos(fieldName);
            if (index == null) {
                throw new RuntimeException("Unexpected field: " + fieldName);
            }
            if (PlanBShardInfoFields.BYTE_SIZE_FIELD.getFldName().equals(fieldName)) {
                return new ValuesFunctionFactory(Column.builder().format(Format.NUMBER).build(), index);
            }
            return new ValuesFunctionFactory(Column.builder().format(Format.TEXT).build(), index);
        };
    }

    private void doSearch(final ExpressionCriteria criteria,
                          final FieldIndex fieldIndex,
                          final DateTimeSettings dateTimeSettings,
                          final ValuesConsumer valuesConsumer,
                          final ErrorConsumer errorConsumer,
                          final TaskContext taskContext) {
        final ValueFunctionFactories<Values> valueFunctionFactories = createValueFunctionFactories(fieldIndex);
        final Predicate<Values> predicate = expressionPredicateFactory.createOptional(
                        criteria.getExpression(),
                        valueFunctionFactories,
                        dateTimeSettings)
                .orElse(values -> true);

        try {
            final String[] fields = fieldIndex.getFields();
            final List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
            final Set<String> set = targetNodeSetFactory.getEnabledActiveTargetNodeSet();
            for (final String nodeName : set) {
                // If this is the node that was contacted then just resolve it locally
                if (NodeCallUtil.shouldExecuteLocally(nodeInfoProvider.get(), nodeName)) {
                    final List<String[]> list = getStoreInfo(fieldIndex.getFields());
                    processResult(list, fields, predicate, valuesConsumer);
                } else {
                    final String url = NodeCallUtil.getBaseEndpointUrl(nodeInfoProvider.get(),
                            nodeServiceProvider.get(),
                            nodeName) + ResourcePaths.buildAuthenticatedApiPath(
                            PlanBRemoteQueryResource.BASE_PATH, PlanBRemoteQueryResource.GET_STORE_INFO);
                    final WebTarget webTarget = webTargetFactoryProvider.get().create(url);
                    final Runnable runnable = taskContextFactory.childContext(
                            taskContext,
                            "Calling node " + nodeName, childTaskContext -> {
                                try {
                                    final Response response = webTarget
                                            .request(MediaType.APPLICATION_JSON)
                                            .post(Entity.json(new PlanBShardInfoRequest(fieldIndex.getFields())));
                                    if (response.getStatus() == Status.NOT_FOUND.getStatusCode()) {
                                        throw new NotFoundException(response);
                                    } else if (response.getStatus() != Status.OK.getStatusCode()) {
                                        throw new WebApplicationException(response);
                                    }

                                    final PlanBShardInfoResponse info = response
                                            .readEntity(PlanBShardInfoResponse.class);
                                    processResult(info.getData(), fields, predicate, valuesConsumer);
                                } catch (final RuntimeException e) {
                                    errorConsumer.add(Severity.ERROR, nodeName, e::getMessage);
                                }
                            });
                    final CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(runnable);
                    completableFutures.add(completableFuture);
                }
            }

            CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0])).join();

        } catch (final NodeNotFoundException | NullClusterStateException e) {
            LOGGER.debug(e::getMessage, e);
        }
    }

    private void processResult(final List<String[]> list,
                               final String[] fields,
                               final Predicate<Values> predicate,
                               final ValuesConsumer consumer) {
        list.forEach(result -> {
            final Val[] values = new Val[fields.length];
            for (int i = 0; i < fields.length; i++) {
                final String field = fields[i];
                final String res = result[i];
                if (res == null) {
                    values[i] = ValNull.INSTANCE;
                } else if (Objects.equals(field, PlanBShardInfoFields.BYTE_SIZE_FIELD.getFldName())) {
                    values[i] = ValLong.create(Long.parseLong(res));
                } else {
                    values[i] = ValString.create(res);
                }
            }
            if (predicate.test(Values.of(values))) {
                consumer.accept(values);
            }
        });
    }

    public List<String[]> getStoreInfo(final String[] fields) {
        final List<String[]> results = new ArrayList<>();
        final Map<String, Optional<PlanBDoc>> map = new HashMap<>();

        // Add snapshots
        final Path snapshotDir = statePaths.getSnapshotDir();
        if (Files.isDirectory(snapshotDir)) {
            try (final Stream<Path> stream = Files.list(snapshotDir)) {
                stream.forEach(snapshotParent -> {
                    try {
                        final String uuid = snapshotParent.getFileName().toString();
                        final DocRef docRef = DocRef.builder().type(PlanBDoc.TYPE).uuid(uuid).build();
                        if (securityContext.isAdmin() ||
                            securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW)) {
                            final Optional<PlanBDoc> optionalPlanBDoc = map
                                    .computeIfAbsent(uuid, k ->
                                            Optional.ofNullable(planBDocStore.readDocument(DocRef
                                                    .builder()
                                                    .type(PlanBDoc.TYPE)
                                                    .uuid(uuid)
                                                    .build())));

                            try (final Stream<Path> innerStream = Files.list(snapshotParent)) {
                                innerStream.forEach(snapshot ->
                                        addData(fields,
                                                results,
                                                snapshot,
                                                optionalPlanBDoc,
                                                "Snapshot"));
                            } catch (final IOException e) {
                                LOGGER.debug(e::getMessage, e);
                            }
                        }
                    } catch (final DocumentNotFoundException e) {
                        // It is possible that a Plan B store is deleted before we try to query it.
                        LOGGER.debug(e::getMessage, e);
                    } catch (final RuntimeException e) {
                        LOGGER.error(e::getMessage, e);
                    }
                });
            } catch (final IOException e) {
                LOGGER.debug(e::getMessage, e);
            }
        }

        // Add shards
        final Path shardDir = statePaths.getShardDir();
        if (Files.isDirectory(shardDir)) {
            try (final Stream<Path> stream = Files.list(shardDir)) {
                stream.forEach(shard -> {
                    final String uuid = shard.getFileName().toString();
                    final Optional<PlanBDoc> optionalPlanBDoc = map
                            .computeIfAbsent(uuid, k ->
                                    Optional.ofNullable(planBDocStore.readDocument(DocRef
                                            .builder()
                                            .type(PlanBDoc.TYPE)
                                            .uuid(uuid)
                                            .build())));

                    addData(fields, results, shard, optionalPlanBDoc, "Shard");
                });
            } catch (final IOException e) {
                LOGGER.debug(e::getMessage, e);
            }
        }

        return results;
    }

    private void addData(final String[] fields,
                         final List<String[]> results,
                         final Path dir,
                         final Optional<PlanBDoc> optionalPlanBDoc,
                         final String type) {
        final String[] values = new String[fields.length];
        for (int i = 0; i < fields.length; i++) {
            final String field = fields[i];
            if (field.equals(PlanBShardInfoFields.NAME_FIELD.getFldName())) {
                values[i] = optionalPlanBDoc
                        .map(AbstractDoc::getName)
                        .orElse(null);
            } else if (field.equals(PlanBShardInfoFields.SCHEMA_TYPE_FIELD.getFldName())) {
                values[i] = optionalPlanBDoc
                        .map(doc -> NullSafe.get(
                                doc,
                                PlanBDoc::getStateType,
                                StateType::getDisplayValue)).orElse(null);
            } else if (field.equals(PlanBShardInfoFields.NODE_FIELD.getFldName())) {
                values[i] = nodeInfoProvider.get().getThisNodeName();
            } else if (field.equals(PlanBShardInfoFields.SHARD_TYPE_FIELD.getFldName())) {
                values[i] = type;
            } else if (field.equals(PlanBShardInfoFields.BYTE_SIZE_FIELD.getFldName())) {
                values[i] = String.valueOf(FileUtil.getByteSize(dir));
            } else if (field.equals(PlanBShardInfoFields.SETTINGS_FIELD.getFldName()) &&
                       optionalPlanBDoc.isPresent()) {
                final Shard shard = shardManager.getShardForMapName(optionalPlanBDoc.get().getName());
                if (shard != null) {
                    values[i] = shard.getInfo();
                }
            }
        }

        results.add(values);
    }
}
