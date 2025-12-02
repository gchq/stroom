package stroom.gitrepo.api;

import stroom.gitrepo.shared.GitRepoDoc;
import stroom.util.shared.Message;

import java.io.IOException;
import java.util.List;

/**
 * Class to call the ImportExport mechanism to handle the import and export to
 * local Git repositories, then to sync the local repo with a remote repo.
 */
public interface GitRepoStorageService {

    /**
     * Called by pressing the Git Settings 'Push to Git' button.
     * @param gitRepoDoc    The document that we're pushing the button on.
     *                      Must not be null.
     * @param commitMessage The Git commit message. Must not be null.
     * @param calledFromUi True if the method is being called from the UI over
     *                     REST, false if being called from a Job.
     *                     Affects how some errors are handled.
     * @return The export summary. Might return if the export hasn't yet taken
     * place.
     * @throws IOException if something goes wrong
     */
    List<Message> exportDoc(GitRepoDoc gitRepoDoc,
                            final String commitMessage,
                            boolean calledFromUi)
        throws IOException;

    /**
     * Called when the user presses the Pull from Git button in the UI.
     *
     * @param gitRepoDoc The document holding the Git repo settings
     * @param isMockEnvironment Whether we're in a Mock environment during testing
     *                          thus ExplorerTree isn't there.
     * @return A list of messages about the import
     * @throws IOException if something goes wrong
     */
    List<Message> importDoc(GitRepoDoc gitRepoDoc,
                            boolean isMockEnvironment) throws IOException;

    /**
     * Checks if any updates are available in the Git Repo.
     * @param messages List to add messages to. Can be null if extra
     *                 info isn't needed.
     * @param gitRepoDoc The thing we want to check for updates. Must not be null.
     * @return true if updates are available, false if not.
     * @throws IOException if something goes wrong.
     */
    boolean areUpdatesAvailable(List<String> messages,
                                GitRepoDoc gitRepoDoc) throws IOException;

}
