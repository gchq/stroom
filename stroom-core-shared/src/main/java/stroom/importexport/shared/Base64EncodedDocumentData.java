package stroom.importexport.shared;

import stroom.docref.DocRef;

import java.io.Serializable;
import java.util.Map;

public class Base64EncodedDocumentData implements Serializable {
    private DocRef docRef;
    private Map<String, String> dataMap;

    public Base64EncodedDocumentData() {
    }

    public Base64EncodedDocumentData(final DocRef docRef, final Map<String, String> dataMap) {
        this.docRef = docRef;
        this.dataMap = dataMap;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public void setDocRef(final DocRef docRef) {
        this.docRef = docRef;
    }

    public Map<String, String> getDataMap() {
        return dataMap;
    }

    public void setDataMap(final Map<String, String> dataMap) {
        this.dataMap = dataMap;
    }
}
