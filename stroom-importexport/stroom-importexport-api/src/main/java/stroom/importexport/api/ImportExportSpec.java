package stroom.importexport.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Generates a specification file for import/export.
 * The spec file allows us to tweak settings on import.
 */
@JsonInclude
@JsonPropertyOrder(alphabetic = true)
public class ImportExportSpec {

    /**
     * The version of the export.
     */
    public enum ImportExportVersion {
        /** Anything before spec files existed */
        VERSION_BEFORE_7_10,

        /** First version with spec files */
        VERSION_7_10
    }

    /**
     * The thing that invokes import or export.
     */
    public enum ImportExportCaller {
        /** Called from the old export mechanism */
        EXPORT,

        /** Called from GitRepos */
        GITREPO
    }

    @JsonProperty
    private final ImportExportVersion version;

    @JsonProperty
    private final ImportExportCaller creator;

    public ImportExportSpec(@JsonProperty("version") final ImportExportVersion version,
                            @JsonProperty("creator") final ImportExportCaller creator) {
        this.version = version == null ? ImportExportVersion.VERSION_7_10 : version;
        this.creator = creator == null ? ImportExportCaller.EXPORT : creator;
    }

    public ImportExportVersion getVersion() {
        return version;
    }

    public ImportExportCaller getCreator() {
        return creator;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImportExportSpec that = (ImportExportSpec) o;
        return version == that.version
               && Objects.equals(creator, that.creator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, creator);
    }

    /**
     * @return A spec object representing a back compatible spec,
     * for when no spec file exists in the data to be imported.
     */
    public static ImportExportSpec buildBackCompatible() {
        return new ImportExportSpec(ImportExportVersion.VERSION_BEFORE_7_10,
                ImportExportCaller.EXPORT);
    }

    /**
     * @return A spec object for use when exporting from a GitRepo
     * document.
     */
    public static ImportExportSpec buildGitRepoSpec() {
        return new ImportExportSpec(ImportExportVersion.VERSION_7_10,
                ImportExportCaller.GITREPO);
    }

    /**
     * @return A spec object for use when doing an export from a menu item.
     */
    public static ImportExportSpec buildExportSpec() {
        return new ImportExportSpec(ImportExportVersion.VERSION_7_10,
                ImportExportCaller.EXPORT);
    }

}
