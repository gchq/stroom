/*
 * Copyright 2018 Crown Copyright
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

package stroom.pipeline.xsltfunctions;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.dictionary.DictionaryStore;
import stroom.feed.FeedService;
import stroom.properties.StroomPropertyService;
import stroom.pipeline.state.CurrentUserHolder;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.MetaDataHolder;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.state.SearchIdHolder;
import stroom.pipeline.state.StreamHolder;
import stroom.refdata.ReferenceData;
import stroom.util.spring.StroomScope;

@Configuration
public class XsltFunctionsSpringConfig {
    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public BitmapLookup bitmapLookup(final ReferenceData referenceData,
                                     final StreamHolder streamHolder) {
        return new BitmapLookup(referenceData, streamHolder);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public Classification classification(final FeedHolder feedHolder,
                                         final FeedService feedService) {
        return new Classification(feedHolder, feedService);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public CurrentTime currentTime() {
        return new CurrentTime();
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public CurrentUser currentUser(final CurrentUserHolder currentUserHolder) {
        return new CurrentUser(currentUserHolder);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public Dictionary dictionary(final DictionaryStore dictionaryStore) {
        return new Dictionary(dictionaryStore);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public FeedName feedName(final FeedHolder feedHolder) {
        return new FeedName(feedHolder);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public FetchJson fetchJson(final StroomPropertyService propertyService) {
        return new FetchJson(propertyService);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public FormatDate formatDate() {
        return new FormatDate();
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public GenerateURL generateURL() {
        return new GenerateURL();
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public Get get(final TaskScopeMap map) {
        return new Get(map);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public HexToDec hexToDec() {
        return new HexToDec();
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public HexToOct hexToOct() {
        return new HexToOct();
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public JsonToXml jsonToXml() {
        return new JsonToXml();
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public Log log() {
        return new Log();
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public Lookup lookup(final ReferenceData referenceData,
                         final StreamHolder streamHolder) {
        return new Lookup(referenceData, streamHolder);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public Meta meta(final MetaDataHolder metaDataHolder) {
        return new Meta(metaDataHolder);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public NumericIP numericIP() {
        return new NumericIP();
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public ParseUri parseUri() {
        return new ParseUri();
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public PipelineName pipelineName(final PipelineHolder pipelineHolder) {
        return new PipelineName(pipelineHolder);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public Put put(final TaskScopeMap map) {
        return new Put(map);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public Random random() {
        return new Random();
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public SearchId searchId(final SearchIdHolder searchIdHolder) {
        return new SearchId(searchIdHolder);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public StreamId streamId(final StreamHolder streamHolder) {
        return new StreamId(streamHolder);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public TaskScopeMap taskScopeMap() {
        return new TaskScopeMap();
    }
}