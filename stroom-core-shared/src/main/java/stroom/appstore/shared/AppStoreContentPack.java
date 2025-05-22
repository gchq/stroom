package stroom.appstore.shared;

import stroom.docs.shared.Description;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Class represents a Content Pack within an App Store.
 */
@Description(
        "Contains the information for an App Store Content Pack"
)
@JsonPropertyOrder({
        "uiName",
        "iconUrl",
        "licenseName",
        "licenseUrl",
        "gitUrl",
        "gitBranch",
        "gitPath",
        "gitCommit"
})
@JsonInclude(Include.NON_NULL)
public class AppStoreContentPack {

    /** The name as displayed in the UI */
    @JsonProperty
    private final String uiName;

    /** URL of the icon to display */
    @JsonProperty
    private final String iconUrl;

    /** Display name of the license */
    @JsonProperty
    private final String licenseName;

    /** URL of the full license text */
    @JsonProperty
    private final String licenseUrl;

    /** Git remote repository URL */
    @JsonProperty
    private final String gitUrl;

    /** Git branch */
    @JsonProperty
    private final String gitBranch;

    /** Path inside Git repo */
    @JsonProperty
    private final String gitPath;

    /** Hash of the Git commit to pull */
    @JsonProperty
    private final String gitCommit;

    /** Default logo to use - big Stroom logo */
    private static final String DEFAULT_ICON_URL =
            "https://raw.githubusercontent.com/gchq/stroom/refs/heads/master/logo.png";

    /** Default Git path to use - the root */
    private static final String DEFAULT_GIT_PATH =
            "/";

    /**
     * Constructor. Initialises the values.
     * @param uiName Name as shown in the UI. Must not be null.
     * @param iconUrl Icon URL. If null then default icon will be used.
     * @param licenseName Name of license for UI. Can be null.
     * @param licenseUrl URL of full license info. Can be null.
     * @param gitUrl URL of remote Git repository. Must not be null.
     * @param gitBranch Name of Git branch. Must not be null.
     * @param gitPath Path to files we're interested in. Can be null in which
     *                case the root will be used.
     * @param gitCommit Hash of the Git commit to pull. Can be null in which
     *                  case the latest commit will be pulled.
     */
    @JsonCreator
    public AppStoreContentPack(@JsonProperty("uiName") final String uiName,
                               @JsonProperty("iconUrl") final String iconUrl,
                               @JsonProperty("licenseName") final String licenseName,
                               @JsonProperty("licenseUrl") final String licenseUrl,
                               @JsonProperty("gitUrl") final String gitUrl,
                               @JsonProperty("gitBranch") final String gitBranch,
                               @JsonProperty("gitPath") final String gitPath,
                               @JsonProperty("gitCommit") final String gitCommit) {

        // Implementation note:
        // Objects.requireNonNullElse() isn't available in GWT

        this.uiName = Objects.requireNonNull(uiName);
        this.iconUrl = iconUrl == null ? DEFAULT_ICON_URL : iconUrl;
        this.licenseName = licenseName == null ? "" : licenseName;
        this.licenseUrl = licenseUrl == null ? "" : licenseUrl;
        this.gitUrl = Objects.requireNonNull(gitUrl);
        this.gitBranch = Objects.requireNonNull(gitBranch);
        this.gitPath = gitPath == null ? DEFAULT_GIT_PATH : gitPath;
        this.gitCommit = gitCommit == null ? "" : gitCommit;
    }

    /**
     * @return the display name of the content pack.
     * Never returns null.
     */
    public String getUiName() {
        return uiName;
    }

    /**
     * @return the URL of the icon to display.
     * Never returns null but may return empty string.
     */
    public String getIconUrl() {
        return iconUrl;
    }

    /**
     * @return The name of the license to display.
     * Never returns null but may return empty string.
     */
    public String getLicenseName() {
        return licenseName;
    }

    /**
     * @return The URL of the full license text.
     * Never returns null but may return empty string.
     */
    public String getLicenseUrl() {
        return licenseUrl;
    }

    /**
     * @return The URL of the remote Git repository.
     * Never returns null.
     */
    public String getGitUrl() {
        return gitUrl;
    }

    /**
     * @return The name of the Git branch to pull.
     * Never returns null.
     */
    public String getGitBranch() {
        return gitBranch;
    }

    /**
     * @return The path within the Git repo to the stuff we want to import.
     * Never returns null.
     */
    public String getGitPath() {
        return gitPath;
    }

    /**
     * @return The commit hash to pull. Never returns null but may return
     * the empty string.
     */
    public String getGitCommit() {
        return gitCommit;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AppStoreContentPack that = (AppStoreContentPack) o;
        return Objects.equals(uiName, that.uiName) && Objects.equals(iconUrl,
                that.iconUrl) && Objects.equals(licenseName, that.licenseName) && Objects.equals(
                licenseUrl,
                that.licenseUrl) && Objects.equals(gitUrl, that.gitUrl) && Objects.equals(gitBranch,
                that.gitBranch) && Objects.equals(gitPath, that.gitPath) && Objects.equals(gitCommit,
                that.gitCommit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uiName, iconUrl, licenseName, licenseUrl, gitUrl, gitBranch, gitPath, gitCommit);
    }

    @Override
    public String toString() {
        return "AppStoreContentPack{" +
               "uiName='" + uiName + '\'' +
               ", iconUrl='" + iconUrl + '\'' +
               ", licenseName='" + licenseName + '\'' +
               ", licenseUrl='" + licenseUrl + '\'' +
               ", gitUrl='" + gitUrl + '\'' +
               ", gitBranch='" + gitBranch + '\'' +
               ", gitPath='" + gitPath + '\'' +
               ", gitCommit='" + gitCommit + '\'' +
               '}';
    }
}
