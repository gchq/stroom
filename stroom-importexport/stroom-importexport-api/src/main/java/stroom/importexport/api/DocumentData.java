package stroom.importexport.api;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import stroom.docref.DocRef;
import stroom.importexport.shared.Base64EncodedDocumentData;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.Base64;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@XmlType(name = "DocumentData")
@XmlRootElement(name = "documentData")
@XmlAccessorType(XmlAccessType.FIELD)
@ApiModel(description = "Raw data representation of a document")
public class DocumentData implements Serializable {
    @XmlElement(name = "docRef")
    @ApiModelProperty(
            value = "The document reference for the document",
            required = true)
    private DocRef docRef;

    @XmlElement(name = "data")
    @ApiModelProperty(
            value = "A map of file extensions to file contents that are used to represent all of the document contents",
            required = true)
    private Map<String, byte[]> dataMap;

    public DocumentData() {
    }

    public DocumentData(final DocRef docRef, final Map<String, byte[]> dataMap) {
        this.docRef = docRef;
        this.dataMap = dataMap;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public void setDocRef(final DocRef docRef) {
        this.docRef = docRef;
    }

    public Map<String, byte[]> getDataMap() {
        return dataMap;
    }

    public void setDataMap(final Map<String, byte[]> dataMap) {
        this.dataMap = dataMap;
    }

    public static Base64EncodedDocumentData toBase64EncodedDocumentData(final DocumentData documentData) {
        final Map<String, String> encodedData = documentData.getDataMap().entrySet()
                .stream()
                .collect(Collectors.toMap(Entry::getKey, e -> Base64.getEncoder().encodeToString(e.getValue())));
        return new Base64EncodedDocumentData(documentData.getDocRef(), encodedData);
    }

    public static DocumentData fromBase64EncodedDocumentData(Base64EncodedDocumentData base64EncodedDocumentData) {
        final Map<String, byte[]> decodedData = base64EncodedDocumentData.getDataMap().entrySet()
                .stream()
                .collect(Collectors.toMap(Entry::getKey, e -> Base64.getDecoder().decode(e.getValue())));
        return new DocumentData(base64EncodedDocumentData.getDocRef(), decodedData);
    }
}
