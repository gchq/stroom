package stroom.appstore.shared;

import stroom.docs.shared.Description;
import stroom.gitrepo.shared.GitRepoDoc;

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
        "stroomName",
        "uiName",
        "iconUrl",
        "iconSvg",
        "licenseName",
        "licenseUrl",
        "gitUrl",
        "gitBranch",
        "gitPath",
        "gitCommit"
})
@JsonInclude(Include.NON_NULL)
public class AppStoreContentPack {

    /** The name as used by the App Store / Content Store */
    @JsonProperty
    private final String stroomName;

    /** The name as displayed in the UI */
    @JsonProperty
    private final String uiName;

    /** URL of the icon to display */
    @JsonProperty
    private final String iconUrl;

    /** SVG content of the icon. Not final as resolved outside this class. */
    @JsonProperty
    private String iconSvg;

    /** Display name of the license */
    @JsonProperty
    private final String licenseName;

    /** URL of the full license text */
    @JsonProperty
    private final String licenseUrl;

    /** Where the pack will be installed within Stroom */
    @JsonProperty
    private final String stroomPath;

    /** Extra Markdown information about this pack */
    @JsonProperty
    private final String details;

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

    /** Name of the Content Store - resolved later */
    @JsonProperty
    private String contentStoreUiName;

    /** Default Git path to use - the root */
    private static final String DEFAULT_GIT_PATH =
            "/";

    /** Length to truncate details field to in toString() */
    private static final int DETAILS_TRUNC = 25;

    /**
     * Constructor. Initialises the values from the YAML.
     * @param stroomName Name as used in the AppStore. Must not be null.
     * @param uiName Name as shown in the UI. Must not be null.
     * @param iconUrl Icon URL. Can be null in which case null will
     *                be returned.
     * @param iconSvg SVG content of the icon. Can be null in which
     *                case null will be stored for later resolution.
     * @param licenseName Name of license for UI. Can be null.
     * @param licenseUrl URL of full license info. Can be null.
     * @param stroomPath Where the pack will be installed in Stroom.
     *                   Can be null in which case it will be installed in
     *                   the root of the Explorer Tree.
     * @param gitUrl URL of remote Git repository. Must not be null.
     * @param gitBranch Name of Git branch. Must not be null.
     * @param gitPath Path to files we're interested in. Can be null in which
     *                case the root will be used.
     * @param gitCommit Hash of the Git commit to pull. Can be null in which
     *                  case the latest commit will be pulled.
     */
    @JsonCreator
    public AppStoreContentPack(@JsonProperty("stroomName") final String stroomName,
                               @JsonProperty("uiName") final String uiName,
                               @JsonProperty("iconUrl") final String iconUrl,
                               @JsonProperty("iconSvg") final String iconSvg,
                               @JsonProperty("licenseName") final String licenseName,
                               @JsonProperty("licenseUrl") final String licenseUrl,
                               @JsonProperty("stroomPath") final String stroomPath,
                               @JsonProperty("details") final String details,
                               @JsonProperty("gitUrl") final String gitUrl,
                               @JsonProperty("gitBranch") final String gitBranch,
                               @JsonProperty("gitPath") final String gitPath,
                               @JsonProperty("gitCommit") final String gitCommit) {

        // Implementation note:
        // Objects.requireNonNullElse() isn't available in GWT

        this.stroomName = Objects.requireNonNull(stroomName);
        this.uiName = Objects.requireNonNull(uiName);
        this.iconUrl = iconUrl;
        this.iconSvg = iconSvg;
        this.licenseName = licenseName == null ? "" : licenseName;
        this.licenseUrl = licenseUrl == null ? "" : licenseUrl;
        this.stroomPath = stroomPath == null || stroomPath.isEmpty() ? "/" : stroomPath;
        this.details = details == null ? "": details;
        this.gitUrl = Objects.requireNonNull(gitUrl);
        this.gitBranch = Objects.requireNonNull(gitBranch);
        this.gitPath = gitPath == null ? DEFAULT_GIT_PATH : gitPath;
        this.gitCommit = gitCommit == null ? "" : gitCommit;
    }

    /**
     * @return the Stroom AppStore name for this content pack.
     */
    public String getStroomName() {
        return stroomName;
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
     * Can return null.
     */
    public String getIconUrl() {
        return iconUrl;
    }

    /**
     * Sets the icon SVG content. Used so that something else
     * can resolve the icon if it hasn't been set yet.
     * @param iconSvg The SVG content for the icon.
     */
    public void setIconSvg(String iconSvg) {
        this.iconSvg = iconSvg;
    }

    /**
     * @return null, or the SVG representing the icon.
     */
    public String getIconSvg() {
        return iconSvg;
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
     * @return The path within stroom where the content pack should be
     * installed. Never returns null or empty string.
     */
    public String getStroomPath() {
        return stroomPath;
    }

    /**
     * @return Returns the description of the content pack.
     * Return string is in Markdown format.
     */
    public String getDetails() {
        return details;
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

    /**
     * Sets the name of the content store that this belongs to.
     * Resolved later. This structure may change.
     */
    public void setContentStoreUiName(String uiName) {
        this.contentStoreUiName = uiName;
    }

    /**
     * @return The name of the content store this belongs to.
     */
    public String getContentStoreUiName() {
        return contentStoreUiName;
    }

    /**
     * Returns whether this Content Pack matches the given GitRepoDoc.
     * Note that this ignores the name of the pack. Instead it focuses
     * on the Git content, as the content has UUIDs and shouldn't be
     * downloaded to multiple places within Stroom as confusion would result.
     * @param gitRepoDoc The existing Stroom GitRepoDoc to check.
     * @return true if there is a match; false otherwise.
     */
    public boolean matches(final GitRepoDoc gitRepoDoc) {
        return Objects.equals(gitUrl, gitRepoDoc.getUrl())
                && Objects.equals(gitPath, gitRepoDoc.getPath());
    }

    /**
     * Copies the settings in this Content Pack into the GitRepoDoc.
     * @param gitRepoDoc The doc to copy settings into.
     */
    public void updateSettingsIn(final GitRepoDoc gitRepoDoc) {
        gitRepoDoc.setUrl(gitUrl);
        gitRepoDoc.setBranch(gitBranch);
        gitRepoDoc.setPath(gitPath);
        gitRepoDoc.setCommit(gitCommit);
        gitRepoDoc.setDescription(details);
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AppStoreContentPack that = (AppStoreContentPack) o;
        return Objects.equals(stroomName, that.stroomName)
               && Objects.equals(uiName, that.uiName)
               && Objects.equals(iconUrl, that.iconUrl)
               && Objects.equals(iconSvg, that.iconSvg)
               && Objects.equals(licenseName, that.licenseName)
               && Objects.equals(licenseUrl, that.licenseUrl)
               && Objects.equals(stroomPath, that.stroomPath)
               && Objects.equals(details, that.details)
               && Objects.equals(gitUrl, that.gitUrl)
               && Objects.equals(gitBranch, that.gitBranch)
               && Objects.equals(gitPath, that.gitPath)
               && Objects.equals(gitCommit, that.gitCommit)
               && Objects.equals(contentStoreUiName, that.contentStoreUiName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stroomName,
                uiName,
                iconUrl,
                iconSvg,
                licenseName,
                licenseUrl,
                stroomPath,
                details,
                gitUrl,
                gitBranch,
                gitPath,
                gitCommit,
                contentStoreUiName);
    }

    @Override
    public String toString() {
        String svgContent = (iconSvg == null ? "null" : "<svg content>");
        return "AppStoreContentPack{"
               + "stroomName='" + stroomName + '\'' +
               "  uiName='" + uiName + '\'' +
               ", iconUrl='" + iconUrl + '\'' +
               ", iconSvg='" + svgContent + '\'' +
               ", licenseName='" + licenseName + '\'' +
               ", licenseUrl='" + licenseUrl + '\'' +
               ", stroomPath='" + stroomPath + '\'' +
               ", details='" + details.substring(0, Math.min(details.length(), DETAILS_TRUNC)) + '\'' +
               ", gitUrl='" + gitUrl + '\'' +
               ", gitBranch='" + gitBranch + '\'' +
               ", gitPath='" + gitPath + '\'' +
               ", gitCommit='" + gitCommit + '\'' +
               ", contentStore UI name='" + contentStoreUiName + '\'' +
               '}';
    }
}
