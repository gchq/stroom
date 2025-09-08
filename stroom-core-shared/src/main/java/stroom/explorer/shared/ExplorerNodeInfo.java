package stroom.explorer.shared;

import stroom.docref.DocRefInfo;
import stroom.util.shared.SerialisationTestConstructor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class ExplorerNodeInfo {

    @JsonProperty
    private final ExplorerNode explorerNode;
    @JsonProperty
    private final DocRefInfo docRefInfo;

    @JsonCreator
    public ExplorerNodeInfo(@JsonProperty("explorerNode") final ExplorerNode explorerNode,
                            @JsonProperty("docRefInfo") final DocRefInfo docRefInfo) {
        this.explorerNode = Objects.requireNonNull(explorerNode);
        this.docRefInfo = Objects.requireNonNull(docRefInfo);
    }

    @SerialisationTestConstructor
    private ExplorerNodeInfo() {
        this.explorerNode = ExplorerNode.builder().build();
        this.docRefInfo = DocRefInfo.builder().build();
    }

    public ExplorerNode getExplorerNode() {
        return explorerNode;
    }

    public DocRefInfo getDocRefInfo() {
        return docRefInfo;
    }

    @Override
    public String toString() {
        return "ExplorerNodeInfo{" +
                "explorerNode=" + explorerNode +
                ", docRefInfo=" + docRefInfo +
                '}';
    }
}
