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

package stroom.dashboard.server;

import org.modelmapper.Converter;
import org.modelmapper.ModelMapper;
import org.modelmapper.PropertyMap;
import org.modelmapper.convention.MatchingStrategies;
import org.modelmapper.spi.MappingContext;
import org.modelmapper.spi.MatchingStrategy;
import stroom.query.api.Param;
import stroom.query.api.Query;
import stroom.query.api.SearchRequest;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

public class Mapper {
    private static Param[] convert(final Map<String, String> paramMap) {
        if (paramMap == null || paramMap.size() == 0) {
            return null;
        }

        final Param[] arr = new Param[paramMap.size()];
        int i = 0;
        for (final Entry<String, String> entry : paramMap.entrySet()) {
            arr[i++] = new Param(entry.getKey(), entry.getValue());
        }

        return arr;
    }

    public SearchRequest mapRequest(final stroom.dashboard.shared.SearchRequest in) {
//        Converter<Map<String, String>, Collection<Param>> mapCollectionConverter = new Converter<Map<String, String>, Collection<Param>>() {
//            @Override
//            public Collection<Param> convert(final MappingContext<Map<String, String>, Collection<Param>> context) {
//                return null;
//            }
//        };

        PropertyMap<stroom.dashboard.shared.Search, Query> searchMapping = new PropertyMap<stroom.dashboard.shared.Search, Query>() {
            @Override
            protected void configure() {
                map(source.getDataSourceRef(), destination.getDataSource());
                map(source.getExpression(), destination.getExpression());
                skip(source.getComponentSettingsMap());
//                map().setParams(convert(source.getParamMap()));

                skip(source.getParamMap());
                skip().setParams(null);

                skip(source.getIncremental());
            }
        };

        PropertyMap<stroom.dashboard.shared.SearchRequest, SearchRequest> searchRequestMapping = new PropertyMap<stroom.dashboard.shared.SearchRequest, SearchRequest>() {
            @Override
            protected void configure() {
                skip().setKey(null);
                map(source.getSearch(), destination.getQuery());
//                map(source.getComponentResultRequests(), destination.getResultRequests());

                skip(source.getComponentResultRequests());
                skip().setResultRequests(null);

                map().setDateTimeLocale(source.getDateTimeLocale());
                map().setIncremental(source.getSearch().getIncremental());
            }
        };

        final ModelMapper modelMapper = new ModelMapper();
//        modelMapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);
        modelMapper.addMappings(searchRequestMapping);
        modelMapper.addMappings(searchMapping);
        modelMapper.validate();

        SearchRequest out = modelMapper.map(in, SearchRequest.class);
        return out;
    }
}
