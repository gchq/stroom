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

package stroom.logging;

import event.logging.Criteria;
import event.logging.Criteria.DataSources;
import event.logging.Event;
import event.logging.Export;
import event.logging.MultiObject;
import event.logging.Query;
import event.logging.Query.Advanced;
import event.logging.Search;
import event.logging.util.EventLoggingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.datasource.DataSourceProviderRegistry;
import stroom.dictionary.shared.DictionaryService;
import stroom.entity.server.QueryDataLogUtil;
import stroom.query.api.v1.DocRef;
import stroom.query.api.v1.ExpressionOperator;
import stroom.security.Insecure;

import javax.annotation.Resource;

@Component
@Insecure
public class SearchEventLogImpl implements SearchEventLog {
    private static final Logger LOGGER = LoggerFactory.getLogger(SearchEventLogImpl.class);

    @Resource
    private StroomEventLoggingService eventLoggingService;
    @Resource
    private DataSourceProviderRegistry dataSourceProviderRegistry;
    @Resource
    private DictionaryService dictionaryService;

    @Override
    public void search(final DocRef dataSourceRef, final ExpressionOperator expression) {
        search("Search", dataSourceRef, expression, null);
    }

    @Override
    public void search(final DocRef dataSourceRef, final ExpressionOperator expression, final Exception ex) {
        search("Search", dataSourceRef, expression, ex);
    }

    @Override
    public void batchSearch(final DocRef dataSourceRef, final ExpressionOperator expression) {
        search("Batch search", dataSourceRef, expression, null);
    }

    @Override
    public void batchSearch(final DocRef dataSourceRef, final ExpressionOperator expression,
                            final Exception ex) {
        search("Batch search", dataSourceRef, expression, ex);
    }

    @Override
    public void downloadResults(final DocRef dataSourceRef, final ExpressionOperator expression) {
        downloadResults("Batch search", dataSourceRef, expression, null);
    }

    @Override
    public void downloadResults(final DocRef dataSourceRef, final ExpressionOperator expression,
                                final Exception ex) {
        downloadResults("Download search results", dataSourceRef, expression, ex);
    }

    @Override
    public void downloadResults(final String type, final DocRef dataSourceRef,
                                final ExpressionOperator expression, final Exception ex) {
        try {
            final String dataSourceName = getDataSourceName(dataSourceRef);

            final DataSources dataSources = new DataSources();
            dataSources.getDataSource().add(dataSourceName);

            final Criteria criteria = new Criteria();
            criteria.setDataSources(dataSources);
            criteria.setQuery(getQuery(expression));

            final MultiObject multiObject = new MultiObject();
            multiObject.getObjects().add(criteria);

            final Export exp = new Export();
            exp.setSource(multiObject);
            exp.setOutcome(EventLoggingUtil.createOutcome(ex));

            final Event event = eventLoggingService.createAction(type, type + "ing data source \"" + dataSourceRef.toInfoString());

            event.getEventDetail().setExport(exp);

            eventLoggingService.log(event);
        } catch (final Exception e) {
            LOGGER.error("Unable to download results!", e);
        }
    }

    @Override
    public void search(final String type, final DocRef dataSourceRef, final ExpressionOperator expression,
                       final Exception ex) {
        try {
            String dataSourceName = getDataSourceName(dataSourceRef);
            if (dataSourceName == null || dataSourceName.isEmpty()) {
                dataSourceName = "NULL";
            }

            final DataSources dataSources = new DataSources();
            dataSources.getDataSource().add(dataSourceName);

            final Search search = new Search();
            search.setDataSources(dataSources);
            search.setQuery(getQuery(expression));
            search.setOutcome(EventLoggingUtil.createOutcome(ex));

            final Event event = eventLoggingService.createAction(type, type + "ing data source \"" + dataSourceRef.toInfoString());
            event.getEventDetail().setSearch(search);

            eventLoggingService.log(event);
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    public String getDataSourceName(final DocRef docRef) {
        if (docRef == null) {
            return null;
        }

//        final DataSource dataSource = dataSourceProviderRegistry.getDataSource(docRef);
//        if (dataSource == null) {
//            return null;
//        }

        return docRef.getName();
    }

    private Query getQuery(final ExpressionOperator expression) {
        final Query query = new Query();
        final Advanced advanced = new Advanced();
        query.setAdvanced(advanced);
        QueryDataLogUtil.appendExpressionItem(advanced.getAdvancedQueryItems(), dictionaryService, expression);
        return query;
    }
}
