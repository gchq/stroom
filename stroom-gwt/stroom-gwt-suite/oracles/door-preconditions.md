# Door preconditions — how to reach every presenter the walk has not

**Generated** by `stroom-stroom-gwt-suite/tools/build-door-preconditions.mjs` from the Stroom source, the
reachability graph, the mined show edges and the current coverage ledger. Do not hand-edit.

8 doors unreached. Each is classified by the EASIEST of its opener sites — one create path
makes a door reachable however many selection-gated paths it also has.

| class | doors | what it means | remedy |
| --- | ---: | --- | --- |
| plain | 6 | no guard found around the show | a walker or attribution gap — look at it |
| permission | 1 | gated by an app or document permission | a fact about this user — walk as an admin or accept |
| no-opener | 1 | no show edge at all | reachability research, or unwired (denominator) |

## plain (6)

* **AiAttachmentDataPresenter** _(AI, dialog)_ — parent reached: AskStroomAiPresenter
  - AskStroomAiPresenter.onViewAttachmentData() → plain
* **DownloadChatPresenter** _(AI, dialog)_ — parent reached: AskStroomAiPresenter
  - AskStroomAiPresenter.onDownloadChat() → plain · trigger: `Download`
* **QueryInfoPresenter** _(Dashboard, dialog)_
  - QueryInfo.prompt() → plain
* **ConstraintEditPresenter** _(Pathways, dialog)_
  - ConstraintListPresenter.onAdd() → create
  - ConstraintListPresenter.onEdit() → plain
* **PathwayEditPresenter** _(Pathways, dialog)_
  - PathwayListPresenter.onAdd() → create
  - PathwayListPresenter.onEdit() → plain
* **DocRefSelectionPresenter** _(Pipeline, screen/dialog via plugin)_
  - PipelinePlugin.save() → plain

## permission (1)

* **CredentialsManagerDialogPresenter** _(Credentials, dialog)_
  - ContentStoreContentPackDetailsPresenter.btnCreateGitRepoClick() → permission

## no-opener (1)

* **ContentPresenter** _(Gwt, UNKNOWN)_
  - via: (none)
