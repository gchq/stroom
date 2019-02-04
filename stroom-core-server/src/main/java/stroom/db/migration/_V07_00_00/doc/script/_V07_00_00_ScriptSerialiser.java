package stroom.db.migration._V07_00_00.doc.script;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.db.migration._V07_00_00.doc._V07_00_00_EncodingUtil;
import stroom.db.migration._V07_00_00.doc._V07_00_00_JsonSerialiser2;
import stroom.db.migration._V07_00_00.entity.shared._V07_00_00_DocRefs;
import stroom.db.migration._V07_00_00.entity.util._V07_00_00_XMLMarshallerUtil;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.util.Map;

public class _V07_00_00_ScriptSerialiser extends _V07_00_00_JsonSerialiser2<_V07_00_00_ScriptDoc> {
    private static final Logger LOGGER = LoggerFactory.getLogger(_V07_00_00_ScriptSerialiser.class);

    private static final String JS = "js";

    public _V07_00_00_ScriptSerialiser() {
        super(_V07_00_00_ScriptDoc.class);
    }

    @Override
    public _V07_00_00_ScriptDoc read(final Map<String, byte[]> data) throws IOException {
        final _V07_00_00_ScriptDoc document = super.read(data);

        final String js = _V07_00_00_EncodingUtil.asString(data.get(JS));
        if (js != null) {
            document.setData(js);
        }
        return document;
    }

    @Override
    public Map<String, byte[]> write(final _V07_00_00_ScriptDoc document) throws IOException {
        final Map<String, byte[]> data = super.write(document);

        if (document.getData() != null) {
            data.put(JS, _V07_00_00_EncodingUtil.asBytes(document.getData()));
        }

        return data;
    }

    public _V07_00_00_DocRefs getDocRefsFromLegacyXML(final String xml) {
        if (xml != null) {
            try {
                final JAXBContext jaxbContext = JAXBContext.newInstance(_V07_00_00_DocRefs.class);
                return _V07_00_00_XMLMarshallerUtil.unmarshal(jaxbContext, _V07_00_00_DocRefs.class, xml);
            } catch (final JAXBException | RuntimeException e) {
                LOGGER.error("Unable to unmarshal dashboard config", e);
            }
        }

        return null;
    }
}