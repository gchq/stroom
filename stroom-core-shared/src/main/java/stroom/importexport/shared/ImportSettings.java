package stroom.importexport.shared;

import stroom.docref.DocRef;
import stroom.importexport.shared.ImportState.State;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class ImportSettings {

    @JsonProperty
    private final ImportMode importMode;
    @JsonProperty
    private final boolean enableFilters;
    @JsonProperty
    private final Long enableFiltersFromTime;
    @JsonProperty
    private final boolean useImportNames;
    @JsonProperty
    private final boolean useImportFolders;
    @JsonProperty
    private final DocRef rootDocRef;
    @JsonProperty
    private final boolean mockEnvironment;

    @JsonCreator
    public ImportSettings(@JsonProperty("importMode") final ImportMode importMode,
                          @JsonProperty("enableFilters") final boolean enableFilters,
                          @JsonProperty("enableFiltersFromTime") final Long enableFiltersFromTime,
                          @JsonProperty("useImportNames") final boolean useImportNames,
                          @JsonProperty("useImportFolders") final boolean useImportFolders,
                          @JsonProperty("rootDocRef") final DocRef rootDocRef,
                          @JsonProperty("mockEnvironment") final boolean mockEnvironment) {
        this.importMode = importMode;
        this.enableFilters = enableFilters;
        this.enableFiltersFromTime = enableFiltersFromTime;
        this.useImportNames = useImportNames;
        this.useImportFolders = useImportFolders;
        this.rootDocRef = rootDocRef;
        this.mockEnvironment = mockEnvironment;
    }

    public ImportMode getImportMode() {
        return importMode;
    }

    public boolean isEnableFilters() {
        return enableFilters;
    }

    public Long getEnableFiltersFromTime() {
        return enableFiltersFromTime;
    }

    public boolean isUseImportNames() {
        return useImportNames;
    }

    public boolean isUseImportFolders() {
        return useImportFolders;
    }

    public DocRef getRootDocRef() {
        return rootDocRef;
    }

    public boolean isMockEnvironment() {
        return mockEnvironment;
    }

    public static boolean ok(final ImportSettings importSettings,
                             final ImportState importState) {
        if (State.IGNORE.equals(importState.getState())) {
            return false;
        }

        return ImportMode.IGNORE_CONFIRMATION.equals(importSettings.getImportMode())
               || (ImportMode.ACTION_CONFIRMATION.equals(importSettings.getImportMode()) && importState.isAction());
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Auto import of content plus auto enable filters for demo and testing.
     *
     * @return
     */
    public static ImportSettings auto() {
        return builder().importMode(ImportMode.IGNORE_CONFIRMATION).enableFilters(true).build();
    }

    public static ImportSettings createConfirmation() {
        return builder().importMode(ImportMode.CREATE_CONFIRMATION).build();
    }

    public static ImportSettings actionConfirmation() {
        return builder().importMode(ImportMode.ACTION_CONFIRMATION).build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImportSettings that = (ImportSettings) o;
        return enableFilters == that.enableFilters &&
               useImportNames == that.useImportNames &&
               useImportFolders == that.useImportFolders &&
               importMode == that.importMode &&
               mockEnvironment == that.mockEnvironment &&
               Objects.equals(enableFiltersFromTime, that.enableFiltersFromTime) &&
               Objects.equals(rootDocRef, that.rootDocRef);
    }

    /**
     * toString to aid debugging import
     *
     * @return Meaningful string describing the object.
     */
    @Override
    public String toString() {
        return "ImportSettings{" +
               "importMode=" + importMode +
               ", enableFilters=" + enableFilters +
               ", enableFiltersFromTime=" + enableFiltersFromTime +
               ", useImportNames=" + useImportNames +
               ", useImportFolders=" + useImportFolders +
               ", rootDocRef=" + rootDocRef +
               ", isMockEnvironment=" + mockEnvironment +
               '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(importMode,
                enableFilters,
                enableFiltersFromTime,
                useImportNames,
                useImportFolders,
                rootDocRef,
                mockEnvironment);
    }


    // --------------------------------------------------------------------------------


    public enum ImportMode {
        CREATE_CONFIRMATION,
        ACTION_CONFIRMATION,
        IGNORE_CONFIRMATION
    }


    // --------------------------------------------------------------------------------


    public static class Builder {

        private ImportMode importMode;
        private boolean enableFilters;
        private Long enableFiltersFromTime;
        private boolean useImportNames;
        private boolean useImportFolders;
        private DocRef rootDocRef;
        private boolean mockEnvironment;

        public Builder importMode(final ImportMode importMode) {
            this.importMode = importMode;
            return this;
        }

        public Builder enableFilters(final boolean enableFilters) {
            this.enableFilters = enableFilters;
            return this;
        }

        public Builder enableFiltersFromTime(final Long enableFiltersFromTime) {
            this.enableFiltersFromTime = enableFiltersFromTime;
            return this;
        }

        public Builder useImportNames(final boolean useImportNames) {
            this.useImportNames = useImportNames;
            return this;
        }

        public Builder useImportFolders(final boolean useImportFolders) {
            this.useImportFolders = useImportFolders;
            return this;
        }

        public Builder rootDocRef(final DocRef rootDocRef) {
            this.rootDocRef = rootDocRef;
            return this;
        }

        /**
         * Used to flag to the Import/Export serializer that the environment is mocked for
         * test and thus the ExplorerTree is missing.
         */
        public Builder isMockEnvironment(final boolean mockEnvironment) {
            this.mockEnvironment = mockEnvironment;
            return this;
        }

        public ImportSettings build() {
            return new ImportSettings(
                    importMode,
                    enableFilters,
                    enableFiltersFromTime,
                    useImportNames,
                    useImportFolders,
                    rootDocRef,
                    mockEnvironment);
        }
    }
}
