package stroom.analytics.shared;

import stroom.dashboard.shared.DownloadSearchResultFileType;
import stroom.util.shared.AbstractBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class ReportSettings {

    @JsonProperty
    private final DownloadSearchResultFileType fileType;

    @JsonCreator
    public ReportSettings(@JsonProperty("fileType") final DownloadSearchResultFileType fileType) {
        this.fileType = fileType;
    }

    public DownloadSearchResultFileType getFileType() {
        return fileType;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ReportSettings that = (ReportSettings) o;
        return fileType == that.fileType;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(fileType);
    }

    @Override
    public String toString() {
        return "ReportSettings{" +
               "fileType=" + fileType +
               '}';
    }


    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static class Builder extends AbstractBuilder<ReportSettings, Builder> {

        private DownloadSearchResultFileType fileType;

        public Builder() {
        }

        public Builder(final ReportSettings settings) {
            this.fileType = settings.fileType;
        }

        public Builder fileType(final DownloadSearchResultFileType fileType) {
            this.fileType = fileType;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public ReportSettings build() {
            return new ReportSettings(fileType);
        }
    }
}
