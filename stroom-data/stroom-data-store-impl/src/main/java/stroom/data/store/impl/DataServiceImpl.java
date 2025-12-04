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

package stroom.data.store.impl;

import stroom.data.shared.DataInfoSection;
import stroom.data.shared.DataInfoSection.Entry;
import stroom.data.shared.UploadDataRequest;
import stroom.data.store.api.AttributeMapFactory;
import stroom.data.store.api.DataService;
import stroom.data.store.api.Store;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.docstore.shared.DocRefUtil;
import stroom.feed.api.FeedProperties;
import stroom.feed.api.FeedStore;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.MetaService;
import stroom.meta.shared.DataRetentionFields;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaExpressionUtil;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.shared.AbstractFetchDataResult;
import stroom.pipeline.shared.FetchDataRequest;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.state.CurrentUserHolder;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.MetaDataHolder;
import stroom.pipeline.state.MetaHolder;
import stroom.pipeline.state.PipelineHolder;
import stroom.resource.api.ResourceStore;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.task.api.TaskContextFactory;
import stroom.ui.config.shared.SourceConfig;
import stroom.util.date.DateUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.pipeline.scope.PipelineScopeRunnable;
import stroom.util.shared.Message;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.ResultPage;
import stroom.util.zip.ZipUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class DataServiceImpl implements DataService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DataServiceImpl.class);

    private final ResourceStore resourceStore;
    private final DataUploadTaskHandler dataUploadTaskHandlerProvider;
    private final DataDownloadTaskHandler dataDownloadTaskHandlerProvider;
    private final DocRefInfoService docRefInfoService;
    private final MetaService metaService;
    private final AttributeMapFactory attributeMapFactory;
    private final TempDirProvider tempDirProvider;
    private final SecurityContext securityContext;
    private final FeedStore feedStore;

    private final DataFetcher dataFetcher;

    @Inject
    DataServiceImpl(final ResourceStore resourceStore,
                    final DataUploadTaskHandler dataUploadTaskHandler,
                    final DataDownloadTaskHandler dataDownloadTaskHandler,
                    final DocRefInfoService docRefInfoService,
                    final MetaService metaService,
                    final AttributeMapFactory attributeMapFactory,
                    final SecurityContext securityContext,
                    final FeedStore feedStore,
                    final Store streamStore,
                    final FeedProperties feedProperties,
                    final Provider<FeedHolder> feedHolderProvider,
                    final Provider<MetaDataHolder> metaDataHolderProvider,
                    final Provider<PipelineHolder> pipelineHolderProvider,
                    final Provider<MetaHolder> metaHolderProvider,
                    final Provider<CurrentUserHolder> currentUserHolderProvider,
                    final PipelineStore pipelineStore,
                    final Provider<PipelineFactory> pipelineFactoryProvider,
                    final Provider<ErrorReceiverProxy> errorReceiverProxyProvider,
                    final PipelineDataCache pipelineDataCache,
                    final PipelineScopeRunnable pipelineScopeRunnable,
                    final SourceConfig sourceConfig,
                    final TaskContextFactory taskContextFactory,
                    final TempDirProvider tempDirProvider) {

        this.resourceStore = resourceStore;
        this.dataUploadTaskHandlerProvider = dataUploadTaskHandler;
        this.dataDownloadTaskHandlerProvider = dataDownloadTaskHandler;
        this.docRefInfoService = docRefInfoService;
        this.metaService = metaService;
        this.attributeMapFactory = attributeMapFactory;
        this.securityContext = securityContext;
        this.feedStore = feedStore;
        this.tempDirProvider = tempDirProvider;

        this.dataFetcher = new DataFetcher(streamStore,
                feedProperties,
                feedHolderProvider,
                metaDataHolderProvider,
                pipelineHolderProvider,
                metaHolderProvider,
                currentUserHolderProvider,
                pipelineStore,
                pipelineFactoryProvider,
                errorReceiverProxyProvider,
                pipelineDataCache,
                securityContext,
                pipelineScopeRunnable,
                sourceConfig,
                taskContextFactory);
    }

    @Override
    public ResourceGeneration download(final FindMetaCriteria criteria) {
        return securityContext.secureResult(AppPermission.EXPORT_DATA_PERMISSION, () -> {
            // Import file.
            final ResourceKey resourceKey = resourceStore.createTempFile("StroomData.zip");
            final Path tempFile = resourceStore.getTempFile(resourceKey);
            final Path outputDir = tempDirProvider.get().resolve(UUID.randomUUID().toString());
            try {
                final DataDownloadSettings settings = new DataDownloadSettings();
                final DataDownloadResult result = dataDownloadTaskHandlerProvider.downloadData(
                        criteria,
                        outputDir,
                        "data",
                        settings);

                if (result.getRecordsWritten() == 0) {
                    if (result.getMessageList() != null && !result.getMessageList().isEmpty()) {
                        throw new RuntimeException("Download failed with errors: " +
                                                   result.getMessageList().stream()
                                                           .map(Message::getMessage)
                                                           .collect(Collectors.joining(", ")));
                    }
                }

                try {
                    // Find out how many files were written.
                    final List<Path> list;
                    try (final Stream<Path> stream = Files.list(outputDir)) {
                        list = stream.toList();
                    }

                    if (list.size() == 1) {
                        // If we have only 1 file then just move it to the temp file.
                        Files.move(list.getFirst(), tempFile);
                    } else if (list.size() > 1) {
                        // If we have more than 1 file then zip them all.
                        ZipUtil.zip(tempFile, outputDir);
                    } else {
                        throw new RuntimeException("Download failed with no files written");
                    }
                } catch (final IOException e) {
                    throw new RuntimeException("Download failed with errors: " + e.getMessage());
                }

                return new ResourceGeneration(resourceKey, result.getMessageList());
            } finally {
                try {
                    FileUtil.deleteDir(outputDir);
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                }
            }
        });
    }

    @Override
    public ResourceKey upload(final UploadDataRequest request) {

        // Feed names are unique so just get the first
        final DocRef feedDocRef = feedStore.findByName(request.getFeedName())
                .stream()
                .findFirst()
                .orElseThrow(() ->
                        new RuntimeException("Unable to find feed document with name " + request.getFeedName()));

        if (!securityContext.hasDocumentPermission(feedDocRef, DocumentPermission.EDIT)) {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to update feed " + request.getFeedName());
        }

        return securityContext.secureResult(AppPermission.IMPORT_DATA_PERMISSION, () -> {
            try {
                // Import file.
                final Path tempFile = resourceStore.getTempFile(request.getKey());

                dataUploadTaskHandlerProvider.uploadData(
                        request.getFileName(),
                        tempFile,
                        request.getFeedName(),
                        request.getStreamTypeName(),
                        request.getEffectiveMs(),
                        request.getMetaData());

            } finally {
                // Delete the import if it was successful
                resourceStore.deleteTempFile(request.getKey());
            }

            return request.getKey();
        });
    }

    @Override
    public Map<String, String> metaAttributes(final long id) {
        return attributeMapFactory.getAttributes(id);
    }

    @Override
    public List<DataInfoSection> info(final long id) {
        final ResultPage<MetaRow> metaRows = metaService.findDecoratedRows(
                new FindMetaCriteria(MetaExpressionUtil.createDataIdExpression(id)));
        final MetaRow metaRow = metaRows.getFirst();
        final List<DataInfoSection> sections = new ArrayList<>();
        if (metaRow == null) {
            final List<DataInfoSection.Entry> entries = new ArrayList<>(1);
            entries.add(new DataInfoSection.Entry("Deleted Stream Id", String.valueOf(id)));
            sections.add(new DataInfoSection("Stream", entries));

        } else {
            sections.add(new DataInfoSection("Stream", getStreamEntries(metaRow.getMeta())));

            final List<DataInfoSection.Entry> entries = new ArrayList<>();

            final Map<String, String> attributeMap = metaRow.getAttributes();
            final Map<String, String> additionalAttributes = attributeMapFactory.getAttributes(
                    metaRow.getMeta().getId());
            final String files = additionalAttributes.remove("Files");
            attributeMap.putAll(additionalAttributes);

            final List<String> sortedKeys = attributeMap
                    .keySet()
                    .stream()
                    .sorted()
                    .toList();
            sortedKeys.forEach(key -> {
                final String value = attributeMap.get(key);
                if (value != null &&
                    // We are going to add retention entries separately.
                    !DataRetentionFields.RETENTION_AGE.equals(key) &&
                    !DataRetentionFields.RETENTION_UNTIL.equals(key) &&
                    !DataRetentionFields.RETENTION_RULE.equals(key)) {

                    if (MetaFields.DURATION.getFldName().equals(key)) {
                        entries.add(new DataInfoSection.Entry(key, convertDuration(value)));
                    } else if (key.toLowerCase().contains("time")) {
                        entries.add(new DataInfoSection.Entry(key, convertTime(value)));
                    } else if (key.toLowerCase().contains("size")) {
                        entries.add(new DataInfoSection.Entry(key, convertSize(value)));
                    } else if (key.toLowerCase().contains("count")) {
                        entries.add(new DataInfoSection.Entry(key, convertCount(value)));
                    } else {
                        entries.add(new DataInfoSection.Entry(key, value));
                    }
                }
            });
            sections.add(new DataInfoSection("Attributes", entries));

            // Add additional data retention information.
            sections.add(new DataInfoSection("Retention", getDataRententionEntries(attributeMap)));

            // Files are often very long so change the delimiter to \n
            final List<Entry> fileEntries = AttributeMapUtil.valueAsList(files)
                    .stream()
                    .map(val -> new Entry("", val))
                    .toList();

            sections.add(new DataInfoSection("Files", fileEntries));
        }
        return sections;
    }

    @Override
    public AbstractFetchDataResult fetch(final FetchDataRequest request) {
        try {
            final AppPermission appPermission = request.getPipeline() != null
                    ? AppPermission.VIEW_DATA_WITH_PIPELINE_PERMISSION
                    : AppPermission.VIEW_DATA_PERMISSION;

            return securityContext.secureResult(appPermission, () ->
                    dataFetcher.getData(request));
        } catch (final RuntimeException e) {
            LOGGER.debug(LogUtil.message("Error fetching data {}", request), e);
            throw e;
        }
    }

    @Override
    public Set<String> getChildStreamTypes(final long id, final long partNo) {
        try {
            final AppPermission permission = AppPermission.VIEW_DATA_PERMISSION;
            return securityContext.secureResult(permission, () -> {
                final Set<String> childTypes = dataFetcher.getAvailableChildStreamTypes(id, partNo);
                LOGGER.debug(() ->
                        LogUtil.message("childTypes {}",
                                childTypes.stream()
                                        .sorted()
                                        .collect(Collectors.joining(","))));
                return childTypes;
            });
        } catch (final Exception e) {
            LOGGER.error(LogUtil.message("Error fetching child stream types for id {}, part number {}",
                    id, partNo), e);
            throw e;
        }
    }

    private String convertDuration(final String value) {
        try {
            return ModelStringUtil.formatDurationString(Long.parseLong(value));
        } catch (final RuntimeException e) {
            // Ignore.
        }
        return value;
    }

    private String convertTime(final String value) {
        try {
            final long valLong = Long.parseLong(value);
            return DateUtil.createNormalDateTimeString(valLong) + " (" + valLong + ")";
        } catch (final RuntimeException e) {
            // Ignore.
        }
        return value;
    }

    private String convertSize(final String value) {
        try {
            final long valLong = Long.parseLong(value);
            final String iecByteSizeStr = ModelStringUtil.formatIECByteSizeString(valLong);
            if (valLong >= 1024) {
                return iecByteSizeStr + " (" + NumberFormat.getIntegerInstance().format(valLong) + ")";
            } else {
                return iecByteSizeStr;
            }
        } catch (final RuntimeException e) {
            // Ignore.
        }
        return value;
    }

    private String convertCount(final String value) {
        try {
            return ModelStringUtil.formatCsv(Long.parseLong(value));
        } catch (final RuntimeException e) {
            // Ignore.
        }
        return value;
    }


    private List<DataInfoSection.Entry> getStreamEntries(final Meta meta) {
        final List<DataInfoSection.Entry> entries = new ArrayList<>();

        entries.add(new DataInfoSection.Entry("Stream Id", String.valueOf(meta.getId())));
        entries.add(new DataInfoSection.Entry("Status", meta.getStatus().getDisplayValue()));
        entries.add(new DataInfoSection.Entry("Status Ms", getDateTimeString(meta.getStatusMs())));
        if (meta.getParentMetaId() != null) {
            entries.add(new DataInfoSection.Entry("Parent Stream Id", String.valueOf(meta.getParentMetaId())));
        }
        entries.add(new DataInfoSection.Entry("Created", getDateTimeString(meta.getCreateMs())));
        entries.add(new DataInfoSection.Entry("Effective", getDateTimeString(meta.getEffectiveMs())));
        entries.add(new DataInfoSection.Entry("Stream Type", meta.getTypeName()));
        entries.add(new DataInfoSection.Entry("Feed", meta.getFeedName()));
        addEncodingInfo(meta, entries);

        if (meta.getProcessorUuid() != null) {
            entries.add(new DataInfoSection.Entry("Processor", meta.getProcessorUuid()));
        }
        if (meta.getPipelineUuid() != null) {
            final String pipelineName = getPipelineName(meta);
            final String pipeline = DocRefUtil.createSimpleDocRefString(new DocRef(PipelineDoc.TYPE,
                    meta.getPipelineUuid(),
                    pipelineName));
            entries.add(new DataInfoSection.Entry("Processor Pipeline", pipeline));
        }
        if (meta.getProcessorFilterId() != null) {
            entries.add(new DataInfoSection.Entry("Processor Filter Id", String.valueOf(meta.getProcessorFilterId())));
        }
        if (meta.getProcessorTaskId() != null) {
            entries.add(new DataInfoSection.Entry("Processor Task Id", String.valueOf(meta.getProcessorTaskId())));
        }
        return entries;
    }

    private void addEncodingInfo(final Meta meta, final List<Entry> entries) {
        feedStore.findByName(meta.getFeedName())
                .stream()
                .findFirst()
                .map(feedStore::readDocument)
                .ifPresentOrElse(
                        feedDoc -> {
                            // If this is the received stream type then show the encoding
                            if (metaService.isRaw(meta.getTypeName())) {
                                NullSafe.consume(feedDoc.getEncoding(), encoding -> {
                                    entries.add(new DataInfoSection.Entry("Data Encoding", encoding));
                                });
                            } else {
                                entries.add(new DataInfoSection.Entry(
                                        "Data Encoding", StreamUtil.DEFAULT_CHARSET_NAME));
                            }
                        },
                        () -> LOGGER.error("Can't find feed doc with name " + meta.getFeedName()));
    }


    private String getDateTimeString(final Long ms) {
        if (ms == null) {
            return "";
        }
        return DateUtil.createNormalDateTimeString(ms) + " (" + ms + ")";
    }

    private List<DataInfoSection.Entry> getDataRententionEntries(final Map<String, String> attributeMap) {
        final List<DataInfoSection.Entry> entries = new ArrayList<>();

        if (attributeMap != null && !attributeMap.isEmpty()) {
            entries.add(new DataInfoSection.Entry(DataRetentionFields.RETENTION_AGE,
                    attributeMap.get(DataRetentionFields.RETENTION_AGE)));
            entries.add(new DataInfoSection.Entry(DataRetentionFields.RETENTION_UNTIL,
                    attributeMap.get(DataRetentionFields.RETENTION_UNTIL)));
            entries.add(new DataInfoSection.Entry(DataRetentionFields.RETENTION_RULE,
                    attributeMap.get(DataRetentionFields.RETENTION_RULE)));
        }

        return entries;
    }

    private String getPipelineName(final Meta meta) {
        if (meta.getPipelineUuid() != null) {
            return docRefInfoService
                    .name(new DocRef("Pipeline", meta.getPipelineUuid()))
                    .orElse(null);
        }
        return null;
    }
}
