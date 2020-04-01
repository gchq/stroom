package stroom.index.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.index.impl.migration._V07_00_00.doc.index._V07_00_00_IndexField;
import stroom.index.impl.migration._V07_00_00.doc.index._V07_00_00_IndexFields;
import stroom.index.shared.AnalyzerType;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexFieldType;
import stroom.index.shared.IndexFields;
import stroom.util.xml.XMLMarshallerUtil;

import javax.inject.Inject;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class IndexSerialiser implements DocumentSerialiser2<IndexDoc> {
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexSerialiser.class);

//    private static final String JS = "js";

    private final Serialiser2<IndexDoc> delegate;

    @Inject
    public IndexSerialiser(final Serialiser2Factory serialiser2Factory) {
        delegate = serialiser2Factory.createSerialiser(IndexDoc.class);
    }

    @Override
    public IndexDoc read(final Map<String, byte[]> data) throws IOException {
        final IndexDoc document = delegate.read(data);

//        final String js = EncodingUtil.asString(data.get(JS));
//        if (js != null) {
//            document.setMeta(js);
//        }
        return document;
    }

    @Override
    public Map<String, byte[]> write(final IndexDoc document) throws IOException {
        return delegate.write(document);
    }

    public IndexFields getIndexFieldsFromLegacyXML(final String xml) {
        if (xml != null) {
            try {
                final JAXBContext jaxbContext = JAXBContext.newInstance(_V07_00_00_IndexFields.class);
                final _V07_00_00_IndexFields oldIndexFields = XMLMarshallerUtil.unmarshal(jaxbContext, _V07_00_00_IndexFields.class, xml);
                List<_V07_00_00_IndexField> oldIndexFieldList = oldIndexFields.getIndexFields();
                if (oldIndexFieldList == null) {
                    oldIndexFieldList = Collections.emptyList();
                }

                final List<IndexField> indexFieldList = oldIndexFieldList
                        .stream()
                        .map(field -> new IndexField(
                                IndexFieldType.valueOf(field.getFieldType().name()),
                                field.getFieldName(),
                                AnalyzerType.valueOf(field.getAnalyzerType().name()),
                                field.isIndexed(),
                                field.isStored(),
                                field.isTermPositions(),
                                field.isCaseSensitive()))
                        .collect(Collectors.toList());

                return new IndexFields(indexFieldList);

            } catch (final JAXBException | RuntimeException e) {
                LOGGER.error("Unable to unmarshal index config", e);
            }
        }

        return null;
    }
}