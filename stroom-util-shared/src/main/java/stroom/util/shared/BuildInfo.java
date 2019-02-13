package stroom.util.shared;

public class BuildInfo {
    private String upDate;
    private String buildDate = "TBD";
    private String buildVersion = "TBD";

    public BuildInfo() {
    }

    public BuildInfo(final String upDate, final String buildVersion, final String buildDate) {
        this.upDate = upDate;
        this.buildVersion = buildVersion;
        this.buildDate = buildDate;
    }

    public String getBuildVersion() {
        return buildVersion;
    }

    public void setBuildVersion(final String buildVersion) {
        this.buildVersion = buildVersion;
    }

    public String getBuildDate() {
        return buildDate;
    }

    public void setBuildDate(final String buildDate) {
        this.buildDate = buildDate;
    }

    public String getUpDate() {
        return upDate;
    }

    public void setUpDate(final String upDate) {
        this.upDate = upDate;
    }
}
