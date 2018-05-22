package stroom.importexport;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import stroom.docref.DocRef;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.Map;

@XmlType(name = "DocumentData")
@XmlRootElement(name = "documentData")
@XmlAccessorType(XmlAccessType.FIELD)
@ApiModel(description = "Raw data representation of a document")
public class OldDocumentData implements Serializable {
    @XmlElement(name = "docRef")
    @ApiModelProperty(
            value = "The document reference for the document",
            required = true)
    private DocRef docRef;

    @XmlElement(name = "data")
    @ApiModelProperty(
            value = "A map of file extensions to file contents that are used to represent all of the document contents",
            required = true)
    private Map<String, String> dataMap;

    private OldDocumentData() {
    }

    public OldDocumentData(final DocRef docRef, final Map<String, String> dataMap) {
        this.docRef = docRef;
        this.dataMap = dataMap;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public Map<String, String> getDataMap() {
        return dataMap;
    }
}
