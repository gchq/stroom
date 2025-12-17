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

package stroom.dashboard.impl;

import stroom.query.api.SearchRequest;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks serialisation by converting to and from the SearchRequest objects and comparing the results
 */

class TestSearchRequestSerialisation {

    @Test
    void testJsonSearchRequestSerialisation() {
        // Given
        final SearchRequest searchRequest = SearchRequestTestData.apiSearchRequest();

        // When
        final String serialisedSearchRequest = JsonUtil.writeValueAsString(searchRequest);
        final SearchRequest deserialisedSearchRequest = JsonUtil.readValue(
                serialisedSearchRequest, SearchRequest.class);
        final String reSerialisedSearchRequest = JsonUtil.writeValueAsString(deserialisedSearchRequest);

        // Then
        assertThat(searchRequest).isEqualTo(deserialisedSearchRequest);
        assertThat(serialisedSearchRequest).isEqualTo(reSerialisedSearchRequest);
    }

//    @Test
//    void testXmlSearchRequestSerialisation() throws JAXBException {
//        // Given
//        SearchRequest searchRequest = SearchRequestTestData.apiSearchRequest();
//        final JAXBContext context = JAXBContext.newInstance(SearchRequest.class);
//        Marshaller marshaller = context.createMarshaller();
//        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
//        Unmarshaller unmarshaller = context.createUnmarshaller();
//        ByteArrayOutputStream firstDeserialisation = new ByteArrayOutputStream();
//        ByteArrayOutputStream secondDeserialisation = new ByteArrayOutputStream();
//
//        // When
//        marshaller.marshal(searchRequest, firstDeserialisation);
//        String serialisedSearchRequest = firstDeserialisation.toString();
//        SearchRequest deserialisedSearchRequest = (SearchRequest) unmarshaller.unmarshal(
//                new ByteArrayInputStream(serialisedSearchRequest.getBytes()));
//        marshaller.marshal(deserialisedSearchRequest, secondDeserialisation);
//        String reSerialisedSearchRequest = secondDeserialisation.toString();
//
//        // Then
//        assertThat(searchRequest).isEqualTo(deserialisedSearchRequest);
//        assertThat(serialisedSearchRequest).isEqualTo(reSerialisedSearchRequest);
//    }

}
