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
 */

package stroom.data.store.impl;

import stroom.data.shared.DataInfoSection;
import stroom.data.shared.DataInfoSection.Entry;
import stroom.data.shared.UploadDataRequest;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.docstore.shared.DocRefUtil;
import stroom.feed.api.FeedStore;
import stroom.meta.api.MetaService;
import stroom.meta.shared.DataRetentionFields;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaExpressionUtil;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.pipeline.shared.PipelineDoc;
import stroom.resource.api.ResourceStore;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.PermissionNames;
import stroom.util.date.DateUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class DataServiceImpl implements DataService {
    private final ResourceStore resourceStore;
    private final DataUploadTaskHandler dataUploadTaskHandlerProvider;
    private final DataDownloadTaskHandler dataDownloadTaskHandlerProvider;
    private final DocRefInfoService docRefInfoService;
    private final MetaService metaService;
    private final AttributeMapFactory attributeMapFactory;
    private final SecurityContext securityContext;
    private final FeedStore feedStore;

    @Inject
    DataServiceImpl(final ResourceStore resourceStore,
                    final DataUploadTaskHandler dataUploadTaskHandlerProvider,
                    final DataDownloadTaskHandler dataDownloadTaskHandlerProvider,
                    final DocRefInfoService docRefInfoService,
                    final MetaService metaService,
                    final AttributeMapFactory attributeMapFactory,
                    final SecurityContext securityContext,
                    final FeedStore feedStore) {
        this.resourceStore = resourceStore;
        this.dataUploadTaskHandlerProvider = dataUploadTaskHandlerProvider;
        this.dataDownloadTaskHandlerProvider = dataDownloadTaskHandlerProvider;
        this.docRefInfoService = docRefInfoService;
        this.metaService = metaService;
        this.attributeMapFactory = attributeMapFactory;
        this.securityContext = securityContext;
        this.feedStore = feedStore;
    }

    @Override
    public ResourceGeneration download(final FindMetaCriteria criteria) {
        return securityContext.secureResult(PermissionNames.EXPORT_DATA_PERMISSION, () -> {
            // Import file.
            final ResourceKey resourceKey = resourceStore.createTempFile("StroomData.zip");
            final Path file = resourceStore.getTempFile(resourceKey);
            String fileName = file.getFileName().toString();
            int index = fileName.lastIndexOf(".");
            if (index != -1) {
                fileName = fileName.substring(0, index);
            }

            final DataDownloadSettings settings = new DataDownloadSettings();
            final DataDownloadResult result = dataDownloadTaskHandlerProvider.downloadData(criteria, file.getParent(), fileName, settings);

            if (result.getRecordsWritten() == 0) {
                return null;
            }
            return new ResourceGeneration(resourceKey, new ArrayList<>());
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

        if (!securityContext.hasDocumentPermission(feedDocRef.getUuid(), DocumentPermissionNames.UPDATE)) {
            throw new PermissionException(securityContext.getUserId(),
                    "You do not have permission to update feed " + request.getFeedName());
        }

        return securityContext.secureResult(PermissionNames.IMPORT_DATA_PERMISSION, () -> {
            try {
                // Import file.
                final Path file = resourceStore.getTempFile(request.getKey());

                dataUploadTaskHandlerProvider.uploadData(
                        request.getFileName(),
                        file,
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
    public List<DataInfoSection> info(final long id) {
        final ResultPage<MetaRow> metaRows = metaService.findDecoratedRows(new FindMetaCriteria(MetaExpressionUtil.createDataIdExpression(id)));
        final MetaRow metaRow = metaRows.getFirst();

//        final Meta meta = metaService.getMeta(id, true);
        final List<DataInfoSection> sections = new ArrayList<>();
//
        if (metaRow == null) {
            final List<DataInfoSection.Entry> entries = new ArrayList<>(1);
            entries.add(new DataInfoSection.Entry("Deleted Stream Id", String.valueOf(id)));
            sections.add(new DataInfoSection("Stream", entries));

        } else {
            sections.add(new DataInfoSection("Stream", getStreamEntries(metaRow.getMeta())));

            final List<DataInfoSection.Entry> entries = new ArrayList<>();

            final Map<String, String> attributeMap = metaRow.getAttributes();
            final Map<String, String> additionalAttributes = attributeMapFactory.getAttributes(metaRow.getMeta());
            final String files = additionalAttributes.remove("Files");
            attributeMap.putAll(additionalAttributes);

            final List<String> sortedKeys = attributeMap
                    .keySet()
                    .stream()
                    .sorted()
                    .collect(Collectors.toList());
            sortedKeys.forEach(key -> {
                final String value = attributeMap.get(key);
                if (value != null) {
                    if (MetaFields.DURATION.getName().equals(key)) {
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

            sections.add(new DataInfoSection("Files", Collections.singletonList(new Entry("", files))));
        }

        return sections;
    }

    private String convertDuration(final String value) {
        try {
            return ModelStringUtil.formatDurationString(Long.parseLong(value));
        } catch (RuntimeException e) {
            // Ignore.
        }
        return value;
    }

    private String convertTime(final String value) {
        try {
            return DateUtil.createNormalDateTimeString(Long.parseLong(value));
        } catch (RuntimeException e) {
            // Ignore.
        }
        return value;
    }

    private String convertSize(final String value) {
        try {
            return ModelStringUtil.formatIECByteSizeString(Long.parseLong(value));
        } catch (RuntimeException e) {
            // Ignore.
        }
        return value;
    }

    private String convertCount(final String value) {
        try {
            return ModelStringUtil.formatCsv(Long.parseLong(value));
        } catch (RuntimeException e) {
            // Ignore.
        }
        return value;
    }


    private List<DataInfoSection.Entry> getStreamEntries(final Meta meta) {
        final List<DataInfoSection.Entry> entries = new ArrayList<>();

        entries.add(new DataInfoSection.Entry("Stream Id", String.valueOf(meta.getId())));
        entries.add(new DataInfoSection.Entry("Status", meta.getStatus().getDisplayValue()));
        entries.add(new DataInfoSection.Entry("Status Ms", getDateTimeString(meta.getStatusMs())));
        entries.add(new DataInfoSection.Entry("Parent Data Id", String.valueOf(meta.getParentMetaId())));
        entries.add(new DataInfoSection.Entry("Created", getDateTimeString(meta.getCreateMs())));
        entries.add(new DataInfoSection.Entry("Effective", getDateTimeString(meta.getEffectiveMs())));
        entries.add(new DataInfoSection.Entry("Stream Type", meta.getTypeName()));
        entries.add(new DataInfoSection.Entry("Feed", meta.getFeedName()));

        if (meta.getProcessorUuid() != null) {
            entries.add(new DataInfoSection.Entry("Processor", meta.getProcessorUuid()));
        }
        if (meta.getPipelineUuid() != null) {
            final String pipelineName = getPipelineName(meta);
            final String pipeline = DocRefUtil.createSimpleDocRefString(new DocRef(PipelineDoc.DOCUMENT_TYPE, meta.getPipelineUuid(), pipelineName));
            entries.add(new DataInfoSection.Entry("Processor Pipeline", pipeline));
        }
        return entries;
    }

    private String getDateTimeString(final long ms) {
        return DateUtil.createNormalDateTimeString(ms) + " (" + ms + ")";
    }

    private List<DataInfoSection.Entry> getDataRententionEntries(final Map<String, String> attributeMap) {
        final List<DataInfoSection.Entry> entries = new ArrayList<>();

        if (attributeMap != null && !attributeMap.isEmpty()) {
//            // Add additional data retention information.
//            final StreamAttributeMapRetentionRuleDecorator decorator = decoratorProvider.get();
//            decorator.addMatchingRetentionRuleInfo(meta, attributeMap);

            entries.add(new DataInfoSection.Entry(DataRetentionFields.RETENTION_AGE, attributeMap.get(DataRetentionFields.RETENTION_AGE)));
            entries.add(new DataInfoSection.Entry(DataRetentionFields.RETENTION_UNTIL, attributeMap.get(DataRetentionFields.RETENTION_UNTIL)));
            entries.add(new DataInfoSection.Entry(DataRetentionFields.RETENTION_RULE, attributeMap.get(DataRetentionFields.RETENTION_RULE)));
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