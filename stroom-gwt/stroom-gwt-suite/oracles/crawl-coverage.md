# Crawl coverage — how much of the 414 have we reached?

**Generated** by `tools/build-crawl-coverage.mjs`. Do not hand-edit.

Joins the crawl output to `gwt-inventory.csv` through the identifiers each side publishes: dialog
captions (mined from the `ShowPopupEvent` site) and menu leaves (mined from the menu registration).

## Totals

| | count |
| --- | ---: |
| Presenters in the inventory | 414 |
| …with crawl evidence | **58** |
| …no evidence yet | 356 |
| Crawl files joined | 3 |
| Captions seen that match no presenter | 168 |

> A low number here is not a defect. Most presenters are embedded panels with no route of their own,
> and the crawls run so far cover three areas of forty-five. The point is that the gap is now
> COUNTABLE and named, rather than being a number about the crawler.

## By area

| area | reached | total |
| --- | ---: | ---: |
| Dashboard | 8 | 62 |
| Data receipt | 6 | 14 |
| Security | 5 | 40 |
| Data / streams | 4 | 29 |
| Explorer | 4 | 16 |
| AI | 3 | 9 |
| Import / Export | 3 | 6 |
| Node / cluster | 3 | 8 |
| Analytics | 2 | 32 |
| Annotation | 2 | 18 |
| HTTP | 2 | 2 |
| Index | 2 | 13 |
| About | 1 | 1 |
| Cache | 1 | 3 |
| Config / properties | 1 | 5 |
| Content store | 1 | 4 |
| Credentials | 1 | 9 |
| Entity | 1 | 10 |
| Jobs / scheduler | 1 | 3 |
| Monitoring | 1 | 1 |
| Pathways | 1 | 10 |
| Pipeline | 1 | 20 |
| Preferences | 1 | 4 |
| Processor | 1 | 13 |
| Query / search | 1 | 13 |
| Tasks | 1 | 4 |
| Activity | 0 | 4 |
| AWS / S3 | 0 | 1 |
| Content | 0 | 2 |
| Data generation | 0 | 3 |
| Dictionary | 0 | 5 |
| Documentation | 0 | 1 |
| Feed | 0 | 2 |
| Folder | 0 | 2 |
| Git repository | 0 | 3 |
| Kafka | 0 | 1 |
| Main | 0 | 1 |
| AI (OpenAI) | 0 | 2 |
| State (Plan B) | 0 | 11 |
| Script | 0 | 4 |
| Search | 0 | 9 |
| Visualisation | 0 | 6 |
| Welcome | 0 | 1 |
| XML schema | 0 | 2 |
| Statistics | 0 | 5 |

## Reached

* **AboutPresenter** — menu leaf "about" in annotations, Annotations (log)
* **AiChatHistoryPresenter** — caption "chat history" in annotations, security, Annotations (log)
* **AskStroomAiConfigPresenter** — caption "configure ask stroom ai" in annotations, security, Annotations (log)
* **AskStroomAiPresenter** — caption "ask stroom ai" in annotations, security, Annotations (log)
* **BatchDocumentPermissionsEditPresenter** — caption "batch change permissions for all filtered documents" in annotations, security, Annotations (log)
* **BatchDocumentPermissionsPresenter** — menu leaf "document permissions" in annotations, security, Annotations (log)
* **BatchExecutionScheduleEditPresenter** — caption "batch change selected schedules" in annotations, Annotations (log)
* **BrowseAnnotationPresenter** — menu leaf "browse annotations" in annotations, Annotations (log)
* **CachePresenter** — menu leaf "caches" in annotations, Annotations (log)
* **ContentStorePresenter** — menu leaf "content store" in annotations, Annotations (log)
* **ContentTemplateEditPresenter** — caption "add new template" in annotations, Annotations (log)
* **ContentTemplateTabPresenter** — menu leaf "content templates" in annotations, Annotations (log)
* **CredentialsPresenter** — menu leaf "credentials manager" in annotations, security, Annotations (log)
* **CurrentPasswordPresenter** — caption "enter your current password" in annotations, doc-test-dashboard, Annotations (log)
* **CurrentSelectionPresenter** — caption "current selection" in annotations, doc-test-dashboard, Annotations (log)
* **DatabaseTablesMonitoringPresenter** — menu leaf "database tables" in annotations, Annotations (log)
* **DataRetentionPresenter** — menu leaf "data retention" in annotations, Annotations (log)
* **DataRetentionRulePresenter** — caption "edit rule" in annotations, Annotations (log)
* **DataUploadPresenter** — caption "upload" in annotations, Annotations (log)
* **DependenciesTabPresenter** — menu leaf "dependencies" in annotations, Annotations (log)
* **EditAccountPresenter** — caption "create account" in security
* **ElementPresenter** — caption "filter streams" in annotations, Annotations (log)
* **ExecutionScheduleRunNowPresenter** — caption "run now" in annotations, Annotations (log)
* **ExportConfigPresenter** — menu leaf "export" in annotations, doc-test-dashboard, security, Annotations (log)
* **ExpressionPresenter** — caption "filter streams" in annotations, Annotations (log)
* **FindAnnotationPresenter** — caption "choose annotation" in annotations, doc-test-dashboard, Annotations (log)
* **FindInContentPresenter** — caption "find in content" in annotations, doc-test-dashboard, security, Annotations (log)
* **FindPresenter** — caption "find" in annotations, doc-test-dashboard, Annotations (log)
* **FsVolumeGroupPresenter** — menu leaf "data volumes" in annotations, Annotations (log)
* **GlobalPropertyTabPresenter** — menu leaf "properties" in annotations, Annotations (log)
* **HttpClientConfigPresenter** — caption "edit http client configuration" in annotations, Annotations (log)
* **HttpTlsConfigPresenter** — caption "edit http tls configuration" in annotations, Annotations (log)
* **ImportConfigPresenter** — menu leaf "import" in annotations, doc-test-dashboard, security, Annotations (log)
* **IndexVolumeGroupPresenter** — menu leaf "index volumes" in annotations, Annotations (log)
* **InfoDocumentPresenter** — caption "info" in doc-test-dashboard
* **JobPresenter** — menu leaf "jobs" in annotations, Annotations (log)
* **LayoutConstraintPresenter** — caption "set layout constraints" in annotations, doc-test-dashboard, Annotations (log)
* **NavigationPresenter** — menu leaf "delete tab session" in annotations, Annotations (log)
* **NewFsVolumeGroupPresenter** — caption "new" in annotations, doc-test-dashboard, security, Annotations (log)
* **NewIndexVolumeGroupPresenter** — caption "new" in annotations, doc-test-dashboard, security, Annotations (log)
* **NewNodeGroupPresenter** — caption "new" in annotations, doc-test-dashboard, security, Annotations (log)
* **NodeGroupPresenter** — menu leaf "node groups" in annotations, Annotations (log)
* **NodePresenter** — menu leaf "nodes" in annotations, Annotations (log)
* **ProcessorProfilePresenter** — menu leaf "processor profiles" in annotations, Annotations (log)
* **QueryFavouritesPresenter** — caption "query favourites" in annotations, doc-test-dashboard, Annotations (log)
* **QueryHistoryPresenter** — caption "query history" in annotations, doc-test-dashboard, Annotations (log)
* **RecentItemsPresenter** — caption "recent items" in annotations, doc-test-dashboard, Annotations (log)
* **RenameTabPresenter** — caption "rename tab" in annotations, doc-test-dashboard, Annotations (log)
* **ResultStorePresenter** — caption "search result stores" in annotations, doc-test-dashboard, Annotations (log)
* **RulePresenter** — caption "edit rule" in annotations, Annotations (log)
* **RulePresenter** — caption "edit rule" in annotations, Annotations (log)
* **RuleSetPresenter** — menu leaf "data receipt rules" in annotations, Annotations (log)
* **RulesPresenter** — caption "settings" in annotations, doc-test-dashboard, Annotations (log)
* **SettingsPresenter** — caption "settings" in annotations, doc-test-dashboard, Annotations (log)
* **TaskManagerPresenter** — menu leaf "server tasks" in annotations, Annotations (log)
* **TracesPresenter** — menu leaf "traces" in annotations, Annotations (log)
* **UserPreferencesPresenter** — menu leaf "preferences" in annotations, doc-test-dashboard, Annotations (log)
* **UserRefPopupPresenter** — menu leaf "user permissions report" in annotations

## Captions seen that match no presenter

Either the caption map is incomplete (a caption built from a variable, not a literal) or these are
not presenters. Reported because a one-sided join hides its own failures.

* `add`
* `add component`
* `add external user(s)`
* `add new account`
* `add new api key`
* `add new rule above the selected one`
* `add new template above the selected one`
* `add new template below the selected one`
* `add to annotation`
* `add to favourites`
* `add user group`
* `administration`
* `analyticrule (document)`
* `annotate`
* `annotations`
* `api specification`
* `application permissions`
* `apply to filtered`
* `apply to selection`
* `bar`
* `batch edit permissions for filtered documents`
* `batch edit schedules`
* `change password`
* `check if there are updates that could be pulled`
* `choose dashboard`
* `choose pipeline to process results with`
* `configuration`
* `configure`
* `configure the http client used to talk to the git repository`
* `conversation history`
* `copy`
* `copy as`
* `copy test dashboard`
* `create annotation`
* `create favourite from current query`
* `create group`
* `create new annotation`
* `create new api key`
* `create new favourite`
* `create report`
* `create rule`
* `create user`
* `dashboard`
* `dashboard (document)`
* `data processing`
* `datagen (document)`
* `delete`
* `dictionary (document)`
* `documentation`
* `documentation (document)`
* `doughnut`
* `download`
* `download dictionary words`
* `download query`
* `duplicate to...`
* `edit tags`
* `edit tags on test dashboard`
* `elasticcluster (document)`
* `elasticindex (document)`
* `end this user's sessions and revoke their tokens`
* `execution schedule manager`
* `favourites`
* `feed (document)`
* `filter`
* `filter documents to apply permissions changes on`
* `folder`
* `gitrepo (document)`
* `help`
* `hide`
* `history`
* `indexing`
* `input`
* `kafkaconfig (document)`
* `line`
* `luceneindex (document)`
* `main menu`
* `manage accounts`
* `manage api keys`
* `monitoring`
* `move`
* `move test dashboard`
* `navigation`
* `new conversation`
* `new credentials`
* `new field`
* `new folder`
* `ok`
* `openaimodel (document)`
* `params`
* `pathways (document)`
* `permissions`
* `pipeline (document)`
* `planb (document)`
* `process`
* `pull remote changes into stroom`
* `query`
* `query (document)`
* `remove`
* `rename`
* `rename test dashboard`
* `report (document)`
* `revert to default`
* `run schedules now`
* `s3config (document)`
* `save 'all_work_no_play_language_map' as`
* `save 'bitmap-reference' as`
* `save 'example index' as`
* `save 'example solr index' as`
* `save 'imp_exp_test_dashboard' as`
* `save 'imp_exp_test_dictionary' as`
* `save 'imp_exp_test_index' as`
* `save 'imp_exp_test_kafka_config' as`
* `save 'imp_exp_test_script_1' as`
* `save 'imp_exp_test_statistics' as`
* `save 'imp_exp_test_visualisation' as`
* `save 'imp_exp_test_xml_schema' as`
* `save 'mycore' as`
* `save 'seed analytic rule' as`
* `save 'seed data generator' as`
* `save 'seed documentation' as`
* `save 'seed elastic cluster' as`
* `save 'seed elastic index' as`
* `save 'seed openai model' as`
* `save 'seed report' as`
* `save 'seed s3 configuration' as`
* `save 'seed view' as`
* `save 'test dashboard' as`
* `save 'test_charsets_utf16be_bom-reference' as`
* `save as`
* `save new tab session`
* `schema is valid`
* `script (document)`
* `search`
* `security`
* `set as default`
* `set as default for all users`
* `set constraints`
* `set http client config`
* `set http tls config`
* `set password`
* `show menu`
* `show warnings`
* `sign out`
* `sign out other sessions`
* `signing keys`
* `solrindex (document)`
* `statisticstore (document)`
* `system (right-click)`
* `table`
* `table #2`
* `test connection`
* `test dashboard`
* `test dashboard (right-click)`
* `test model`
* `textconverter (document)`
* `tools`
* `transformation`
* `upgrade`
* `user`
* `user access`
* `user groups`
* `user preferences`
* `users`
* `view (document)`
* `view current selection`
* `visualisation (document)`
* `xmlschema (document)`
* `xslt (document)`

## No evidence yet

* AbstractComponentPresenter _(Dashboard)_
* AbstractDuplicateManagementPresenter _(Analytics)_
* AbstractFindPresenter _(Explorer)_
* AbstractMetaListPresenter _(Data / streams)_
* AbstractNotificationListPresenter _(Analytics)_
* AbstractNotificationPresenter _(Analytics)_
* AbstractPlanBSettingsPresenter _(State (Plan B))_
* AbstractProcessingPresenter _(Analytics)_
* AbstractQueryEditPresenter _(Analytics)_
* AbstractRefreshableComponentPresenter _(Dashboard)_
* AbstractSettingsTabPresenter _(Dashboard)_
* AccessTokenSecretPresenter _(Credentials)_
* AccountsListPresenter _(Security)_
* AccountsPresenter _(Security)_
* ActivityEditPresenter _(Activity)_
* ActivityListPresenter _(Activity)_
* AddEventLinkPresenter _(Annotation)_
* AiAttachmentDataPresenter _(AI)_
* AiChatHistoryResultListPresenter _(AI)_
* AiConfigGeneralPresenter _(AI)_
* AiConfigTableAnalysisPresenter _(AI)_
* AnalyticDataShardListPresenter _(Analytics)_
* AnalyticDataShardsPresenter _(Analytics)_
* AnalyticDuplicateManagementPresenter _(Analytics)_
* AnalyticEmailDestinationPresenter _(Analytics)_
* AnalyticNotificationEditPresenter _(Analytics)_
* AnalyticNotificationListPresenter _(Analytics)_
* AnalyticNotificationPresenter _(Analytics)_
* AnalyticProcessingPresenter _(Analytics)_
* AnalyticQueryEditPresenter _(Analytics)_
* AnalyticRulePresenter _(Analytics)_
* AnalyticStreamDestinationPresenter _(Analytics)_
* AnnotationEditPresenter _(Annotation)_
* AnnotationLinkPresenter _(Annotation)_
* AnnotationPresenter _(Annotation)_
* AnnotationTagCreatePresenter _(Annotation)_
* AnnotationTagEditPresenter _(Annotation)_
* AnnotationTagListPresenter _(Annotation)_
* AnnotationTagPresenter _(Annotation)_
* ApiKeysListPresenter _(Security)_
* ApiKeysPresenter _(Security)_
* AppPermissionsEditPresenter _(Security)_
* AppPermissionsPresenter _(Security)_
* AppUserPermissionsListPresenter _(Security)_
* AuthenticationErrorPresenter _(Security)_
* BasicEmbeddedQuerySettingsPresenter _(Dashboard)_
* BasicKeyValueInputSettingsPresenter _(Dashboard)_
* BasicListInputSettingsPresenter _(Dashboard)_
* BasicQuerySettingsPresenter _(Dashboard)_
* BasicSettingsTabPresenter _(Dashboard)_
* BasicTableFilterSettingsPresenter _(Dashboard)_
* BasicTableSettingsPresenter _(Dashboard)_
* BasicTextInputSettingsPresenter _(Dashboard)_
* BasicTextSettingsPresenter _(Dashboard)_
* BasicVisSettingsPresenter _(Dashboard)_
* BatchProcessorFilterEditPresenter _(Processor)_
* CacheListPresenter _(Cache)_
* CacheNodeListPresenter _(Cache)_
* ChangeAssignedToPresenter _(Annotation)_
* ChangePasswordPresenter _(Security)_
* ChangeStatusPresenter _(Annotation)_
* CharacterNavigatorPresenter _(Data / streams)_
* CharacterRangeSelectionPresenter _(Data / streams)_
* ChooserPresenter _(Annotation)_
* ColumnFilterPresenter _(Dashboard)_
* ColumnFunctionEditorPresenter _(Dashboard)_
* ColumnSelectionPresenter _(Dashboard)_
* ColumnValuesFilterPresenter _(Dashboard)_
* CommentEditPresenter _(Annotation)_
* ConfigPropertyClusterValuesListPresenter _(Config / properties)_
* ConfigPropertyClusterValuesPresenter _(Config / properties)_
* ConstraintEditPresenter _(Pathways)_
* ConstraintListPresenter _(Pathways)_
* ContentStoreContentPackDetailsPresenter _(Content store)_
* ContentStoreContentPackListPresenter _(Content store)_
* ContentStoreCredentialsDialogPresenter _(Content store)_
* ContentTabPanePresenter _(Content)_
* ContentTabPresenter _(Content)_
* ContentTemplateListPresenter _(Data receipt)_
* CopyDocumentPresenter _(Entity)_
* CreateDocumentPresenter _(Entity)_
* CreateExternalUserPresenter _(Security)_
* CreateMultipleUsersPresenter _(Security)_
* CreateNewGroupPresenter _(Security)_
* CreateUserPresenter _(Security)_
* CredentialEditPresenter _(Credentials)_
* CredentialSettingsPresenter _(Credentials)_
* CredentialsListPresenter _(Credentials)_
* CredentialsManagerDialogPresenter _(Credentials)_
* CustomRowStylePresenter _(Dashboard)_
* DashboardPresenter _(Dashboard)_
* DashboardSuperPresenter _(Dashboard)_
* DataGenPresenter _(Data generation)_
* DataGenProcessingPresenter _(Data generation)_
* DataGenSettingsPresenter _(Data generation)_
* DataPresenter _(Data / streams)_
* DataPreviewTabPresenter _(Data / streams)_
* DataRetentionImpactPresenter _(Data receipt)_
* DataRetentionPolicyListPresenter _(Data receipt)_
* DataRetentionPolicyPresenter _(Data receipt)_
* DenseVectorFieldPresenter _(Index)_
* DependenciesInfoPresenter _(Import / Export)_
* DependenciesPresenter _(Import / Export)_
* DictionaryListPresenter _(Dictionary)_
* DictionaryPresenter _(Dictionary)_
* DictionarySettingsPresenter _(Dictionary)_
* DocPresenter _(Entity)_
* DocRefListPresenter _(Dictionary)_
* DocRefSelectionPresenter _(Pipeline)_
* DocSelectionBoxPresenter _(Explorer)_
* DocTabPresenter _(Entity)_
* DocumentationPresenter _(Documentation)_
* DocumentCreatePermissionsListPresenter _(Security)_
* DocumentListPresenter _(Explorer)_
* DocumentPermissionsListPresenter _(Explorer)_
* DocumentUserCreatePermissionsEditPresenter _(Security)_
* DocumentUserPermissionsEditPresenter _(Security)_
* DocumentUserPermissionsListPresenter _(Security)_
* DocumentUserPermissionsPresenter _(Security)_
* DownloadChatPresenter _(AI)_
* DownloadPresenter _(Dashboard)_
* DuplicateManagementListPresenter _(Analytics)_
* DurationPresenter _(Annotation)_
* EditApiKeyPresenter _(Security)_
* EditExpressionPresenter _(Dashboard)_
* EditExpressionPresenter _(Data / streams)_
* EditFeedDependencyPresenter _(Processor)_
* EditorPreferencesPresenter _(Preferences)_
* ElasticClusterPresenter _(Search)_
* ElasticClusterSettingsPresenter _(Search)_
* ElasticIndexFieldListPresenter _(Search)_
* ElasticIndexPresenter _(Search)_
* ElasticIndexSettingsPresenter _(Search)_
* EmailResetPasswordPresenter _(Security)_
* EmbeddedQueryPresenter _(Dashboard)_
* EmbeddedQuerySettingsPresenter _(Dashboard)_
* EntityCheckTreePresenter _(Explorer)_
* EntityTreePresenter _(Explorer)_
* ExplorerNodeEditTagsPresenter _(Explorer)_
* ExplorerNodeRemoveTagsPresenter _(Explorer)_
* ExplorerPopupPresenter _(Explorer)_
* ExpressionTreePresenter _(Query / search)_
* FeedDependencyListPresenter _(Processor)_
* FeedDependencyPresenter _(Processor)_
* FeedPresenter _(Feed)_
* FeedSettingsPresenter _(Feed)_
* FieldEditPresenter _(Data receipt)_
* FieldListPresenter _(Data receipt)_
* FindAnnotationListPresenter _(Annotation)_
* FindDocResultListPresenter _(Explorer)_
* FolderPresenter _(Folder)_
* FolderRootPresenter _(Folder)_
* FormatPresenter _(Dashboard)_
* FsVolumeEditPresenter _(Data / streams)_
* FsVolumeGroupEditPresenter _(Data / streams)_
* FsVolumeGroupListPresenter _(Data / streams)_
* FsVolumeStatusListPresenter _(Data / streams)_
* GitRepoCommitDialogPresenter _(Git repository)_
* GitRepoPresenter _(Git repository)_
* GitRepoSettingsPresenter _(Git repository)_
* HistogramSettingsPresenter _(State (Plan B))_
* ImportConfigConfirmPresenter _(Import / Export)_
* IncludeExcludeFilterDictionaryPresenter _(Dashboard)_
* IncludeExcludeFilterPresenter _(Dashboard)_
* IndexFieldEditPresenter _(Index)_
* IndexFieldListPresenter _(Index)_
* IndexPresenter _(Index)_
* IndexSettingsPresenter _(Index)_
* IndexShardPresenter _(Index)_
* IndexVolumeEditPresenter _(Index)_
* IndexVolumeGroupEditPresenter _(Index)_
* IndexVolumeGroupListPresenter _(Index)_
* IndexVolumeListPresenter _(Index)_
* IndexVolumeStatusListPresenter _(Index)_
* ItemNavigatorPresenter _(Data / streams)_
* ItemSelectionPresenter _(Data / streams)_
* JobListPresenter _(Jobs / scheduler)_
* JobNodeListPresenter _(Jobs / scheduler)_
* KafkaConfigPresenter _(Kafka)_
* KeyStoreSecretPresenter _(Credentials)_
* KeyValueInputPresenter _(Dashboard)_
* KeyValueInputSettingsPresenter _(Dashboard)_
* LinkedEventPresenter _(Annotation)_
* LinkTabPanelPresenter _(Entity)_
* ListInputPresenter _(Dashboard)_
* ListInputSettingsPresenter _(Dashboard)_
* LoginPresenter _(Security)_
* MainPresenter _(Main)_
* ManageActivityPresenter _(Activity)_
* ManageGlobalPropertyEditPresenter _(Config / properties)_
* ManageGlobalPropertyListPresenter _(Config / properties)_
* MarkdownEditPresenter _(Entity)_
* MarkdownPreviewPresenter _(Entity)_
* MarkerListPresenter _(Data / streams)_
* MetaListPresenter _(Data / streams)_
* MetaPresenter _(Data / streams)_
* MetaRelationListPresenter _(Data / streams)_
* MetricSettingsPresenter _(State (Plan B))_
* MoveDocumentPresenter _(Entity)_
* MultiChooserPresenter _(Annotation)_
* MultiRulesPresenter _(Dashboard)_
* NameDocumentPresenter _(Entity)_
* NamePresenter _(Dashboard)_
* NewElementPresenter _(Pipeline)_
* NewPipelineReferencePresenter _(Pipeline)_
* NewPropertyPresenter _(Pipeline)_
* NodeGroupEditPresenter _(Node / cluster)_
* NodeGroupListPresenter _(Node / cluster)_
* NodeGroupStateListPresenter _(Node / cluster)_
* NodeJobListPresenter _(Node / cluster)_
* NodeStatusListPresenter _(Node / cluster)_
* OpenAIModelPresenter _(AI (OpenAI))_
* OpenAIModelSettingsPresenter _(AI (OpenAI))_
* PathwayEditPresenter _(Pathways)_
* PathwayListPresenter _(Pathways)_
* PathwaysPresenter _(Pathways)_
* PathwaysSettingsPresenter _(Pathways)_
* PathwaysSplitPresenter _(Pathways)_
* PathwayTreePresenter _(Pathways)_
* PipelinePresenter _(Pipeline)_
* PipelineReferenceListPresenter _(Pipeline)_
* PipelineStructurePresenter _(Pipeline)_
* PipelineTreePresenter _(Pipeline)_
* PlanBPresenter _(State (Plan B))_
* PlanBSettingsPresenter _(State (Plan B))_
* ProcessChoicePresenter _(Data / streams)_
* ProcessorEditPresenter _(Processor)_
* ProcessorLimitsPresenter _(Dashboard)_
* ProcessorListPresenter _(Processor)_
* ProcessorPresenter _(Processor)_
* ProcessorProfileEditPresenter _(Processor)_
* ProcessorProfileListPresenter _(Processor)_
* ProcessorTaskListPresenter _(Data / streams)_
* ProcessorTaskPresenter _(Data / streams)_
* ProcessorTaskPresenter _(Processor)_
* ProcessorTaskSummaryPresenter _(Data / streams)_
* ProfilePeriodEditPresenter _(Processor)_
* ProfilePeriodListPresenter _(Processor)_
* PropertyListPresenter _(Pipeline)_
* QueryDocEditPresenter _(Query / search)_
* QueryDocPresenter _(Query / search)_
* QueryEditPresenter _(Query / search)_
* QueryHelpPresenter _(Query / search)_
* QueryInfoPresenter _(Dashboard)_
* QueryPresenter _(Dashboard)_
* QueryResultTablePresenter _(Query / search)_
* QueryResultTableSplitPresenter _(Query / search)_
* QueryResultVisPresenter _(Query / search)_
* QuerySettingsPresenter _(Dashboard)_
* QueryToolbarPresenter _(Query / search)_
* RangeStateSettingsPresenter _(State (Plan B))_
* RenameColumnPresenter _(Dashboard)_
* ReportDuplicateManagementPresenter _(Analytics)_
* ReportNotificationListPresenter _(Analytics)_
* ReportNotificationPresenter _(Analytics)_
* ReportPresenter _(Analytics)_
* ReportProcessingPresenter _(Analytics)_
* ReportQueryEditPresenter _(Analytics)_
* ReportSettingsPresenter _(Analytics)_
* ResetPasswordPresenter _(Security)_
* ResultStoreListPresenter _(Query / search)_
* ResultStoreSettingsPresenter _(Query / search)_
* RuleListPresenter _(Dashboard)_
* RuleSetListPresenter _(Data receipt)_
* RuleSetSettingsPresenter _(Data receipt)_
* S3ConfigPresenter _(AWS / S3)_
* ScheduledProcessEditPresenter _(Analytics)_
* ScheduledProcessHistoryListPresenter _(Analytics)_
* ScheduledProcessingPresenter _(Analytics)_
* ScheduledProcessListPresenter _(Analytics)_
* ScriptDependencyListPresenter _(Script)_
* ScriptListPresenter _(Script)_
* ScriptPresenter _(Script)_
* ScriptSettingsPresenter _(Script)_
* SelectionHandlerListPresenter _(Dashboard)_
* SelectionHandlerPresenter _(Dashboard)_
* SelectionHandlersPresenter _(Dashboard)_
* SelectionSummaryPresenter _(Data / streams)_
* SessionSettingsPresenter _(State (Plan B))_
* SigningKeyListPresenter _(Security)_
* SigningKeyPresenter _(Security)_
* SolrIndexFieldEditPresenter _(Search)_
* SolrIndexFieldListPresenter _(Search)_
* SolrIndexPresenter _(Search)_
* SolrIndexSettingsPresenter _(Search)_
* SourcePresenter _(Data / streams)_
* SourceTabPresenter _(Data / streams)_
* SplashPresenter _(Activity)_
* SshKeySecretPresenter _(Credentials)_
* SslConfigPresenter _(AI)_
* StateSettingsPresenter _(State (Plan B))_
* StatisticsCustomMaskListPresenter _(Statistics)_
* StatisticsDataSourcePresenter _(Statistics)_
* StatisticsDataSourceSettingsPresenter _(Statistics)_
* StatisticsFieldEditPresenter _(Statistics)_
* StatisticsFieldListPresenter _(Statistics)_
* StepControlPresenter _(Pipeline)_
* StepLocationLinkPresenter _(Pipeline)_
* StepLocationPresenter _(Pipeline)_
* SteppingFilterPresenter _(Pipeline)_
* SteppingMetaListPresenter _(Data / streams)_
* SteppingPresenter _(Pipeline)_
* StreamingProcessingPresenter _(Analytics)_
* TableBuilderProcessingPresenter _(Analytics)_
* TableFilterPresenter _(Dashboard)_
* TableFilterPresenter _(Dashboard)_
* TableFilterSettingsPresenter _(Dashboard)_
* TablePresenter _(Dashboard)_
* TableSettingsPresenter _(Dashboard)_
* TabSessionChooserPresenter _(Explorer)_
* TaskManagerListPresenter _(Tasks)_
* TemporalRangeStateSettingsPresenter _(State (Plan B))_
* TemporalStateSettingsPresenter _(State (Plan B))_
* TextConverterPresenter _(Pipeline)_
* TextConverterSettingsPresenter _(Pipeline)_
* TextInputPresenter _(Dashboard)_
* TextInputSettingsPresenter _(Dashboard)_
* TextPresenter _(Dashboard)_
* TextPresenter _(Data / streams)_
* TextPresenter _(Query / search)_
* TextSettingsPresenter _(Dashboard)_
* ThemePreferencesPresenter _(Preferences)_
* TimePreferencesPresenter _(Preferences)_
* TraceSettingsPresenter _(State (Plan B))_
* TracesListPresenter _(Pathways)_
* TypeFilterPresenter _(Explorer)_
* UnknownComponentPresenter _(Dashboard)_
* UserAccessListPresenter _(Security)_
* UserAccessPresenter _(Security)_
* UserAndGroupsPresenter _(Security)_
* UserDependenciesListPresenter _(Security)_
* UserInfoPresenter _(Security)_
* UserListPresenter _(Security)_
* UsernamePasswordSecretPresenter _(Credentials)_
* UserPermissionReportPresenter _(Security)_
* UserRefSelectionBoxPresenter _(Security)_
* UserSessionsListPresenter _(Security)_
* UsersPresenter _(Security)_
* UserTabPresenter _(Security)_
* UserTaskManagerPresenter _(Tasks)_
* UserTaskPresenter _(Tasks)_
* VisPresenter _(Dashboard)_
* VisSettingsPresenter _(Dashboard)_
* VisualisationAssetsAddItemDialogPresenter _(Visualisation)_
* VisualisationAssetsEditAssetDialogPresenter _(Visualisation)_
* VisualisationAssetsPresenter _(Visualisation)_
* VisualisationAssetsUploadFileDialogPresenter _(Visualisation)_
* VisualisationPresenter _(Visualisation)_
* VisualisationSettingsPresenter _(Visualisation)_
* WelcomePresenter _(Welcome)_
* WordListPresenter _(Dictionary)_
* XMLSchemaPresenter _(XML schema)_
* XMLSchemaSettingsPresenter _(XML schema)_
* XPathFilterPresenter _(Pipeline)_
* XPathListPresenter _(Pipeline)_
* XsltPresenter _(Pipeline)_