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

package stroom.meta.impl;

import com.codahale.metrics.health.HealthCheck.Result;
import stroom.meta.api.AttributeMapFactory;
import stroom.meta.api.MetaService;
import stroom.meta.shared.DataRetentionFields;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaInfoSection;
import stroom.meta.shared.MetaInfoSection.Entry;
import stroom.meta.shared.MetaResource;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.UpdateStatusRequest;
import stroom.security.api.SecurityContext;
import stroom.util.HasHealthCheck;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

class MetaResourceImpl implements MetaResource, RestResource, HasHealthCheck {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MetaResourceImpl.class);

    private final MetaService metaService;
    private final Optional<AttributeMapFactory> attributeMapFactory;
    private final StreamAttributeMapRetentionRuleDecorator ruleDecorator;
    private final SecurityContext securityContext;

    @Inject
    MetaResourceImpl(final MetaService metaService,
                     final Optional<AttributeMapFactory> attributeMapFactory,
                     final StreamAttributeMapRetentionRuleDecorator ruleDecorator,
                     final SecurityContext securityContext) {
        this.metaService = metaService;
        this.attributeMapFactory = attributeMapFactory;
        this.ruleDecorator = ruleDecorator;
        this.securityContext = securityContext;
    }

    @Override
    public Integer updateStatus(final UpdateStatusRequest request) {
        return securityContext.secureResult(() -> metaService.updateStatus(request.getCriteria(), request.getNewStatus()));
    }

    @Override
    public ResultPage<MetaRow> findMetaRow(final FindMetaCriteria criteria) {
        final ResultPage<MetaRow> list = metaService.findRows(criteria);
        list.getValues().forEach(metaRow -> ruleDecorator.addMatchingRetentionRuleInfo(metaRow.getMeta(), metaRow.getAttributes()));

        return list;
    }

    @Override
    public List<MetaInfoSection> fetchFullMetaInfo(final long id) {
        final Meta meta = metaService.getMeta(id, true);
        final List<MetaInfoSection> sections = new ArrayList<>();

        final Map<String, String> attributeMap = attributeMapFactory.map(amf -> amf.getAttributes(meta)).orElse(null);
        if (attributeMap == null) {
            final List<Entry> entries = new ArrayList<>(1);
            entries.add(new Entry("Deleted Stream Id", String.valueOf(meta.getId())));
            sections.add(new MetaInfoSection("Stream", entries));

        } else {
            sections.add(new MetaInfoSection("Stream", getStreamEntries(meta)));

            final List<Entry> entries = new ArrayList<>();
            final List<String> sortedKeys = attributeMap.keySet().stream().sorted(Comparator.naturalOrder()).collect(Collectors.toList());
            sortedKeys.forEach(key -> entries.add(new Entry(key, attributeMap.get(key))));
            sections.add(new MetaInfoSection("Attributes", entries));

            // Add additional data retention information.
            sections.add(new MetaInfoSection("Retention", getDataRententionEntries(meta, attributeMap)));
        }

        return sections;
    }

    private List<Entry> getStreamEntries(final Meta meta) {
        final List<Entry> entries = new ArrayList<>();

        entries.add(new Entry("Stream Id", String.valueOf(meta.getId())));
        entries.add(new Entry("Status", meta.getStatus().getDisplayValue()));
        entries.add(new Entry("Status Ms", getDateTimeString(meta.getStatusMs())));
        entries.add(new Entry("Parent Data Id", String.valueOf(meta.getParentMetaId())));
        entries.add(new Entry("Created", getDateTimeString(meta.getCreateMs())));
        entries.add(new Entry("Effective", getDateTimeString(meta.getEffectiveMs())));
        entries.add(new Entry("Stream Type", meta.getTypeName()));
        entries.add(new Entry("Feed", meta.getFeedName()));

        if (meta.getProcessorUuid() != null) {
            entries.add(new Entry("Processor", meta.getProcessorUuid()));
        }
        if (meta.getPipelineUuid() != null) {
            entries.add(new Entry("Processor Pipeline", meta.getPipelineUuid()));
        }
        return entries;
    }

    private String getDateTimeString(final long ms) {
        return DateUtil.createNormalDateTimeString(ms) + " (" + ms + ")";
    }

    private List<Entry> getDataRententionEntries(final Meta meta, final Map<String, String> attributeMap) {
        final List<Entry> entries = new ArrayList<>();

        // Add additional data retention information.
        ruleDecorator.addMatchingRetentionRuleInfo(meta, attributeMap);

        entries.add(new Entry(DataRetentionFields.RETENTION_AGE, attributeMap.get(DataRetentionFields.RETENTION_AGE)));
        entries.add(new Entry(DataRetentionFields.RETENTION_UNTIL, attributeMap.get(DataRetentionFields.RETENTION_UNTIL)));
        entries.add(new Entry(DataRetentionFields.RETENTION_RULE, attributeMap.get(DataRetentionFields.RETENTION_RULE)));

        return entries;
    }

    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}