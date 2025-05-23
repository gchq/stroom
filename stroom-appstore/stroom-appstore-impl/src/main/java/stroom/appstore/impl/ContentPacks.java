package stroom.appstore.impl;

import stroom.appstore.shared.AppStoreContentPack;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Class to represent content packs from one URL.
 * Used to pull the content packs in from the YAML App Store file.
 */
public class ContentPacks {

    /** List of git repositories */
    @JsonProperty("gitRepos")
    private final Map<String, AppStoreContentPack> gitRepos = new HashMap<>();

    /**
     * Default constructor
     */
    public ContentPacks() {
        // No code
    }

    /**
     * @return an unmodifiable view of the map of
     * content pack name -> content pack.
     */
    public Map<String, AppStoreContentPack> getMap() {
        return Collections.unmodifiableMap(gitRepos);
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ContentPacks that = (ContentPacks) o;
        return Objects.equals(gitRepos, that.gitRepos);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(gitRepos);
    }

    @Override
    public String toString() {
        return "ContentPacks {" +
               "gitRepos=" + gitRepos +
               '}';
    }
}
