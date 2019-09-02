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

package stroom.search.solr;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import stroom.search.solr.shared.SolrConnectionTestAction;
import stroom.task.api.AbstractTaskHandler;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.SharedString;

import java.io.IOException;

class SolrConnectionTestHandler extends AbstractTaskHandler<SolrConnectionTestAction, SharedString> {
    @Override
    public SharedString exec(final SolrConnectionTestAction action) {
        try {
            final SolrClient solrClient = new SolrClientFactory().create(action.getSolrIndexDoc().getSolrConnectionConfig());
//            final SolrPingResponse response = new SolrPing().process(solrClient, action.getSolrIndex().getCollection());
            final SolrPingResponse response = solrClient.ping();

            final StringBuilder sb = new StringBuilder();
            sb.append("Request URL: ");
            sb.append(response.getRequestUrl());
            sb.append("\nStatus: ");
            sb.append(response.getStatus());
            sb.append("\nElapsed Time: ");
            sb.append(ModelStringUtil.formatDurationString(response.getElapsedTime()));
            sb.append("\nQ Time: ");
            sb.append(ModelStringUtil.formatDurationString((long) response.getQTime()));

            sb.append("\nResponse Header: ");
            sb.append(response.getResponseHeader().toString());
            sb.append("\nResponse: ");
            sb.append(response.getResponse().toString());

            if (response.getException() != null) {
                sb.append("\nError: ");
                sb.append(response.getException().toString());
            }

            return SharedString.wrap(sb.toString());
        } catch (final IOException | SolrServerException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
