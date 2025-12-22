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

package stroom.query.impl;

import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.query.shared.QueryDoc;

import jakarta.inject.Inject;

import java.io.IOException;
import java.util.Map;

public class QuerySerialiser implements DocumentSerialiser2<QueryDoc> {

//    private static final Logger LOGGER = LoggerFactory.getLogger(QuerySerialiser.class);
//
//    private static final String JSON = "json";

    private final Serialiser2<QueryDoc> delegate;

    @Inject
    public QuerySerialiser(final Serialiser2Factory serialiser2Factory) {
        this.delegate = serialiser2Factory.createSerialiser(QueryDoc.class);
    }

    @Override
    public QueryDoc read(final Map<String, byte[]> data) throws IOException {
        final QueryDoc document = delegate.read(data);
//        final byte[] jsonData = data.get(JSON);
//        if (jsonData != null) {
//            try {
//                final QueryConfig dashboardConfig = getQueryConfigFromJson(jsonData);
//                document.setQueryConfig(dashboardConfig);
//            } catch (final RuntimeException e) {
//                LOGGER.error("Unable to unmarshal dashboard config", e);
//            }
//        }
        return document;
    }

    @Override
    public Map<String, byte[]> write(final QueryDoc document) throws IOException {
//        final QueryConfig dashboardConfig = document.getQueryConfig();
//        document.setQueryConfig(null);

        final Map<String, byte[]> data = delegate.write(document);

//        if (dashboardConfig != null) {
//            final StringWriter stringWriter = new StringWriter();
//            dashboardConfigSerialiser.write(stringWriter, dashboardConfig);
//            data.put(JSON, EncodingUtil.asBytes(stringWriter.toString()));
//            document.setQueryConfig(dashboardConfig);
//        }


        return data;
    }

//    public QueryConfig getQueryConfigFromJson(final byte[] jsonData) throws IOException {
//        return dashboardConfigSerialiser.read(jsonData);
//    }
}
