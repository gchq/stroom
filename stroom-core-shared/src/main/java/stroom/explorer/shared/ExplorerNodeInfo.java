package stroom.explorer.shared;

import stroom.docref.DocRefInfo;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.UserName;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
public class ExplorerNodeInfo {

    @JsonProperty
    private final ExplorerNode explorerNode;
    @JsonProperty
    private final DocRefInfo docRefInfo;
    // This shouldn't be a Set, but we have to handle legacy docs with >1 owner
    @JsonProperty
    private final Set<UserName> owners;

    @JsonCreator
    public ExplorerNodeInfo(@JsonProperty("explorerNode") final ExplorerNode explorerNode,
                            @JsonProperty("docRefInfo") final DocRefInfo docRefInfo,
                            @JsonProperty("owners") final Set<UserName> owners) {
        this.explorerNode = Objects.requireNonNull(explorerNode);
        this.docRefInfo = Objects.requireNonNull(docRefInfo);
        this.owners = owners;
    }

    public ExplorerNode getExplorerNode() {
        return explorerNode;
    }

    public DocRefInfo getDocRefInfo() {
        return docRefInfo;
    }

    public Set<UserName> getOwners() {
        return GwtNullSafe.set(owners);
    }

    @Override
    public String toString() {
        return "ExplorerNodeInfo{" +
                "explorerNode=" + explorerNode +
                ", docRefInfo=" + docRefInfo +
                ", owners=" + owners +
                '}';
    }
}
