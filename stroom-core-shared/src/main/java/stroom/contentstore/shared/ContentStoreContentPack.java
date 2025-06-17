package stroom.contentstore.shared;

import stroom.docs.shared.Description;
import stroom.gitrepo.shared.GitRepoDoc;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Collection;
import java.util.Objects;

/**
 * Class represents a Content Pack within a Content Store.
 */
@Description(
        "Contains the information for an Content Store Content Pack"
)
@JsonPropertyOrder({
        "id",
        "uiName",
        "iconUrl",
        "iconSvg",
        "licenseName",
        "licenseUrl",
        "gitRepoName",
        "gitUrl",
        "gitBranch",
        "gitPath",
        "gitCommit",
        "gitNeedsAuth",
        "contentStoreUiName",
        "isInstalled"
})
@JsonInclude(Include.NON_NULL)
public class ContentStoreContentPack {

    @JsonProperty
    private ContentStoreMetadata contentStoreMetadata = null;

    /** The ID of this Content Pack within the Content Store */
    @JsonProperty
    private final String id;

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

    /** Name of the GitRepoDoc */
    @JsonProperty
    private final String gitRepoName;

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

    /** Whether this repo requires authentication */
    @JsonProperty
    private final Boolean gitNeedsAuth;

    /** Whether this content pack is already installed */
    @JsonProperty
    private Boolean isInstalled = Boolean.FALSE;

    /** Default Git path to use - the root */
    private static final String DEFAULT_GIT_PATH =
            "/";

    /** Length to truncate details field to in toString() */
    private static final int DETAILS_TRUNC = 25;

    /**
     * Constructor. Initialises the values from the YAML.
     * @param id Name as used in the ContentStore. Must not be null.
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
     * @param gitRepoName The name of the GitRepoDoc to create. That is, the
     *                    name as shown in the Explorer Tree. Can be null or
     *                    empty string, in which case the uiName will be used
     *                    instead.
     * @param gitUrl URL of remote Git repository. Must not be null.
     * @param gitBranch Name of Git branch. Must not be null.
     * @param gitPath Path to files we're interested in. Can be null in which
     *                case the root will be used.
     * @param gitCommit Hash of the Git commit to pull. Can be null in which
     *                  case the latest commit will be pulled.
     * @param gitNeedsAuth Whether this GIT repo needs authentication to pull
     *                     stuff from.
     */
    @JsonCreator
    public ContentStoreContentPack(@JsonProperty("id") final String id,
                                   @JsonProperty("uiName") final String uiName,
                                   @JsonProperty("iconUrl") final String iconUrl,
                                   @JsonProperty("iconSvg") final String iconSvg,
                                   @JsonProperty("licenseName") final String licenseName,
                                   @JsonProperty("licenseUrl") final String licenseUrl,
                                   @JsonProperty("stroomPath") final String stroomPath,
                                   @JsonProperty("details") final String details,
                                   @JsonProperty("gitRepoName") final String gitRepoName,
                                   @JsonProperty("gitUrl") final String gitUrl,
                                   @JsonProperty("gitBranch") final String gitBranch,
                                   @JsonProperty("gitPath") final String gitPath,
                                   @JsonProperty("gitCommit") final String gitCommit,
                                   @JsonProperty("gitNeedsAuth") final Boolean gitNeedsAuth) {

        // Implementation note:
        // Objects.requireNonNullElse() isn't available in GWT

        this.id = Objects.requireNonNull(id);
        this.uiName = Objects.requireNonNull(uiName);
        this.iconUrl = iconUrl;
        this.iconSvg = iconSvg;
        this.licenseName = licenseName == null ? "" : licenseName;
        this.licenseUrl = licenseUrl == null ? "" : licenseUrl;
        this.stroomPath = stroomPath == null || stroomPath.isEmpty() ? "/" : stroomPath;
        this.details = details == null ? "": details;
        this.gitRepoName = gitRepoName == null || gitRepoName.isEmpty() ? uiName : gitRepoName;
        this.gitUrl = Objects.requireNonNull(gitUrl);
        this.gitBranch = Objects.requireNonNull(gitBranch);
        this.gitPath = gitPath == null ? DEFAULT_GIT_PATH : gitPath;
        this.gitCommit = gitCommit == null ? "" : gitCommit;
        this.gitNeedsAuth = gitNeedsAuth == null ? Boolean.FALSE : gitNeedsAuth;
    }

    /**
     * @return The ID of this content pack.
     */
    public String getId() {
        return id;
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
     * @return The name of the GitRepoDoc to create. Based on the gitRepoName
     * field in the YAML, or if that is null or empty, the uiName.
     */
    public String getGitRepoName() {
        return gitRepoName;
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
     * @return Whether this Git repo needs authentication to pull stuff from.
     * Never returns null.
     */
    public Boolean getGitNeedsAuth() {
        return gitNeedsAuth;
    }

    /**
     * Sets the metadata of the content store that this belongs to.
     * Resolved later. This structure may change. Only called when creating the list of
     * content packs. Shouldn't be called by anything else.
     * @param meta The metadata. Must not be null.
     */
    public void setContentStoreMetadata(final ContentStoreMetadata meta) {
        Objects.requireNonNull(meta);
        this.contentStoreMetadata = meta;
    }

    /**
     * @return The metadata of the content store this belongs to.
     * Never returns null once creation of this Content Pack is
     * complete.
     */
    public ContentStoreMetadata getContentStoreMetadata() {
        Objects.requireNonNull(this.contentStoreMetadata);
        return contentStoreMetadata;
    }

    /**
     * Used by JSON serialization to set the value of the isInstalled
     * flag. Should not be called by normal code. Instead, call
     * checkIfInstalled().
     * @param isInstalled true if this content pack is already installed.
     */
    @SuppressWarnings("unused")
    public void setIsInstalled(final Boolean isInstalled) {
        if (isInstalled == null) {
            this.isInstalled = Boolean.FALSE;
        } else {
            this.isInstalled = isInstalled;
        }
    }

    /**
     * Returns Boolean rather that boolean to keep JSON serialization happy.
     * @return true if this content pack is already installed, as determined
     * by the checkMatches() method. Never returns null.
     */
    public Boolean isInstalled() {
        return isInstalled;
    }

    /**
     * Checks the given collection of GitRepoDocs to see if any of them
     * match this content pack. If one does then mark this content pack
     * as installed.
     * @param docs The collection of GitRepos that already exist. Must
     *             not be null but can be empty.
     */
    public void checkIfInstalled(final Collection<GitRepoDoc> docs) {
        Objects.requireNonNull(docs);
        for (var doc : docs) {
            if (this.matches(doc)) {
                this.isInstalled = Boolean.TRUE;
                break;
            }
        }
    }

    /**
     * Returns whether this Content Pack matches the given GitRepoDoc.
     * Matches on the Content Store ownerID and the Content Pack ID.
     * Doesn't look at anything else, so may get clashes between
     * Content Packs and manually created GitRepos.
     * @param gitRepoDoc The existing Stroom GitRepoDoc to check.
     * @return true if there is a match; false otherwise.
     */
    public boolean matches(final GitRepoDoc gitRepoDoc) {

        // Check if Content Store ownerId matches
        boolean ownerIdMatch = false;
        if (this.contentStoreMetadata != null) {
            String myOwnerId = this.contentStoreMetadata.getOwnerId();
            if (gitRepoDoc.getContentStoreMetadata() != null) {
                String gitRepoOwnerId = gitRepoDoc.getContentStoreMetadata().getOwnerId();

                ownerIdMatch = myOwnerId.equals(gitRepoOwnerId);
            }
        }

        // Check content pack ID
        boolean contentPackIdMatch = Objects.equals(this.id, gitRepoDoc.getContentStoreContentPackId());

        // Both must match
        return ownerIdMatch && contentPackIdMatch;
    }

    /**
     * Copies the settings in this Content Pack into the GitRepoDoc.
     * @param gitRepoDoc The doc to copy settings into.
     */
    public void updateSettingsIn(final GitRepoDoc gitRepoDoc) {
        gitRepoDoc.setContentStoreMetadata(contentStoreMetadata);
        gitRepoDoc.setContentStoreContentPackId(id);
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
        final ContentStoreContentPack that = (ContentStoreContentPack) o;
        return Objects.equals(contentStoreMetadata, that.contentStoreMetadata)
               && Objects.equals(id, that.id)
               && Objects.equals(uiName, that.uiName)
               && Objects.equals(iconUrl, that.iconUrl)
               && Objects.equals(iconSvg, that.iconSvg)
               && Objects.equals(licenseName, that.licenseName)
               && Objects.equals(licenseUrl, that.licenseUrl)
               && Objects.equals(stroomPath, that.stroomPath)
               && Objects.equals(details, that.details)
               && Objects.equals(gitRepoName, that.gitRepoName)
               && Objects.equals(gitUrl, that.gitUrl)
               && Objects.equals(gitBranch, that.gitBranch)
               && Objects.equals(gitPath, that.gitPath)
               && Objects.equals(gitCommit, that.gitCommit)
               && Objects.equals(gitNeedsAuth, that.gitNeedsAuth)
               && Objects.equals(isInstalled, that.isInstalled);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contentStoreMetadata,
                id,
                uiName,
                iconUrl,
                iconSvg,
                licenseName,
                licenseUrl,
                stroomPath,
                details,
                gitRepoName,
                gitUrl,
                gitBranch,
                gitPath,
                gitNeedsAuth,
                gitCommit,
                isInstalled);
    }

    @Override
    public String toString() {
        String svgContent = (iconSvg == null ? "null" : "<svg content>");
        return "ContentStoreContentPack{"
               + "contentStore Metadata='" + contentStoreMetadata + '\''
               + "ID='" + id + '\''
               + "  uiName='" + uiName + '\''
               + ", iconUrl='" + iconUrl + '\''
               + ", iconSvg='" + svgContent + '\''
               + ", licenseName='" + licenseName + '\''
               + ", licenseUrl='" + licenseUrl + '\''
               + ", stroomPath='" + stroomPath + '\''
               + ", details='" + details.substring(0, Math.min(details.length(), DETAILS_TRUNC)) + '\''
               + ", gitRepoName='" + gitRepoName + '\''
               + ", gitUrl='" + gitUrl + '\''
               + ", gitBranch='" + gitBranch + '\''
               + ", gitPath='" + gitPath + '\''
               + ", gitCommit='" + gitCommit + '\''
               + ", gitNeedsAuth='" + gitNeedsAuth + '\''
               + ", isInstalled=" + isInstalled + '\''
               + '}';
    }
}
