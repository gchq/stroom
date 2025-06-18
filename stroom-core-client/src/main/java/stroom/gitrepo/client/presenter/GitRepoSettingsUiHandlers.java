package stroom.gitrepo.client.presenter;

import stroom.document.client.event.DirtyUiHandlers;
import stroom.task.client.TaskMonitorFactory;

/**
 * Interface to handle the button pushes from the GitRepo Settings tab.
 */
public interface GitRepoSettingsUiHandlers extends DirtyUiHandlers {

    /**
     * 'Push' button event handler.
     */
    void onGitRepoPush(TaskMonitorFactory taskMonitorFactory);

    /**
     * 'Pull' button event handler.
     */
    void onGitRepoPull(TaskMonitorFactory taskMonitorFactory);

    /**
     * 'Check for updates' button event handler.
     */
    void onCheckForUpdates(TaskMonitorFactory taskMonitorFactory);

    /**
     * 'Set Credentials' button event handler.
     */
    void onShowCredentialsDialog(TaskMonitorFactory taskMonitorFactory);

}
