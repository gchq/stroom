package stroom.query.shared;

import stroom.dashboard.shared.FunctionSignature;
import stroom.dashboard.shared.StructureElement;
import stroom.datasource.api.v2.AbstractField;
import stroom.docref.DocRef;
import stroom.util.shared.GwtNullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class QueryHelpItemsResult {

    @JsonProperty
    private final List<DocRef> dataSources;
    @JsonProperty
    private final List<StructureElement> structureElements;
    @JsonProperty
    private final List<FunctionSignature> functionSignatures;
    @JsonProperty
    private final List<AbstractField> dataSourceFields;

    @JsonCreator
    public QueryHelpItemsResult(@JsonProperty("dataSources") final List<DocRef> dataSources,
                                @JsonProperty("structureElements") final List<StructureElement> structureElements,
                                @JsonProperty("functionSignatures") final List<FunctionSignature> functionSignatures,
                                @JsonProperty("dataSourceFields") final List<AbstractField> dataSourceFields) {
        this.dataSources = dataSources;
        this.structureElements = structureElements;
        this.functionSignatures = functionSignatures;
        this.dataSourceFields = dataSourceFields;
    }

    public List<DocRef> getDataSources() {
        return dataSources;
    }

    public List<StructureElement> getStructureElements() {
        return structureElements;
    }

    public List<FunctionSignature> getFunctionSignatures() {
        return functionSignatures;
    }

    public List<AbstractField> getDataSourceFields() {
        return dataSourceFields;
    }

    @Override
    public String toString() {
        return "QueryHelpItemsResult{" +
                "dataSources=" + GwtNullSafe.size(dataSources) +
                ", structureElements=" + GwtNullSafe.size(structureElements) +
                ", functionSignatures=" + GwtNullSafe.size(functionSignatures) +
                ", dataSourceFields=" + GwtNullSafe.size(dataSourceFields) +
                '}';
    }
}
