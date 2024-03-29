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

package stroom.processor.impl.db;

import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.QueryData;

import jakarta.inject.Singleton;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;

@Singleton
public class ProcessorFilterMarshaller extends AbstractEntityMarshaller<ProcessorFilter, QueryData> {

    public ProcessorFilterMarshaller() throws JAXBException {
        super(getJaxbContext());
    }

    private static JAXBContext getJaxbContext() throws JAXBException {

        try {
            return JAXBContext.newInstance(QueryData.class);
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public QueryData getObject(final ProcessorFilter entity) {
        return entity.getQueryData();
    }

    @Override
    public void setObject(final ProcessorFilter entity, final QueryData object) {
        entity.setQueryData(object);
    }

    @Override
    protected String getData(final ProcessorFilter entity) {
        return entity.getData();
    }

    @Override
    protected void setData(final ProcessorFilter entity, final String data) {
        entity.setData(data);
    }

    @Override
    protected Class<QueryData> getObjectType() {
        return QueryData.class;
    }

    @Override
    public String getEntityType() {
        return ProcessorFilter.ENTITY_TYPE;
    }
}
