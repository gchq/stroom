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

package stroom.data.store;

import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.meta.shared.Meta;
import stroom.security.Security;
import stroom.streamstore.shared.FetchFullMetaInfoAction;
import stroom.streamstore.shared.FullMetaInfoResult;
import stroom.streamstore.shared.FullMetaInfoResult.Entry;
import stroom.streamstore.shared.FullMetaInfoResult.Section;
import stroom.task.api.AbstractTaskHandler;
import stroom.util.date.DateUtil;

import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


class FetchFullMetaInfoHandler extends AbstractTaskHandler<FetchFullMetaInfoAction, FullMetaInfoResult> {
    private final Store streamStore;
    private final StreamAttributeMapRetentionRuleDecorator ruleDecorator;
    private final Security security;

    @Inject
    FetchFullMetaInfoHandler(final Store streamStore,
                             final StreamAttributeMapRetentionRuleDecorator ruleDecorator,
                             final Security security) {
        this.streamStore = streamStore;
        this.ruleDecorator = ruleDecorator;
        this.security = security;
    }

    private String getDateTimeString(final long ms) {
        return DateUtil.createNormalDateTimeString(ms) + " (" + ms + ")";
    }

    private List<Entry> getStreamEntries(final Meta meta) {
        final List<Entry> entries = new ArrayList<>();

        entries.add(new Entry("Stream Id", String.valueOf(meta.getId())));
        entries.add(new Entry("Status", meta.getStatus().getDisplayValue()));
        entries.add(new Entry("Status Ms", getDateTimeString(meta.getStatusMs())));
        entries.add(new Entry("Stream Task Id", String.valueOf(meta.getProcessTaskId())));
        entries.add(new Entry("Parent Data Id", String.valueOf(meta.getParentMetaId())));
        entries.add(new Entry("Created", getDateTimeString(meta.getCreateMs())));
        entries.add(new Entry("Effective", getDateTimeString(meta.getEffectiveMs())));
        entries.add(new Entry("Stream Type", meta.getTypeName()));
        entries.add(new Entry("Feed", meta.getFeedName()));

        if (meta.getProcessorId() != null) {
            entries.add(new Entry("Processor Id", String.valueOf(meta.getProcessorId())));
        }
        if (meta.getPipelineUuid() != null) {
            entries.add(new Entry("Processor Pipeline", meta.getPipelineUuid()));
        }
        return entries;
    }

    private List<Entry> getDataRententionEntries(final Meta meta, final Map<String, String> attributeMap) {
        final List<Entry> entries = new ArrayList<>();

        // Add additional data retention information.
        ruleDecorator.addMatchingRetentionRuleInfo(meta, attributeMap);

        entries.add(new Entry(StreamAttributeMapRetentionRuleDecorator.RETENTION_AGE, attributeMap.get(StreamAttributeMapRetentionRuleDecorator.RETENTION_AGE)));
        entries.add(new Entry(StreamAttributeMapRetentionRuleDecorator.RETENTION_UNTIL, attributeMap.get(StreamAttributeMapRetentionRuleDecorator.RETENTION_UNTIL)));
        entries.add(new Entry(StreamAttributeMapRetentionRuleDecorator.RETENTION_RULE, attributeMap.get(StreamAttributeMapRetentionRuleDecorator.RETENTION_RULE)));

        return entries;
    }

    @Override
    public FullMetaInfoResult exec(final FetchFullMetaInfoAction action) {
        final Meta meta = action.getMeta();
        final List<Section> sections = new ArrayList<>();

        try (final Source source = streamStore.openStreamSource(meta.getId())) {
            final Map<String, String> attributeMap = source.getAttributes();

            if (attributeMap == null) {
                final List<Entry> entries = new ArrayList<>(1);
                entries.add(new Entry("Deleted Stream Id", String.valueOf(meta.getId())));
                sections.add(new Section("Stream", entries));

            } else {
                sections.add(new Section("Stream", getStreamEntries(meta)));

                final List<Entry> entries = new ArrayList<>();
                final List<String> sortedKeys = attributeMap.keySet().stream().sorted(Comparator.naturalOrder()).collect(Collectors.toList());
                sortedKeys.forEach(key -> entries.add(new Entry(key, attributeMap.get(key))));
                sections.add(new Section("Attributes", entries));


//            try {
//                final List<String> keys = new ArrayList<>(row.getAttributeKeySet());
//
//                Collections.sort(keys);
//
//                for (final String key : keys) {
//                    if (!key.equals(StreamAttributeConstants.RETENTION_AGE) &&
//                            !key.equals(StreamAttributeConstants.RETENTION_UNTIL) &&
//                            !key.equals(StreamAttributeConstants.RETENTION_RULE)) {
//                        TooltipUtil.addRowData(html, key, row.formatAttribute(key));
//                    }
//                }
//            } catch (final RuntimeException e) {
//                html.append(e.getMessage());
//            }
//
//            if (row.getFileNameList() != null) {
//                TooltipUtil.addBreak(html);
//                TooltipUtil.addHeading(html, "Files");
//                for (final String file : row.getFileNameList()) {
//                    TooltipUtil.addRowData(html, file);
//                }
//            }


                // Add additional data retention information.
                sections.add(new Section("Retention", getDataRententionEntries(meta, attributeMap)));


            }
        } catch (final IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        return new FullMetaInfoResult(sections);
    }
}
