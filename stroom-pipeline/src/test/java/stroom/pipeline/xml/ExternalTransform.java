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

package stroom.pipeline.xml;

import stroom.feed.shared.FeedDoc;
import stroom.pipeline.shared.PipelineDoc;

import java.util.List;

/**
 * <p>
 * Holder class for the test data.
 * </p>
 */
public class ExternalTransform {
    private FeedDoc eventFeed;
    private List<PipelineDoc> otherTranslationList;
    private List<DataProcess> dataProcessList;
    private String basePath;

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(final String outputBasePath) {
        this.basePath = outputBasePath;
    }

    public FeedDoc getEventFeed() {
        return eventFeed;
    }

    public void setEventFeed(final FeedDoc eventFeed) {
        this.eventFeed = eventFeed;
    }

    public List<DataProcess> getDataProcessList() {
        return dataProcessList;
    }

    public void setDataProcessList(final List<DataProcess> dataProcessList) {
        this.dataProcessList = dataProcessList;
    }

    public List<PipelineDoc> getOtherTranslationList() {
        return otherTranslationList;
    }

    public void setOtherTranslationList(final List<PipelineDoc> otherTranslationList) {
        this.otherTranslationList = otherTranslationList;
    }

}
