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

import stroom.security.Security;
import stroom.data.store.api.StreamStore;
import stroom.data.meta.api.Data;
import stroom.streamstore.shared.FetchFullStreamInfoAction;
import stroom.streamstore.shared.FullStreamInfoResult;
import stroom.streamstore.shared.FullStreamInfoResult.Entry;
import stroom.streamstore.shared.FullStreamInfoResult.Section;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;
import stroom.util.date.DateUtil;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@TaskHandlerBean(task = FetchFullStreamInfoAction.class)
class FetchFullStreamInfoHandler extends AbstractTaskHandler<FetchFullStreamInfoAction, FullStreamInfoResult> {
    private final StreamStore streamStore;
    private final StreamAttributeMapRetentionRuleDecorator ruleDecorator;
    private final Security security;

    @Inject
    FetchFullStreamInfoHandler(final StreamStore streamStore,
                               final StreamAttributeMapRetentionRuleDecorator ruleDecorator,
                               final Security security) {
        this.streamStore = streamStore;
        this.ruleDecorator = ruleDecorator;
        this.security = security;
    }

    private String getDateTimeString(final long ms) {
        return DateUtil.createNormalDateTimeString(ms) + " (" + ms + ")";
    }

    private List<Entry> getStreamEntries(final Data stream) {
        final List<Entry> entries = new ArrayList<>();

        entries.add(new Entry("Stream Id", String.valueOf(stream.getId())));
        entries.add(new Entry("Status", stream.getStatus().getDisplayValue()));
        entries.add(new Entry("Status Ms", getDateTimeString(stream.getStatusMs())));
        entries.add(new Entry("Stream Task Id", String.valueOf(stream.getProcessTaskId())));
        entries.add(new Entry("Parent Stream Id", String.valueOf(stream.getParentDataId())));
        entries.add(new Entry("Created", getDateTimeString(stream.getCreateMs())));
        entries.add(new Entry("Effective", getDateTimeString(stream.getEffectiveMs())));
        entries.add(new Entry("Stream Type", stream.getTypeName()));
        entries.add(new Entry("Feed", stream.getFeedName()));

        if (stream.getProcessorId() != null) {
            entries.add(new Entry("Stream Processor Id", String.valueOf(stream.getProcessorId())));
        }
        if (stream.getPipelineUuid() != null) {
            entries.add(new Entry("Stream Processor Pipeline", String.valueOf(stream.getPipelineUuid())));
        }
        return entries;
    }

    private List<Entry> getDataRententionEntries(final Data stream, final Map<String, String> attributeMap) {
        final List<Entry> entries = new ArrayList<>();

        // Add additional data retention information.
        ruleDecorator.addMatchingRetentionRuleInfo(stream, attributeMap);

        entries.add(new Entry(StreamAttributeMapRetentionRuleDecorator.RETENTION_AGE, attributeMap.get(StreamAttributeMapRetentionRuleDecorator.RETENTION_AGE)));
        entries.add(new Entry(StreamAttributeMapRetentionRuleDecorator.RETENTION_UNTIL, attributeMap.get(StreamAttributeMapRetentionRuleDecorator.RETENTION_UNTIL)));
        entries.add(new Entry(StreamAttributeMapRetentionRuleDecorator.RETENTION_RULE, attributeMap.get(StreamAttributeMapRetentionRuleDecorator.RETENTION_RULE)));

        return entries;
    }

    @Override
    public FullStreamInfoResult exec(final FetchFullStreamInfoAction action) {
        final Data stream = action.getStream();
        final List<Section> sections = new ArrayList<>();

        final Map<String, String> attributeMap = streamStore.getStoredMeta(stream);

        if (attributeMap == null) {
            final List<Entry> entries = new ArrayList<>(1);
            entries.add(new Entry("Deleted Stream Id", String.valueOf(stream.getId())));
            sections.add(new Section("Stream", entries));

        } else {
            sections.add(new Section("Stream", getStreamEntries(stream)));

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
            sections.add(new Section("Retention", getDataRententionEntries(stream, attributeMap)));


        }

        return new FullStreamInfoResult(sections);
    }
}
