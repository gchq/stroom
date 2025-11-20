# Change Log

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/)
and this project adheres to [Semantic Versioning](http://semver.org/).


## [Unreleased]

~~~
DO NOT ADD CHANGES HERE - ADD THEM USING log_change.sh
~~~


## [v7.11-beta.10] - 2025-11-20

* Issue **#5257** : Upgrade Lucene to 10.3.1.

* Issue **#5263** : Add copy for selected rows.

* Remove default value for `feedStatus.url` in the proxy config yaml as downstream host should now be used instead.


## [v7.11-beta.9] - 2025-11-19

* Issue **#5192** : Support Elasticsearch kNN search on dense_vector fields.

* Issue **#5124** : Change cluster lock `tryLock` to use the database record locks rather than the inter-node lock handler.

* Issue **#5254** : Fix document NPE.

* Issue **#5250** : Add a new property `stroom.security.identity.autoCreateAdminAccountOnBoot` to control auto-creation of the default administrator account `admin` when stroom is running with an identity provider type of `INTERNAL_IDP` or `TEST_CREDENTIALS`. Enabling this property will create the `admin` account and stroom user. It will create the group `Administrators`, grant the app permission `Administrator` to it and add the `admin` user to the group. The auto-creation of the admin account/user was erroneously removed in a previous version of stroom.

* Change default for `.receive.enabledAuthenticationTypes` to `[CERTIFICATE,TOKEN]` and add `.receive.authenticationRequired` to the default docker config yml files for both stroom and proxy.


## [v7.11-beta.8] - 2025-11-10

* Issue **#5218** : When `autoContentCreation` is enabled, don't attempt to find a content template if the `Feed` header has been provided and the feed exists.

* Issue **#5244** : Fix proxy throwing an error when attempting to do a feed status check.

* Issue **#5186** : Remove error logging for expected Plan B snapshot checking behaviour.

* Issue **#5220** : Fix duplicate check column list change confirm dialog not showing if the duplicate check store is on a remote node.

* Issue **#5126** : Improve Plan B null handling.

* Issue **#5225** : Fix NPE on DocRef.

* Issue **#5237** : Fix Plan B lookup no content error.


## [v7.11-beta.7] - 2025-10-20

* Issue **#656** : Allow table filters to use dictionaries.

* Issue **#672** : Dashboards will only auto refresh when selected.

* Issue **#5149** : Fix context menus not appearing on dashboard tables.

* Issue **#2029** : Add OS memory stats to the node status stats.

* Uplift dependency java-jwt 4.4.0 => 4.5.0.

* Uplift org.apache.commons:commons-csv from 1.10.0 to 1.14.1.

* Uplift dependency org.apache.commons:commons-pool2 from 2.12.0 to 2.12.1.

* Issue **#3799** : Search for tags in Find In Content.

* Issue **#3335** : Preserve escape chars not preceding delimiters.

* Bumps jooq from 3.20.5 to 3.20.8.

* Bumps com.mysql:mysql-connector-j from 9.2.0 to 9.4.0.

* Bumps flyway from 11.9.1 to 11.14.0.

* Issue **#1429** : Protect against large file ingests.

* Add a proxy zip file ingest mechanism to proxy. Add property branch `proxyConfig.dirScanner` to the config.

* Issue **#5175** : Fix zip handling.

* Fix config validation not being run on config branches that are items in a list, e.g. `forwardHttpDestinations`.

* Remove `NotNull` validation condition on `forwardHttpDestinations[n].apiKey` as proxy may use OAuth tokens to authenticate with the downstream destination.

* Issue **#5175** : Add warning messages to stroom and proxy `/datafeed` to warn if a zip is received that contains paths that would unzip outside of a target directory. Only a warning as the paths in a zip sent to `/datafeed` are not used by stroom/proxy.

* Uplift all the content packs that are included in the docker and zip distributions.

* Issue **#5191** : Fix UI NPE.

* Issue **#5189** : Change how the proxy directory queues are initialised. Now on initialisation, the min/max ids take into account incomplete paths, so if the largest path is `1/900` then the max id will be taken to be `900999`. When proxy is getting the next item off the queue it will delete any incomplete paths it finds en-route to the next valid item.

* Issue **#5126** : Fix Plan B condense.

* Issue **#5200** : Fix problem of duplicate check store failing to open due to its directory being deleted.

* Issue **#5201** : Clear out the duplicate check store if the column names are changed. Add a confirm dialog to the Analytic Rule doc save action, to get the user to confirm that all dup check data will be deleted when there is a change to the derived/explicit dup check columns.

* Issue **#5198** : Increase the maximum limit for `top()` to 10,000.

* Issue **#5117** : Send Plan B data to all enabled target nodes regardless of active status.

* Issue **#5146** : Fix use of not equals in annotation queries.

* Issue **#5145** : Expand all now affects favourites.

* Issue **#5109** : Fix pipeline migration.

* Issue **#5147** : Fix proxy omitting allowed headers (e.g. 'Feed') if the case does not match that in the allowed set.

* Issue **#5148** : Allow annotation users to edit labels.


## [v7.11-beta.6] - 2025-09-23

* Issue **#4121** : Add rename option for pipeline elements.

* Issue **#2374** : Add pipeline element descriptions.

* Issue **#4099** : Add InRange function.

* Issue **#2374** : Add description is now editable for pipeline elements.

* Issue **#268** : Add not contains and not exists filters to pipeline stepping.

* Issue **#844** : Add functions for hostname and hostaddress.

* Issue **#4579** : Add table name/id to conditional formatting exceptions.

* Issue **#4124** : Show severity of search error messages.

* Issue **#4369** : Add new rerun scheduled execution icon.

* Issue **#3207** : Add maxStringFieldLength table setting.

* Issue **#1249** : Dashboard links can open in the same tab.

* Issue **#1304** : Copy dashboard components between dashboards.

* Issue **#4614** : Fix StroomQL highlight.

* Issue **#2145** : New add-meta xslt function.

* Issue **#370** : Perform schema validation on save.

* Issue **#397** : Copy user permissions.

* Issue **#5088** : Add table column filter dashboard component.

* Issue **#2571** : Show Tasks for processor filter.

* Issue **#4177** : Add stream id links.

* Issue **#5137** : Fix how proxy adds HTTP headers when sending downstream. It now only adds received meta entries to the headers if they are on an allow list. This list is made up of a hard coded base list `accountId, accountName, classification, component, contextEncoding, contextFormat, encoding, environment, feed, format, guid, schema, schemaVersion, system, type` and is supplemented by the new config property `forwardHeadersAdditionalAllowSet` in the `forwardHttpDestinations` items.

* Issue **#5135** : Fix proxy multi part gzip handling.

* Uplift JDK to 21.0.8_9 in docker images and sdkmanrc.

* Issue **#5130** : Fix raw size meta bug.

* Issue **#5132** : Fix missing session when AWS ALB does the code flow.

* Fix the OpenID code flow to stop the session being lost after redirection back to the initiating URL.

* Issue **#5101** : Fix select-all filtering when doing a reprocess of everything in a folder. It no longer tries to re-process deleted items streams.

* Issue **#5086** : Improve stream error handling.

* Change the resource store to not rely on sessions. Resources are now linked to a user.

* Issue **#5114** : Improve handling of loss of connection to IDP.

* Change the way security filter decides whether to authenticate or not, e.g. how it determines what is a static resource that does not need authentication.

* Issue **#5115** : Use correct  header during proxy forward requests.

* Issue **#5121** : Proxy aggregation now keeps only common headers in aggregated data.

* Fix exception handling of DistributedTaskFetcher so it will restart after failure.

* Issue **#5127** : Maintain case for proxy meta attributes when logging.

* Issue **#5091** : Stop reference data loads failing if there are no entries in the stream.

* Add `ReceiptId` to the INFO message on data receipt.

* Issue **#5095** : Lock the cluster to perform pipeline migration to prevent other nodes clashing.

* Issue **#5099** : Fix Plan B session key serialisation.

* Issue **#5090** : Fix Plan B getVal() serialisation.

* Issue **#5106** : Fix ref loads with XML values where the `<value>` element name is not in lower case.

* Issue **#5042** : Allow the import of processor filters when the existing processor filter is in a logically deleted state. Add validation to the import confirm dialog to ensure the parent doc is selected when a processor filter is selected.

* Change DocRef Info Cache to evict entries on document creation to stop stroom saying that a document doesn't exist after import.

* Issue **#5077** : Fix bug in user full name templating where it is always re-using the first value, i.e. setting every user to have the full name of the first user to log in.

* Issue **#5047** : Replace the property `stroom.security.authentication.openid.validateAudience` with `stroom.security.authentication.openid.allowedAudiences` (defaults to empty) and `stroom.security.authentication.openid.audienceClaimRequired` (defaults to false). If the IDP is known to provide the `aud` claim (often populated with the `clientId`) then set `allowedAudiences` to contain that value and set `audienceClaimRequired` to `true`.

* Issue **#5068** : Add the config prop `stroom.security.authentication.openId.fullNameClaimTemplate` to allow the user's full name to be formed from a template containing a mixture of static text and claim variables, e.g. `${firstName} ${lastName}`. Unknown variables are replaced with an empty string. Default is `${name}`.

* Issue **#5066** : Change template syntax of `openid.publicKeyUriPattern` prop from positional variables (`{}`) to named variables (`${awsRegion}`). Default value has changed to `https://public-keys.auth.elb.${awsRegion}.amazonaws.com/${keyId}`. If this prop has been explicitly set, its value will need to be changed to named variables.

* Issue **#5073** : Trim the unique identity, display name and full name values for a user to ensure no leading/trailing spaces are stored. Includes DB migration `V07_10_00_005__trim_user_identities.sql` that trims existing values in the `name`, `display_name` and `full_name` columns of the `stroom_user` table.


## [v7.11-beta.5] - 2025-08-14

* Issue **#2279** : Drag and drop tabs.

* Issue **#2584** : Close all tabs to right/left.

* Issue **#5013** : Add row data to annotations.

* Issue **#3049** : Check for full/inactive/closed index volumes.

* Issue **#4070** : Show column information on hover tip.

* Issue **#3815** : Add selected tab colour property.

* Issue **#4790** : Add copy option for property names.

* Issue **#4121** : Add rename option for pipeline elements.

* Issue **#2823** : Add `autoImport` servlet to simplify importing content.

* Issue **#5013** : Add data to existing annotations.

* Issue **#5013** : Add links to other annotations and allow comments to make references to events and other annotations.

* Issue **#2374** : Add pipeline element descriptions.

* Issue **#2374** : Add description is now editable for pipeline elements.

* Issue **#4048** : Add query csv API.

* Issue **#5064** : Fix ref data store discovery.

* Issue **#5065** : Make public key URI configurable.

* Issue **#5046** : Stop feeds being auto-created when there is no content template match.

* Issue **#5062** : Fix permissions issue loading scheduled executors.

* Allow clientSecret to be null/empty for mTLS auth.


## [v7.11-beta.4] - 2025-07-29

* Change the proxy config properties `forwardUrl`, `livenessCheckUrl`, `apiKeyVerificationUrl` and `feedStatusUrl` to be empty by default and to allow them to be populated with either just a path or a full URL. `downstreamHost` config will be used to provide the host details if these properties are empty or only contain a path. Added the property `livenessCheckEnabled` to `forwardHttpDestinations` to control whether the forward destination liveness is checked (defaults to true).


## [v7.11-beta.3] - 2025-07-21

* Issue **#5028** : Add build info metrics `buildVersion`, `buildDate` and `upTime`.

* Add admin port servlets for Prometheus to scrape metrics from stroom and proxy. Servlet is available as `http://host:<admin port>/(stroom|proxy)Admin/prometheusMetrics`.

* Issue **#4735** : Add expand/collapse to result tables.

* Issue **#5013** : Allow annotation status update without requery.

* Issue **#5022** : Fix weird spinner behaviour.

* Issue **#259** : Maximise dashboard panes.

* Issue **#5027** : Allow users to choose run as user for processing.

* Issue **#4959** : Remove terms with field `Status` from re-process filter expressions.

* Issue **#4943** : Fix annotation creation to set provided assigned and status.

* Issue **#5016** : Fix sort state visibility on query table.

* Issue **#5034** : Fix query field help refresh.

* Issue **#5016** : Fix sort state visibility on query table.

* Issue **#5017** : Fix stuck spinner copying embedded query.

* Issue **#4974** : Fix Plan B condense job.

* Issue **#5030** : Add new property `.receive.x509CertificateDnFormat` to stroom and proxy to allow extraction of CNs from DNs in legacy `OPEN_SSL` format. The new property defaults to `LDAP`, which means no change to behaviour if left as is.

* Issue **#5025** : Fix parsing of hyperlinks in dashboard cells.

* Add in validation of the Conditional Formatting custom style colours to ensure the user can only enter valid colours and nothing else.

* Replace incorrect uses of `appendHtmlConstant` with SafeHtmlTemplate.

* Issue **#5012** : Fix errors when trying to use `lookup()` with a Context stream.

* Fix bug in reference data loading when the reference data value is XML that includes an element called `<value>`.

* Fix behaviour in reference data loading of XML values where attributes with no explicit namespace would be given the unnamed namespace of the parent `referenceData` document, i.e. `<ci:data xmlns="reference-data:2" name="name" value="001" />`.


## [v7.11-beta.2] - 2025-07-04

* Issue **#3874** : Add copy context menu to tables.

* Issue **#5016** : Fix sort state visibility on query table.

* Issue **#5017** : Fix stuck spinner copying embedded query.

* Fix NPE when proxy tries to fetch the receipt rules from downstream.


## [v7.11-beta.1] - 2025-07-02

* Add the Receive Data Rules screen to the Administration menu which requires the `Manage Data Receipt Rules` app permission. Add the following new config properties to the `receive` branch: `obfuscatedFields`, `obfuscationHashAlgorithm`, `receiptCheckMode` and `receiptRulesInitialFields`. Remove the property `receiptPolicyUuid`. Add the proxy config property `contentSync.receiveDataRulesUrl`.

* The proxy config property `feedStatus.enabled` has been replaced by `receive.receiptCheckMode` which takes values `FEED_STATUS`, `RECEIPT_POLICY` or `NONE`.

* In the proxy config, the named Jersey clients CONTENT_SYNC and FEED_STATUS have been removed and replaced with DOWNSTREAM.


## [v7.10-beta.6] - 2025-06-26

* Issue **#5007** : Add ceilingTime() and floorTime().

* Issue **#4977** : Limit user visibility in annotations.

* Issue **#4976** : Exclude deleted annotations.


## [v7.10-beta.5] - 2025-06-25

* Issue **#5002** : Fix Plan B env staying open after error.

* Issue **#5003** : Fix query date time formatting.

* Issue **#4974** : Improve logging.


## [v7.10-beta.4] - 2025-06-23

* Issue **#3083** : Allow data() table function to show the Info pane.

* Issue **#4959** : Remove terms with field `Status` from re-process filter expressions.

* Issue **#4974** : NPE debug.

* Issue **#4965** : Add dashboard screen to show current selection parameters.

* Issue **#4943** : Fix annotation creation to set provided assigned and status.

* Issue **#4496** : Add parse-dateTime xslt function.

* Issue **#4496** : Add format-dateTime xslt function.

* Issue **#4983** : Upgrade Flyway to work with newer version of MySQL.

* Issue **#3122** : Make date/time rounding functions time zone sensitive.

* Issue **#4984** : Add debug for Plan B tagged keys.

* Issue **#4969** : Add a checkbox to Content Templates edit screen to make it copy (and re-map) any xslt/textConverter docs in the inherited pipeline.

* Issue **#4991** : Add Plan B schema validation to ensure stores remain compatible especially when merging parts.

* Issue **#4854** : Maintain scrollbar position on datagrid.

* Issue **#4726** : Get meta for parent stream.

* Fix primitive value conversion of query field types.

* Issue **#4940** : Fix duplicate store error log.

* Issue **#4941** : Fix annotation data retention.

* Issue **#4968** : Improve Plan B file receipt.

* Issue **#4956** : Add error handling to duplicate check deletion.

* Issue **#4967** : Fix SQL deadlock.


## [v7.10-beta.3] - 2025-06-05

* Issue **#4900** : Add histogram and metric stores to Plan B.


## [v7.10-beta.2] - 2025-06-05

* Issue **#4940** : Fix duplicate store error log.

* Issue **#4941** : Fix annotation data retention.

* Issue **#4957** : Default vis settings are not added to Query pane visualisations.

* Issue **#3861** : Add Shard Id, Index Version to Index Shards searchable.

* Issue **#4112** : Allow use of Capture groups in the decode() function result.

* Issue **#3955** : Add case expression function.


## [v7.10-beta.1] - 2025-05-27

* Issue **#4484** : Change selection handling to use fully qualified keys.

* Issue **#4456** : Fix selection handling across multiple components by uniquely namespacing selections.

* Issue **#4886** : Fix ctrl+enter query execution for rules and reports.

* Issue **#4884** : Suggest only queryable fields in StroomQL where clause.

* Issue **#4742** : Allow embedded queries to be copies rather than references.

* Issue **#4894** : Plan B query without snapshots.

* Issue **#4896** : Plan B option to synchronise writes.

* Issue **#4720** : Add Plan B shards data source.

* Issue **#4919** : Add functions to format byte size strings.

* Issue **#4901** : Add advanced schema selection to Plan B to improve performance and reduce storage requirements.

* Fix primitive value conversion of query field types.

* Issue **#4945** : Increase index field name length.


## [v7.9-beta.12] - 2025-05-07

* Fix compile issues.


## [v7.9-beta.11] - 2025-05-07

* Issue **#4929** : Improve Plan B merge performance.

* Uplift the stroom/proxy docker base images to 21.0.7_6-jdk-alpine from 21.0.5_11-jdk-alpine.

* Issue **#4934** : Change the audit logging for dashboard queries to log the column names. It now logs one event for each table attached to the query.

* Fix field/function completions not being offered in the dashboard column expression editor.

* Fix OIDC code flow. Session wasn't being created so user was repeatedly redirected back to the IDP.

* Issue **#4892** : Prevent disabled users from authenticating.

* Issue **#4925** : Fix UI IndexOutOfBoundsException.

* Issue **#4926** : Fix UI NPE.

* Issue **#4893** : Fix text editor context menu styling.

* Issue **#4916** : Fix query results download so it doesn't require VIEW on the View. It now only needs USE on the View.

* Fix permissions checking when executing a Query. It no longer requires VIEW permission on the extraction pipeline, just USE.


## [v7.9-beta.10] - 2025-04-29

* Issue **#4875** : Fix select *.

* Issue **#3928** : Add merge filter for deeply nested data.

* Issue **#4211** : Prevent stream status in processing filters.

* Issue **#4927** : Fix TOKEN data feed auth when DATA_FEED_KEY is enabled.

* Issue **#4863** : Stop errors being logged on proxy shutdown.

* Issue **#4879** : Proxy - Improve concurrency protection when closing aggregates.

* Issue **#4889** : Fix stream downloads resulting in HTTP error 404.

* Proxy - Change the way zip receipt works to speed up client requests and to reduce cpu/io load caused by de-compressing/compressing the zip entries. Change the zip splitting to be asynchronous from the initial receipt, so the client doesn't have to wait for it to happen.

* Issue **#4914** : Fix Plan B condense bug.

* Issue **#4915** : Fix Plan B interrupt exception.

* Rationalise the handling/setting of meta attributes between proxy and stroom to ensure common receipt handling. Also change proxy and stroom to set/append ReceiptId, ReceiptIdPath, ReceivedTime, ReceivedTimeHistory and ReceivedPath attributes within zip meta entries.

* Proxy - Fix receipt of un-compressed data not recording the data size in bytes.

* Issue **#4856** : Fix dashboard permissions tab title.


## [v7.9-beta.9] - 2025-04-14

* Issue **#4862** : Add select * to StroomQL.

* Annotations 2.0.

* Issue **#4855** : Fix Plan B session state range.

* Improve log message when proxy HTTP forwarding fails. Now includes the time taken and the bytes sent.

* Issue **#4858** : Fix Plan B query field error.

* Issue **#4859** : Fix query on open for embedded queries.

* Issue **#4861** : Allow embedded queries to have a set page size.

* Issue **#4748** : Add user group icon to API key page.

* Issue **#4806** : Add filter/group/sort indicators to hidden field menu items.


## [v7.9-beta.8] - 2025-04-09

* Uplift BCrypt lib to 0.4.3.

* Add BCrypt as a hashing algorithm to data feed keys. Change Data feed key auth to require the header as configured by `dataFeedKeyOwnerMetaKey`. Change `hashAlgorithmId` to `hashAlgorithm` in the data feed keys json file.


## [v7.9-beta.7] - 2025-04-07

* Issue **#4831** : Fix Data Retention -> Impact Summary not showing any data.

* Issue **#4829** : Fix stuck searches.

* Issue **#4830** : Use a cache rather than sessions to maintain auth flow state to avoid creating unnecessary sessions.

* Issue **#4842** : Fix null session when doing OIDC code flow with KeyCloak.

* Issue **#4844** : Fix issue where vis parent table filters are not applied to the right data values.

* Issue **#4837** : Change the fetching of OIDC config to use jersey client instead of Apache http client. The yaml properties `appConfig.security.authentication.openId.httpClient` and `proxyConfig.security.authentication.openId.httpClient` have been removed. Configuration of the jersey client is now done using `jerseyClients.OPEN_ID.` (see https://gchq.github.io/stroom-docs/docs/install-guide/configuration/stroom-and-proxy/common-configuration/#jersey-http-client-configuration).

* Issue **#4849** : Fix the default forwarding queue config so that it retries for HTTP and not for FILE. Add the config prop `queue.queueAndRetryEnabled` to control whether forwarding is queued with retry handling or not. Add the config prop `atomicMoveEnabled` to `forwardFileDestinations` items to allow disabling of atomic file moves when using a remote file system that doesn't support atomic moves.


## [v7.9-beta.6] - 2025-04-07

* Issue **#4109** : Add `receive` config properties `x509CertificateHeader`, `x509CertificateDnHeader` and `allowedCertificateProviders` to control the use of certificates and DNs placed in the request headers by load balancers or reverse proxies that are doing the TLS termination. Header keys were previously hard coded. `allowedCertificateProviders` is an allow list of FQDN/IPs that are allowed to use the cert/DN headers.

* Add Dropwizard Metrics to proxy.

* Change proxy to use the same caching as stroom.

* Remove unused proxy config property `maxAggregateAge`. `aggregationFrequency` controls the aggregation age/frequency.

* Stroom-Proxy instances that are making remote feed status requests using an API key or token, will now need to hold the application permission `Check Receipt Status` in Stroom. This prevents anybody with an API key from checking feed statuses.

* Issue **#4312** : Add Data Feed Keys to proxy and stroom to allow their use in data receipt authentication. Replace `proxyConfig.receive.(certificateAuthenticationEnabled|tokenAuthenticationEnabled)` with `proxyConfig.receive.enabledAuthenticationTypes` that takes values: `DATA_FEED_KEY|TOKEN|CERTIFICATE` (where `TOKEN` means an oauth token or an API key). The feed status check endpoint `/api/feedStatus/v1` has been deprecated. Proxies with a version >=v7.9 should now use `/api/feedStatus/v2`.

* Replace proxy config prop `proxyConfig.eventStore.maxOpenFiles` with `proxyConfig.eventStore.openFilesCache`.

* Add optional auto-generation of the `Feed` attribute using property `proxyConfig.receive.feedNameGenerationEnabled`. This is used alongside properties `proxyConfig.receive.feedNameTemplate` (which defines a template for the auto-generated feed name using meta keys and their values) and `feedNameGenerationMandatoryHeaders` which defines the mandatory meta headers that must be present for a auto-generation of the feed name to be possible.

* Add a new _Content Templates_ screen to stroom (requires `Manage Content Templates` application permission). This screen is used to define rules for matching incoming data where the feed does not exist and creating content to process data for that feed.

* Feed status check calls made by a proxy into stroom now require the application permission `Check Receipt Status`. This is to stop anyone with an API key from discovering the feeds available in stroom. Any existing API keys used for feed status checks on proxy will need to have `Check Receipt Status` granted to the owner of the key.

* Issue **#4844** : Fix issue where vis parent table filters are not applied to the right data values.


## [v7.9-beta.5] - 2025-04-02

* Issue **#4831** : Fix Data Retention -> Impact Summary not showing any data.

* Issue **#4829** : Fix stuck searches.

* Issue **#4830** : Use a cache rather than sessions to maintain auth flow state to avoid creating unnecessary sessions.

* Issue **#4839** : Change record count filter to allow counting of records at a custom depth to match split filter.

* Issue **#4842** : Fix null session when doing OIDC code flow with KeyCloak.


## [v7.9-beta.4] - 2025-03-25

* Issue **#4828** : Fix recursive cache invalidation for index load.

* Issue **#4827** : Fix NPE when opening the Nodes screen.


## [v7.9-beta.3] - 2025-03-24

* Issue **#4733** : Fix report shutdown error.

* Issue **#4700** : Add props `forwardFileDestinations.subPathTemplate`,  `forwardFileDestinations.templatingMode` and `forwardHttpDestinations.errorSubPathTemplate` to allow templating of the file destination paths. Change the default retries to infinite. Make the following Stroom status codes go straight to the HTTP forwarder error destination: FEED_IS_NOT_DEFINED(101), FEED_IS_NOT_SET_TO_RECEIVE_DATA(110), UNEXPECTED_DATA_TYPE(120). Make the `data` directory more permissive to invalid files/directories that it finds. Remove the retry config prop `maxRetries` and replace it with `maxRetryAge` and `retryDelayGrowthFactor` to allow for a retry delay that grows with each retry. Change the file forwarder to support queueing/retry for remote file systems. Move the queue/retry config into the `queue` branch of the destination config. Fix issue of missing `.entries` files when rebooting proxy. Change the `thread` branch of the config, previous properties moved into `queue`, new properties added to control aggregation.

* Issue **#4821** : Fix wildcard replacement.

* Issue **#4823** : Fix default StreamId, EventId query columns.

* Issue **#2334** : Fix split depth for stepping to match the split filter.

* Issue **#4812** : Fix data download issue.

* Issue **#4815** : Add new `currentUser()` related functions `currentUserUuid()`, `currentUserSubjectId()`, `currentUserDisplayName()` and `currentUserFullName()`, so the annotation function can be passed a user UUID to set initial assignment.

* Issue **#4787** : Make text panes use special `__stream_id__` and `__event_id__` columns by default.

* Issue **#4768** : Fix import file path issue.

* Issue **#4767** : Improve null equality treatment in expressions.

* Issue **#4781** : Fix NPE.

* Issue **#4784** : Fix DynamicIndexingFilter values.

* Issue **#4796** : Fix the accidental deletion of rule dup stores by scheduled reports.

* Issue **#4758** : Change the way the duplicate check store works so that it can cope with values larger than 511 bytes.

* Issue **#4788** : Deep copy selection handlers when duplicating dashboard tables.

* Issue **#4791** : Fix numeric comparator in having clause.

* Issue **#4795** : Add missing properties to HTTPAppender.

* Issue **#4808** : Fix issue of proxy forwarding gzipped data inside a proxy zip when client uses header `Compression: gzip`, i.e. lower case value.


## [v7.9-beta.2] - 2025-02-21

* Issue **#4778** : Improve menu text rendering.

* Issue **#4781** : Fix NPE.

* Issue **#4784** : Fix DynamicIndexingFilter values.

* Issue **#4552** : Update dynamic index fields via the UI.

* Issue **#3921** : Make QuickFilterPredicateFactory produce an expression tree.

* Issue **#3820** : Add OR conditions to quick filter.

* Issue **#3551** : Fix character escape in quick filter.

* Issue **#4553** : Fix word boundary matching.

* Issue **#4776** : Fix column value `>`, `>=`, `<`, `<=`, filtering.


## [v7.9-beta.1] - 2025-02-12

* Issue **#4768** : Fix import file path issue.

* Issue **#4767** : Improve null equality treatment in expressions.

* Issue **#4772** : Uplift GWT to version 2.12.1.

* Issue **#4773** : Improve cookie config.

* Issue **#4692** : Add table column filtering by unique value selection.


## [v7.8-beta.14] - 2025-02-05

* Issue **#4755** : Fix missing values from pipeline structure editor.

* Issue **#4762** : Fix annotation NPE caused by null `AssignedTo`.

* Issue **#4762** : Fix annotations.

* Issue **#4754** : Fix server tasks display user related NPE.

* Issue **#4759** : Fix `Updating IDP user info...` appearing repeatedly in the logs.

* Issue **#4755** : Fix missing values from pipeline structure editor.

* Add debug to LmdbDb to log bytebuffer info.


## [v7.8-beta.13] - 2025-02-03

* Issue **#4626** : Add error messages to dashboard tables when sorting limited result data.

* Issue **#4684** : Fix focus issue for table quick filter.

* Issue **#4734** : Fix find in content multi line query behaviour.

* Issue **#4696** : Fix paging of large numbers of data sources.


## [v7.8-beta.12] - 2025-01-29

* Issue **#4707** : Fix doc ref info service.


## [v7.8-beta.11] - 2025-01-29

* Issue **#4682** : Improve Plan B filter error handling.

* Issue **#4690** : Fix meta data source fields.

* Issue **#4691** : Fix meta data source fields.

* Issue **#4701** : Fix selection filter null component.

* Issue **#4698** : Fix default S3 Appender options.

* Issue **#4686** : StroomQL now uses `stroom.ui.defaultMaxResults` if no `LIMIT` is set.

* Issue **#4696** : Fix paging of large numbers of data sources.

* Issue **#4672** : Add right click menu to copyable items.

* Issue **#4719** : Fix duplicate pipeline dependencies per user due to multiple processor filters.

* Issue **#4728** : No longer treat deleted pipeline filters as dependencies.

* Issue **#4707** : Fix doc ref info service.


## [v7.8-beta.10] - 2025-01-23

* Issue **#4713** : Fix datasource in use issue for API key DAO.

* Issue **#4714** : Fix display of disabled `RunAs` users.

* Issue **#4708** : Add copy links to stream info pane.

* Issue **#4687** : Fix dependencies NPE.

* Issue **#4717** : Fix processor filter expression fields.

* Issue **#4652** : Add user links to server tasks and search results lists.

* Issue **#4669** : Make user dependency list document column resizable.

* Issue **#4685** : Fix doc permission layout.

* Issue **#4705** : Fix conditional format fall through.

* Issue **#4671** : Remove foreign key constraints from the legacy `(app|doc)_permission` tables to `stroom_user` to fix user deletion.

* Issue **#4670** : Fix display of disabled users in multiple permission related screens.

* Issue **#4659** : Fix refresh selection changes after adding/removing users to/from groups.

* Issue **#4693** : Add the property `stroom.session.maxInactiveInterval` to control the HTTP session expiry. Defaults to `1d`.


## [v7.8-planb-beta.9] - 2025-01-21

* Change `receiptId` format to `<epoch ms>_<seq no>_<(P|S)>_<proxyId or stroom nodeName>`. `P|S` represents stroom or Proxy.

* Change stroom to also set the `receiptId` meta attribute on receipt or upload.

* Change proxy logging to still log datafeed events even if the `metaKeys` config prop is empty.

* Issue **#4695** : Change proxy to re-create the proxy ID in proxy-id.txt if the value in there does not match the required pattern.

* Fix the sleep time in UniqueIdGenerator (from 50ms to 0.1ms).


## [v7.8-beta.8] - 2025-01-10

* Fix tests that were breaking the build.

* Change the format of proxy receiptIds. They now look like `0000001736533752496_0000_TestProxy` with the first part being the epoch millis for when it was generated and the last part is the proxy ID.


## [v7.8-beta.7] - 2025-01-09

* Change default `identityProviderType` to `INTERNAL_IDP` in stroom prod config.


## [v7.8-beta.6] - 2025-01-08

* Fix config.


## [v7.8-beta.5] - 2025-01-08

* Fix config.


## [v7.8-beta.4] - 2025-01-08

* Fix config.


## [v7.8-beta.3] - 2025-01-07

* Fix config.


## [v7.8-beta.2] - 2025-01-07

* Fix config.


## [v7.8-beta.1] - 2025-01-07

* Issue **#4661** : Use Apache HttpClient.

* Issue **#4378** : Add reporting.


## [v7.7-beta.9] - 2024-12-20

* Issue **#4671** : Remove foreign key constraints from the legacy `(app|doc)_permission` tables to `stroom_user` to fix user deletion.


## [v7.7-beta.8] - 2024-12-19

* Issue **#4672** : Add context menus to table cells to copy values.

* Issue **#4632** : Fix conditional formatting rule id clash.


## [v7.7-beta.7] - 2024-12-18

* Issue **#4655** : Allow selection of no conditional style.

* Issue **#4632** : Fix conditional formatting rule id clash.

* Issue **#4657** : Fix middle pane stream delete.

* Issue **#4660** : Show pipelines and rules on processor and task panes.


## [v7.7-beta.6] - 2024-12-16

* Issue **#4632** : Fix conditional formatting styles.

* Issue **#4634** : Make icons on highlighted rows white.

* Issue **#4605** : Allow embedded queried to be re-run independently.

* Issue **#4631** : Fix simple expression wildcards.

* Issue **#4641** : Reset selections when dashboard table data changes.

* Issue **#4642** : Show current dashboard selections to help create selection filters.

* Issue **#4637** : Fix StroomQL filter in dictionary.

* Issue **#4645** : Add feature to disable notifications.

* Issue **#4594** : Various changes to the permissions screens. Added a new User screen to show all a user's permissions, api keys, and dependencies. Added links between the various permission and user screens. Improved the tables of some of the permissions screens.

* Fix `java.lang.NoClassDeffoundError: jakarta/el/ELManager` error when booting proxy.

* Fix error when creating a document as a user without `Administrator` or `Manager Users`.

* Issue **#4588** : Fix the API allowing documents to be moved with only VIEW permission. The UI requires EDIT permission. The API is now in line with that.

* Fix the `Copy As` menu item for ancestor folders that the user does not have VIEW permission on. For these cases, the `Copy As` sub menu now only displays the `Copy as name` entry.

* Change the explorer context menu to include the entries for `Dependencies` and `Dependants` if the user has at least VIEW permission. Previously required OWNER.

* Issue **#4586** : Fix error when changing filter on Document Permissions Report.

* Make account creation also create a stroom user. Make an update to an account also update the stroom user if the full name has changed.

* Fix bug in DB migration `V07_06_00_100__annotation_pre_migration_checks`.

* If you are upgrading from a previous v7.6 beta release you will need to run the following SQL. `update analytics_schema_history set checksum = '-86554219' where version = '07.06.00.405';` and `update processor_schema_history set checksum = '-175036745' where version = '07.06.00.305';`.


## [v7.7-beta.5] - 2024-12-05

* Fix `java.lang.NoClassDeffoundError: jakarta/el/ELManager` error when booting proxy.

* Issue **#4596** : Add case-sensitive value filter conditions.

* Issue **#4596** : Drive visualisations using table quick filters and selection handlers.

* Issue **#4596** : Merge include/exclude and column value filter dialogs.

* Issue **#4596** : Change conditional formatting to allow custom light and dark colours.

* Issue **#4596** : Turn off conditional formatting with user preferences.

* Issue **#4627** : Fix StroomQL function character escaping.

* Issue **#4611** : Fix problem of changes to the conditional formatting rules of a duplicated dashboard table affecting the original table. This only affected the enabled/hide properties of the formatting rule.

* Issue **#4612** : Fix stroomQL query not including all data points in the visualisation.

* Issue **#4617** : Add debug to try to diagnose issue.

* Issue **#4606** : Fix dashboard text pane that is set to Show as HTML not showing a vertical scrollbar.


## [v7.7-beta.4] - 2024-11-11

* Issue **#4601** : Add null handling and better error logging to Excel download.


## [v7.7-beta.3] - 2024-11-11

* Issue **#4597** : Fix NPE when opening the Document Permissions Report screen for a user.

* Change the code that counts expression terms and gets all fields/values from an expression tree to no longer ignore NOT operators.

* Issue **#4596** : Fix demo bugs.

* Issue **#4596** : Add table value filter dialog option.


## [v7.7-beta.2] - 2024-11-06

* Change the permission filtering to use a LinkedHashSet for children of and descendants of terms.

* Fix error when creating a document as a user without `Administrator` or `Manager Users`.

* Issue **#4588** : Fix the API allowing documents to be moved with only VIEW permission. The UI requires EDIT permission. The API is now in line with that.

* Fix the `Copy As` menu item for ancestor folders that the user does not have VIEW permission on. For these cases, the `Copy As` sub menu now only displays the `Copy as name` entry.

* Change the explorer context menu to include the entries for `Dependencies` and `Dependants` if the user has at least VIEW permission. Previously required OWNER.

* Issue **#4586** : Fix error when changing filter on Document Permissions Report.


## [v7.7-beta.1] - 2024-11-04

* Issue **#4523** : Embed queries in dashboards.

* Issue **#4504** : Add column value filters.

* Issue **#4546** : Remove redundant dashboard tab options.

* Issue **#4547** : Add selection handlers to dashboard tables to quick filter based on component selection.

* Issue **#4071** : Add preset theme compatible styles for conditional formatting.

* Issue **#4157** : Fix copy of conditional formatting rules.


## [v7.6-beta.4] - 2024-11-04

* Issue **#4550** : Fix datasource already in use issue.

* Uplift docker image JDK to `eclipse-temurin:21.0.5_11-jdk-alpine`.

* Issue **#4580** : Auto add a permission user when an account is created.

* Issue **#4582** : Show all users by default and not just ones with explicit permissions.

* Issue **#4562** : Use Zip64 compatibility mode.

* Issue **#4564** : Use expression as column name.

* Issue **#4558** : Fix query pause behaviour.

* Issue **#4549** : Fix conditional formatting fall through behaviour.

* In the Pipeline Properties pane, add copy/goto hover icons to any properties that are Documents. Add copy/goto hover links to the Pipeline, Feed and Inherited From columns in the Pipeline References pane.

* Issue **#4560** : Don't sort the list of Pipeline References (reference loaders).  Instead display them in the order they will get used in the lookup.

* Issue **#4570** : Fix error when clearing then filtering in a doc ref picker.


## [v7.6-beta.3] - 2024-10-18

* Issue **#4501** : Fix Query editor syntax highlighting.

* Add query help and editor completions for Dictionary Docs for use with `in dictionary`.

* Issue **#4487** : Fix nasty error when running a stats query with no columns.

* Issue **#4498** : Make the explorer tree Expand/Collapse All buttons respect the current Quick Filter input text.

* Issue **#4518** : Change the Stream Upload dialog to default the stream type to that of the feed.

* Issue **#4470** : On import of Feed or Index docs, replace unknown volume groups with the respective configured default volume group (or null if not configured).

* Issue **#4460** : Change the way we display functions with lots of arguments in query help and code completion popup.

* Issue **#4526** : Change Dictionary to not de-duplicate words as this is breaking JSON when used for holding SSL config in JSON form.

* Issue **#4528** : Make the Reindex Content job respond to stroom shutdown.

* Issue **#4532** : Fix Run Job Now so that it works when the job or jobNode is disabled.

* Issue **#4444** : Change the `hash()` expression function to allow the `algorithm` and `salt` arguments to be the result of functions, e.g. `hash(${field1}, concat('SHA-', ${algoLen}), ${salt})`.

* Issue **#4534** : Fix NPE in include/exclude filter.

* Issue **#4527** : Change the non-regex search syntax of _Find in Content_ to not use Lucene field based syntax so that `:` works correctly. Also change the regex search to use Lucene and improve the styling of the screen.

* Issue **#4536** : Fix NPE.

* Issue **#4539** : Improve search query logging.

* Improve the process of (re-)indexing content. It is now triggered by a user doing a content search. Users will get an error message if the index is still being initialised. The `stroom.contentIndex.enabled` property has been removed.

* Issue **#4513** : Add primary key to `doc_permission_backup_V07_05_00_005` table for MySQL Cluster support.

* Issue **#4514** : Fix HTTP 307 with calling `/api/authproxy/v1/noauth/fetchClientCredsToken`.


## [v7.6-beta.2] - 2024-10-07

* Issue **#4475** : Change `mask()` function to `period()` and add `using` to apply a function to window.

* Issue **#4341** : Allow download from query table.

* Issue **#4507** : Fix index shard permission issue.

* Issue **#4510** : Fix right click in editor pane.

* Issue **#4511** : Fix StreamId, EventId selection in query tables.

* Issue **#4485** : Improve dialog move/resize behaviour.

* Issue **#4492** : Make Lucene behave like SQL for OR(NOT()) queries.

* Issue **#4494** : Allow functions in StroomQL select, e.g. `count()`.

* Issue **#4202** : Fix default destination not being selected when you do _Save As_.

* Issue **#4475** : Add `mask()` function and deprecate `countPrevious()`.

* Issue **#4491** : Fix tab closure when deleting items in the explorer tree.

* Issue **#4502** : Fix inability to step an un-processed stream.

* Issue **#4503** : Make the enabled state of the delete/restore buttons on the stream browser depend on the user's permissions. Now they will only be enabled if the user has the require permission (i.e. DELETE/UPDATE) on at least one of the selected items.

* Issue **#4486** : Fix the `format-date` XSLT function for date strings with the day of week in, e.g. `stroom:format-date('Wed Aug 14 2024', 'E MMM dd yyyy')`.

* Issue **#4458** : Fix explorer node tags not being copied. Also fix copy/move not selecting the parent folder of the source as the default destination folder.

* Issue **#4478** : Fix boolean expression precedence in StroomQL.

* Issue **#4454** : Show the source dictionary name for each _word_ in the Dashboard List Input selection box. Add sorting and de-duplication of _words_.

* Issue **#4455** : Add Goto Document links to the Imports sub-tab of the Dictionary screen. Also add new Effective Words tab to list all the words in the dictionary that include those from its imports (and their imports).

* Issue **#4468** : Improve handling of key sequences and detection of key events from ACE editor.

* Issue **#4472** : Change the User Preferences dialog to cope with redundant stroom/editor theme names.

* Issue **#4479** : Add ability to assume role for S3.

* Issue **#4202** : Fix problems with Dashboard Extraction Pipeline picker incorrectly changing the selected pipeline.

* Change the DocRef picker so that it shows a warning icon if the selected DocRef no longer exists or the user doesn't have permission to view it.

* Change the Extraction Pipeline picker on the Index Settings screen to pre-filter on `tag:extraction`. This is configured using the property `stroom.ui.query.indexPipelineSelectorIncludedTags`.

* Change the key names in the example rule detection to remove `-`. Not sensible to encourage keys with a `-` in them as that prevents doing `values.key-1`. Also add a warning if there are multiple detection values with the same name/key (only the first will be used in each case).

* Issue **#4476** : Fix streaming analytic issue where it failed to match after seeing records with missing query fields.

* Issue **#4412** : Fix `/` key not working in quick filter text input fields.

* Issue **#4463** : Fix NPE with analytic rule email templating.

* Issue **#4146** : Fix audit events for deleting/restoring streams.

* Change the alert dialog message styling to have a max-height of 600px so long messages get a scrollbar.

* Issue **#4468** : Fix selection box keyboard selection behavior when no quick filter is visible.

* Issue **#4471** : Fix NPE with stepping filter.

* Issue **#4451** : Add S3 pipeline appender.

* Issue **#4401** : Improve content search.

* Issue **#4417** : Show stepping progress and allow termination.

* Issue **#4436** : Change the way API Keys are verified. Stroom now finds all valid api keys matching the api key prefix and compares the hash of the api key against the hash from each of the matching records. Support has also been added for using different hash algorithms.

* Issue **#4448** : Fix query refresh tooltip when not refreshing.

* Issue **#4457** : Fix ctrl+enter shortcut for query start.

* Issue **#4441** : Improve sorted column matching.

* Issue **#4449** : Reload Scheduled Query Analytics between executions.

* Issue **#4420** : Make app title dynamic.

* Issue **#4453** : Dictionaries will ignore imports if a user has no permission to read them.

* Issue **#4404** : Change the Query editor completions to be context aware, e.g. it only lists Datasources after a `from `.

* Issue **#4450** : Fix editor completion in Query editor so that it doesn't limit completions to 100. Added the property `stroom.ui.maxEditorCompletionEntries` to control the maximum number of completions items that are shown. In the event that the property is exceeded, Stroom will pre-filter the completions based on the user's input.

* Add Visualisations to the Query help and editor completions. Visualisation completion inserts a snippet containing all the data fields in the Visualisation, e.g. `TextValue(field = Field, gridSeries = Grid Series)`.

* Issue **#4436** : Change the way API Keys are verified. Stroom now finds all valid api keys matching the api key prefix and compares the hash of the api key against the hash from each of the matching records. Support has also been added for using different hash algorithms.

* Issue **#4424** : Fix alignment of _Current Tasks_ heading on the Jobs screen.

* Issue **#4422** : Don't show _Edit Schedule_ in actions menu on Jobs screen for Distributed jobs.

* Issue **#4418** : Fix missing css for `/stroom/sessionList`.

* Issue **#4435** : Fix for progress spinner getting stuck on.

* Issue **#4424** : Fix alignment of _Current Tasks_ heading on the Jobs screen.

* Issue **#4422** : Don't show _Edit Schedule_ in actions menu on Jobs screen for Distributed jobs.

* Issue **#4418** : Fix missing css for `/stroom/sessionList`.

* Issue **#4435** : Fix for progress spinner getting stuck on.

* Issue **#4437** : Fix proxy not handling input files larger than 4 GiB.

* Issue **#4069** : Reduce proxy memory usage.

* Change the hard-coded test credentials to match those in v7.2 so that a test stack with 7.0 proxy and 7.2 stroom can communicate with each other. This change has no bearing on production deployments.

* Issue **#3838** : Change ref data meta store to log a warning rather than error when meta entries are not present. This is consistent with behaviour in v7.2.

* Issue **#4426** : Add INFO message when an index shard is created.

* Issue **#4425** : Fix _Usage Date_ heading alignment on Edit Volume Group screen for both data/index volumes.

* Uplift docker image JDK to `eclipse-temurin:21.0.4_7-jdk-alpine`.

* Issue **#4416** : Allow dashboard table sorting to be changed post query.

* Issue **#4421** : Change session state XML structure.

* Issue **#4419** : Automatically unpause dashboard result components when a new search begins.

* Rename migration from `V07_04_00_005__Orphaned_Doc_Perms` to `V07_05_00_005__Orphaned_Doc_Perms`.


## [v7.6-beta.1] - 2024-08-30

* Issue **#4345** : Write analytic email notification failures to the analytic error feed.

* Issue **#4379** : Improve Stroom permission model.


## [v7.5-beta.9] - 2024-08-30

* Issue **#4383** : Add an authentication error screen to be shown when a user tries to login and there is an authentication problem or the user's account has been locked/disabled. Previously the user was re-directed to the sign-in screen even if cert auth was enabled.  Added the new property `stroom.ui.authErrorMessage` to allow setting generic HTML content to show the user when an authentication error occurs.

* Issue **#4412** : Fix `/` key not working in quick filter text input fields.

* Issue **#4400** : Fix missing styling on `sessionList` servlet.

* Fix broken description pane in the stroomQL code completion.

* Issue **#4411** : Prevent queueing too many processor tasks.

* Issue **#4408** : Fix SQL deadlock between task queuing and task physical deletion.

* Issue **#4410** : Allow over creation of processor tasks for bounded filters.

* Issue **#4403** : Fix to make elastic indexes searchable with StroomQL.

* Issue **#2897** : Fix issue of the effective stream intern pool returning incorrect sets of streams.

* Issue **#4397** : Fix search API to not require node name.

* Issue **#4394** : Fix a bug that was causing stepping filters to ignore the top level null prefixed namespace (e.g. `xmlns="event-logging:3"`. This meant all elements in the xpath had to be fully qualified.

* Issue **#4395** : Fix ClassCastException when using a stepping xpath filter that returns something other than a list of nodes, e.g. a double, long, boolean, etc.. This means you can now do something like `/Events/Event/Meta/sm:source/sm:recordNo > 2` `equals` `true`, or `/Events/Event/Meta/sm:source/sm:recordNo mod 2` `equals` `0`.

* Issue **#3960** : Migrate to Elasticsearch Java API Client.

* Issue **#4385** : Fix error when trying to change permissions on a folder with no current owner.

* Issue **#4384** : Stop logging to ERROR when stroomQL contains a malformed function.

* Issue **#4389** : Fix the Query table re-drawing too frequently.


## [v7.5-beta.8] - 2024-07-23

* Change API endpoint `/Authentication/v1/noauth/reset` from GET to POST and from a path parameter to a POST body.

* Fix various issues relating to unauthenticated servlets. Add new servlet paths e.g. `/stroom/XXX` becomes `/XXX` and `/stroom/XXX`. The latter will be removed in some future release. Notable new servlet paths are `/dashboard`, `/status`, `/swagger-ui`, `/echo`, `/debug`, `/datafeed`, `/sessionList`.

* Change `sessionList` servlet to require manage users permission.


## [v7.5-beta.7] - 2024-07-17

* Issue **#4360** : Fix quick time settings popup.

* Issue **#4357** : Fix result paging for analytic duplicate check stores.

* Issue **#4347** : Filter queryable fields for dashboard query expressions.

* Issue **#4350** : Add analytic execution history retention job.

* Issue **#4351** : Improve query event logging.

* Issue **#4357** : Fix result paging for analytic duplicate check stores.

* Issue **#4358** : Fix streaming analytic field matches.


## [v7.5-beta.6] - 2024-07-16

* Improve styling of Jobs screen so disabled jobs/nodes are greyed out.

* Add _Next Scheduled_ column to the detail pane of the Job screen.

* Add _Build Version_ and _Up Date_ columns to the Nodes screen. Also change the styling of the _Ping_ column so an enabled node with no ping stands out while a disabled node does not. Also change the row styling for disabled nodes.

* Add a Run now icon to the jobs screen to execute a job on a node immediately.

* Change the FS Volume and Index Volume tables to low-light CLOSED/INACTIVE volumes. Add tooltips to the path and last updated columns. Change the _Use%_ column to a percentage bar. Add red/green colouring to the _Full_ column values.

* Issue **#4327** : Add a Jobs pane to the Nodes screen to view jobs by node. Add linking between job nodes on the Nodes screen and the Jobs screen.


## [v7.5-beta.5] - 2024-07-11

* Fix lag on Jobs screen when changing the selected job in the top pane.

* Issue **#4348** : Fix error with `IN DICTIONARY` term in rule when the dictionary is empty/blank.

* Issue **#4305** : Don't show warnings on the Server Tasks screen for disabled nodes. Append `(Disabled)` to the node name for disabled nodes in case a disabled node is still running tasks. Changed the position of the warning icon to the right of the wrap icon so it is the right-most icon.

* Make the expand/collapse all icons on Server Tasks and Data Retention Impact Summary consistent with those on the Explorer tree.

* Add properties `httpHeadersStreamMetaDataAllowList` and `httpHeadersStreamMetaDataDenyList` to `HttpAppender` to allow fine grained control of what stream meta keys are sent to the HTTP destination as HTTP headers and `.meta` entries.

* Change `/datafeed` receipt to always set `ReceivedTime` to `now()`. If it was already set (e.g. by proxy) then that value is added to `ReceivedTimeHistory` along with the latest `ReceivedTime`. This can be used to see the latency between proxy receipt and stroom receipt. This is similar to how `ReceivedPath` works.

* Make long descriptions on the Edit Property (pipeline element property) screen wrap onto multiple lines.

* Issue **#4317** : Fix explicit `Feed` header being ignored when `compression` is `true` on `HttpAppender`.

* Issue **#4331** : Fix `java.lang.IllegalStateException` errors when using HTTPAppender.

* Issue **#4330** : Fix NPE in HTTPAppender when the destination errors.

* Improve description text for HttpAppender properties. Also add validation of some property values.

* Add property `useContentEncodingHeader` to HttpAppender to allow the user to choose between using the stroom bespoke `Compression` and HTTP standard `Content-Encoding` headers.

* When using zip compression with HttpAppender, make the .meta file also respect the various HTTP header pipeline element properties.


## [v7.5-beta.4] - 2024-06-28

* Issue **#4339** : Allow user selection of analytic duplicate columns.


## [v7.5-beta.3] - 2024-06-27

* Issue **#2126** : Add experimental state store.

* Issue **#4305** : Don't show warnings on the Server Tasks screen for disabled nodes. Append `(Disabled)` to the node name for disabled nodes in case a disabled node is still running tasks. Changed the position of the warning icon to the right of the wrap icon so it is the right-most icon.

* Make the expand/collapse all icons on Server Tasks and Data Retention Impact Summary consistent with those on the Explorer tree.

* Issue **#4334** : Popup explorer text on mouse hover.


## [v7.5-beta.2] - 2024-06-17

* Issue **#4278** : Make document deletion also delete the permission records for that document. Also run migration `V07_04_00_005__Orphaned_Doc_Perms` which will delete all document permissions (in table `doc_permission`) for docs that are not a folder, not the System doc, are not a valid doc (i.e. in the `doc` table) and are not a pipeline filter. Deleted document permission records will first be copied to a backup table `doc_permission_backup_V07_04_00_005`. 

* Change document Copy and Move to check that the user has Owner permission (or admin) on the document being copied/moved if the permissions mode is None, Destination or Combined. This is because those modes will change the permissions which is something only an Owner/admin can do.

* Fix verification of the `signer` key in the JWS headers when authentication is handled by an AWS load balancer. If you use AWS load balancers for authentication you must add the partial ARN(s) of your load balancer(s) to the property `stroom.security.authentication.openId.expectedSignerPrefixes`.

* Issue **#4313** : Add debug for authentication exceptions.

* Issue **#4322** : Fix Feed Doc Cache entry invalidation when a new feed is created.

* Add debug logging to HttpAppender.

* Issue **#4306** : Fix inability to update config props that have previously been set to a non-default and then back to a default value.

* Issue **#2897** : Add more debug logging to the reference lookup code.

* Issue **#4307** : Fix stuck search.

* Issue **#4303** : Change DSParser to catch and handle StackOverflowError as an ERROR and with a better message.

* Issue **#4281** : Fix recent items dialog throwing an error when there are favourites in the explorer tree.


## [v7.5-beta.1] - 2024-06-04

* Issue **#3989** : Improve pause behaviour in dashboards and general presentation of `busy` state throughout UI.

* Issue **#2111** : Add index assistance to find content feature.


## [v7.4-beta.16] - 2024-05-28

* Issue **#4298** : Improve duplicate management.

* Issue **#4299** : Fix exception when creating a user using the `manageUsers` CLI command.


## [v7.4-beta.15] - 2024-05-27

* Issue **#4263** : Fix HTTPAppender compression error and add additional compression algorithms.


## [v7.4-beta.14] - 2024-05-24

* Issue **#4078** : Support BZIP2 compression and make compression level configurable for FileAppender and RollingFileAppender.

* Issue **#4257** : Stop new dashboards showing params input by default.

* Issue **#4275** : Fix NPE.

* Issue **#4262** : Change the content auto import feature to allow setting the user/group that will own the content on import. Previously content was imported as the 'admin' user, but this user may not exist in all environments. Setting of the user/group is done with the following config properties `stroom.contentPackImport.importAsSubjectId` (the unique identifier for the user/group) and `stroom.contentPackImport.importAsType` (`USER` or `GROUP`).

* Issue **#4279** : Improve index shard writer cache error handling.

* Issue **#4280** : Separate LMDB data directories.

* Issue **#4263** : Fix HTTPAppender compression error and add additional compression algorithms.

* Issue **#4286** : Fix Stroom key bindings incorrectly triggering in the Ace editor find text input field.

* Issue **#4287** : Add create document key sequences to explorer context menu.

* Change the styling of the key binds in the menus.

* Add `Add Current Item to Favourites` to Main Navigation menu and `Add to Favourites` to the top level tab context menu. Both menu items add the currently open document to the user's favourites list. Operation is idempotent.

* Issue **#4289** : Make analytic duplicate checking optional and allow duplicates to be viewed/deleted from store.

* Issue **#4281** : Add debug for recent items issue.

* Issue **#4292** : Reset table selection on dashboard query.


## [v7.4-beta.13] - 2024-05-15

* Issue **#4256** : Change field types from `Long` to `Date` or `Duration` for various data sources. See GitHub issue for full details.

* Issue **#4266** : Add types `Direct Buffers Used` and `Mapped Buffers Used` to the `Memory` internal statistic. These report the bytes used by off-heap direct/mapped byte buffers. Direct buffers are used heavily in search and reference data.

* Issue **#4270** : Fix key binds like `ct` triggering when in a query term value edit field.

* Issue **#4272** : Fix function and structure editor snippets not working in Query and column expression editors.


## [v7.4-beta.12] - 2024-05-15

* Issue **#4268** : Fix NPE affecting querying.


## [v7.4-beta.11] - 2024-05-15

* Issue **#4267** : Fix logout resource for internal IdP.

* Issue **#4268** : Fix NPE affecting querying.


## [v7.4-beta.10] - 2024-05-15

* Issue **#4258** : Stop logging unnecessary error when shard deleted.

* Issue **#4260** : Add debug for deduplication checks.


## [v7.4-beta.9] - 2024-05-15

* Issue **#4264** : Stop workers requesting 0 tasks from the master node.


## [v7.4-beta.8] - 2024-05-02

* Issue **#4253** : Prevent `CancellationException` being seen when user stops query.


## [v7.4-beta.7] - 2024-05-01

* Add key binds for creating documents.

* Improve error message when you don't have permission to create an item in a folder. Now included the folder name.

* Replace _group_ with _folder_ on Create/Move/Copy document dialogues.

* Add `/stroom/analytic_store/` as a docker managed volume.

* Issue **#4250** : Fix LMDB segfault.

* Issue **#4238** : Force focus on properties quick filter.

* Issue **#4246** : Inherit time range when creating rule from query.

* Issue **#4247** : Fix notification UUID.


## [v7.4-beta.6] - 2024-04-29

* Issue **#4240** : Fix creation of new shards when non-full shards already exist for a partition.


## [v7.4-beta.5] - 2024-04-26

* Issue **#4232** : Enforce save before creating rule executors.

* Issue **#4234** : Add change handler for streaming rule cache.

* Issue **#4235** : Fix RefreshAfterWrite cache setting display to be a duration.


## [v7.4-beta.4] - 2024-04-25

* Issue **#4188** : Allow multiple analytic notifications.

* Issue **#4224** : Fix editor Format command for Text converter entities. Also fix editor Format command for Script entities (i.e. JS/JSON).

* Issue **#4228** : Fix right alignment of column headers.

* Issue **#4230** : Fix step location delimiter.


## [v7.4-beta.3] - 2024-04-24

* Fix tag filter in Edit Tags dialog not being in-sync with filter input.

* Issue **#4221** : Remove `id` column from `index_field` and change the primary key to be `(fk_index_field_source_id, name)`. Add retry logic and existing field check when adding fields to mitigate against deadlocks.

* Issue **#4218** : Fix index shard state transition.

* Issue **#4220** : Fix index shard creation.

* Issue **#4217** : Suppress expected shard write errors.

* Issue **#4215** : Fix NPE during context lookup with no context data loaded.

* Issue **#4214** : Fix index shard writer errors.

* Issue **#4203** : Fix event capture getting stuck on buttons.

* Issue **#4204** : Make `indexOf()` and `lastIndexOf()` return `-1` when not found.

* Issue **#4205** : Add `contains()` function.

* Issue **#4209** : Support `and()` function in `eval`.

* Issue **#4212** : Fix shards stuck in opening state.


## [v7.4-beta.2] - 2024-04-10

* Issue **#4161** : Fix cron schedule time UI.

* Issue **#4160** : Fix date time picker.

* Add the `ctrl+enter` key bind to dashboards to start/stop the query. Make the `ctrl+enter` key bind work on any part of the screen for Queries.

* Add various 'Goto' type sequential key binds, e.g. `gt` for 'Goto Server Tasks'.

* Change the Documentation tab to default to edit mode if the user has write permission and there is currently no documentation.

* Add `ctrl+/` and `/` key binds for focusing on the explorer tree and current tab quick filters respectively.

* Create a 'Copy As' explorer tree context menu sub-menu containing 'Copy (Name|UUID|Link) to Clipboard'.

* Issue **#4184** : Add API specification link to help menu.

* Issue **#4167** : Add templating of email subject and body in scheduled query rules. Template language is a sub-set of Jinja templating.

* Issue **#4039** : Make tick boxes in conditional formatting table clickable.

* Add help buttons to edit conditional rule dialog and add F1 keybind for opening help.

* Issue **#4119** : Set stepping location from a popup and not inline editor.

* Issue **#4187** : Fix scheduled executor stop behaviour.

* Rename DB migration script from `V07_03_00_001__job_node.sql` to `V07_04_00_005__job_node.sql`. If you are deploying this version onto an existing 7.4 version then you will need to run the following sql `delete from job_schema_history where version = '07.03.00.001';`.

* Issue **#4172** : Fix NPE when running an empty sQL query.

* Issue **#4168** : Increase width of Id column on Annotations Linked Events dialog. Also increase size of dialog and the width of the left hand split pane.

* Issue **#3998** : Clear visualisations when no data is returned rather than showing `no data`.

* Issue **#4179** : Add text input control to dashboards.

* Issue **#4151** : Prevent runaway creation of index shards when there are file system problems.

* Issue **#4035** : Fix nasty error when changing permissions of a pipeline filter.

* Issue **#4142** : Index shards now cope with field changes.

* Issue **#4183** : Fix processor filter edit.

* Issue **#4190** : Make MapDataStore sort and trim settings configurable.

* Issue **#4192** : Fix NPE when selecting Info context menu item for any folder.

* Issue **#4178** : Change the Info popup so that you can click Info for another entity and it will just update the popup content without hiding it.

* Issue **#4196** : Fix warning message when the byte buffer pool hits its threshold.

* Issue **#4175** : Fix `Batch module not found` error when sending emails.

* Issue **#4199** : Fix internal statistics error when a pipeline is owned by a user with limited privileges.

* Issue **#4195** : Fix SQL deadlock with dynamic index fields.

* Issue **#4169** : Fix index field `Positions` setting not being passed through to Lucene resulting in corrupt shards.

* Issue **#4051** : Fix search extraction for Elastic indexes.

* Issue **#4159** : Fix StroomQL vis params.

* Issue **#4149** : Format results based on real result value types rather than expected expression return type.

* Issue **#4093** : Fix value type for stored values.

* Issue **#4152** : Fix date time formatter always showing `Z` when it should show actual timezone.

* Issue **#4150** : StroomQL `vis as` keyword replaced with `show` plus added validation to parameters.


## [v7.4-beta.1] - 2024-03-12

* Issue **#3749** : Replace Stroom simple cron scheduler with standard compliant Quartz cron scheduler.

* Issue **#4041** : Improve date picker.

* Issue **#4131** : Make Stroom's standard date parsing more lenient to accept various forms of ISO 8601 zoned date times, e.g. with varying number of fractional second digits, no fractional seconds, no seconds, different offset forms ('+02', '+0200', '+02:00', etc.). Normalise `EffectiveTime` and `ReceivedTime` header values so the dates are in Stroom's standard format.


## [v7.3-beta.11] - 2024-03-01

* Issue **#4132** : Add tooltips to the copy/open hover buttons on table cell values.

* Issue **#4018** : Change the way delimited meta values are held internally. They are now comma delimited internally and when written to file.

* Change `<xsl:message>` to output `NO MESSAGE` if the element contains no text value.

* Add the key bind `ctrl-enter` to do a step refresh on the code pane of the stepper.

* Issue **#4140** : Fix selection box popup close issue.

* Issue **#4143** : Fix horizontal scroll.

* Issue **#4144** : Increase size of part no box.

* Issue **#4141** : Show selected item after expand all.


## [v7.3-beta.10] - 2024-02-28

* Issue **#4115** : Fix error when opening Recent Items screen if a recent item is in the favourites list.

* Issue **#4133** : Rollback `lmdbjava` to 0.8.2 to fix FFI issue on centos7.


## [v7.3-beta.9] - 2024-02-25

* Issue **#4107** : Add property `warnOnRemoval` to `InvalidCharFilterReader` and `warnOnReplacement` to `InvalidXMLCharFilterReaderElement` allow control of logged warnings for removal/replacement respectively.

* Fix background styling issue with markdown preview pane.

* Issue **#4108** : Add editor snippets for Data Splitter and XML Fragment Parsing.

* Issue **#4117** : Fix inconsistencies between UI and CLI when creating API Keys.

* Issue **#3890** : Fix processing info task message.

* Issue **#4128** : Fix streaming analytics.

* Issue **#4077** : Auto open query helper folders to find functions by name.

* Issue **#3971** : Allow use of date expressions with `filter` keyword.

* Issue **#4090** : Fix JSON error location reporting.


## [v7.3-beta.8] - 2024-02-17

* Issue **#4030** : Fix token validation for date expressions.

* Issue **#3991** : Add close button to query text window.

* Issue **#3337** : Improve find content feature to highlight the matches in found docs.

* Issue **#4059** : Fix stream browser not showing any streams.

* Issue **#3993** : Add recent items popup and find popup.

* Issue **#1753** : Add expand all explorer option.

* Issue **#4082** : Add XSLT function hex-to-string(hex, charsetName).

* Issue **#4085** : Fix value in error message when the ref data reference count overflows.

* Issue **#4084** : Fix double shift behaviour to show `find`.

* Issue **#4088** : Add links and copy capability to meta browser.

* Issue **#3966** : Add detailed task information for task fetching process.

* Issue **#4092** : Fix explorer expand behaviour.

* Issue **#4094** : Fix change behaviour of entity selection popup.

* Add support for `<xsl:message>some message</xsl:message>` which will log the message as `ERROR`, which is consistent with the `stroom:log()` function. If the attribute `terminate="yes"` is set, the it will log as `FATAL`, which will stop processing. You can also use a suitably named child element to set the severity, `<xsl:message><info>my message</info></xsl:message>`. Use of `terminate="yes"` trumps any severity set. Previous behaviour was to log to `stdout` and throw an exception if `terminate` is `yes`.

* Issue **#4096** : Improve wrapped table data CSS.

* Issue **#4097** : Fix Meta filter giving 'unknown field' error for fields like 'Raw Size'.

* Issue **#4101** : Expand favourites by default.

* Issue **#4091** : Fix Number column format for duration and date values. Now shows the duration in millis and millis since unix epoch respectively. Also fix lack of rounding when setting a fixed number of decimal places.

* Stop 'ADD TASKS TO QUEUE' from spamming the logs when there are no tasks to queue, i.e. a quiet system.

* Change `<xsl:message terminate="yes">` to halt processing of the current stream part immediately, in addition to logging a FATAL error. This is different from the `stroom:log('FATAL'...)` call that will log a _FATAL_ error but won't halt processing.

* Add a XML editor snippet for adding an `<xsl:element>` block with the alias `elem`.

* Issue **#4104** : Fix open document direct links.

* Issue **#4033** : Fix an infinite loop when stepping raw JSON with a FindReplaceFilter. Change the way character data is decoded in the stepper so that it is more lenient to un-decodable bytes, replacing them with '�'. Also make the reader code in the stepper respond to task termination. Add stepper error for each byte sequence that can't be decoded.

* Fix missing fatal/error/warn/info gutter icons in the Ace editor.

* Change stepper so that errors/warn/etc. with no line/col location are not included in the coloured indicator at the top right of the pane. Location agnostic errors now only feature in the log pane at the bottom.

* Change the stepper log pane so that it only provides location/pane information if the location is known. The code has been changed to allow the server to explicitly state which pane an error relates to or if it is not specific to a pane. Elements with no code pane, now default errors with location information to belonging to the Input pane rather than the Output pane as previously.

* Issue **#4054** : Fix comparison of double values, e.g. `toDouble(20000) > toDouble(125000000)`. Improve comparison/sorting logic to cope with the new Duration type and to better handle mixed type comparison.

* Issue **#4056** : Fix display value of Error type values in query/dashboard. Now shows error message prefixed with `ERR: `.

* Issue **#4055** : Fix parsing/formatting of durations in query/dashboard expressions.

* Issue **#2201** : New proxy implementation.


## [v7.3-beta.7] - 2024-01-24

* Issue **#4029** : Remove the need to specify node name for query and dashboard API.

* Issue **#3790** : Add mechanism to locate an open item in the explorer tree.

* Issue **#4043** : Duplicating filters now open a create popup rather than immediately creating a filter.

* Issue **#4058** : Improve processor filter status display.

* Issue **#3951** : Add term copy feature to stream filters.

* Issue **#4052** : Fix query helper breadcrumbs.

* Issue **#4049** : Fix term editor field selection.

* Issue **#4024** : Fix concurrency issues in CompleteableQueue that were causing searches to get stuck. Also fix similar issues in StreamEventMap.

* Issue **#3900** : Fix bug when using an expression term like `Last Commit > 2023-12-22T11:19:32.000Z` when searching the Index Shards data source.


## [v7.3-beta.6] - 2024-01-22

* Issue **#3975** : Add `cidr-to-numeric-ip-range` XSLT function.

* Issue **#4010** : Change ERROR logging to DEBUG when search requests cannot be created, e.g. when a rule has a blank or invalid query string.

* Issue **#4007** : Change the Lucene search code so that it will use the Index's default extraction pipeline if there is no extraction set on the dashboard, or in the case of StroomQL, always use the Index's default extraction pipeline as there no way to set a pipeline.

* Issue **#4021** : Fix using the `parseDuration(...)` function inside another function, e.g. `max(parseDuration(...))`.

* Issue **#4019** : Fix inability to change a date term in a filter tree. Change expression tree validation to use server-side validation so it can validate date terms correctly.

* Issue **#4029** : Change the `/api/(query|dashboard)/v1/(search|downloadSearcResults)` to accept a null `nodeName` path param, e.g. `/api/query/v1/search`.


## [v7.3-beta.5] - 2024-01-19

* Issue **#3915** : Change how multi-line attribute values (e.g. for `Files`) are written to the .meta/.mf files. Previously they were written as is so resulted in multiple lines in the file for one entry, which breaks parsing of the file. Now multi-line values are comma delimited in the file, so each entry will be on a single line. Existing meta/mf files in the system cannot be changed.

* Issue **#3981** : Fix processor records being created with the wrong pipeline UUID.

* Issue **#3979** : Fix term editor condition losing selection.

* Issue **#3979** : Fix term editor condition losing selection.

* Issue **#3891** : Add support for `not equals` `!=` term condition.

* Issue **#4014** : Fix queries hanging dur to an infinite loop when using `countGroups()` expression function.

* Change SQL migration `V07_02_00_110__api_key_legacy_data_migration.sql` to include all API keys, not just enabled ones.

* Issue **#4012** : Fix NPE when cascading perms on a folder with no current perms.

* Change V07_02_00_110__api_key_legacy_data_migration.sql to make prefix column values unique.

* Fix duplicate entries in selection boxes.

* Fix broken SQL migration script V07_02_00_110__api_key_legacy_data_migration.sql.

* Issue **#3952** : Fix handling of thread interrupts (e.g. when a ref load is triggered by a search extraction and the search is stopped) during reference data loads. Interrupted ref loads now correctly get a load state of TERMINATED. Stopped searches no longer show a warning triangle with ref errors. Terminated pipeline tasks no longer list errors/warnings for the ref load/lookups.

* Issue **#3937** : Re-work the API keys screen using GWT rather than React. Change the structure of the API keys from a JSON Web Token to a 'dumb' random string. Add the ability to enable/disable tokens. Change Stroom to store the hash of the API key rather than the key itself. Include a prefix in the API key (which is stored in Stroom) so that users can identify a key they have against the list of keys in Stroom. Legacy API Keys that are _enabled_ will be migrated from the `token` table into the new `apk-key` table. This is mostly so there is visibility of the legacy API keys. As the legacy API keys are JWTs and Stroom was not checking the `token` table for their presence, authentication relies on the JWT being valid and not expired. It is recommended to create new API keys and get users of the legacy API keys to migrate over. The new API keys allow temporary/permanent revocation so are more secure.

* Issue **#3987** : Fix duration functions by replacing `duration()` with `parseDuration()`, `parseISODuration()`, `formatDuration()` and `formatISODuration()`.

* Issue **#4004** : Remove dynamic tag from extraction pipeline selection for Views.

* Issue **#3990** : Fix refresh of explorer tree on delete of a document/folder.

* Issue **#3964** : Add XSLT function for testing whether an IP address is within the specified CIDR range.

* Issue **#3982** : Fix text colour in step filter element selector.

* Issue **#3983** : Fix colour of hamburger menu in light theme so it is visible.

* Issue **#3984** : Text editor fixed line height is now more appropriate for each font size.

* Issue **#3985** : Fix dashboard table custom date format checkbox state.

* Issue **#3950** : Rules now work with non-dynamic indexes.

* Issue **#4002** : Fix setting dirty state when switching to notification tab on rules.

* Issue **#4001** : Fix pipeline structure editor element drag issue.

* Issue **#3999** : Main pane scroll bar now displays where needed.

* Add the un-authenticated API method `/api/authproxy/v1/noauth/fetchClientCredsToken` to effectively proxy for the IDP's token endpoint to obtain an access token using the client credentials flow. The request contains the client credentials and looks like `{ "clientId": "a-client", "clientSecret": "BR9m.....KNQO" }`. The response media type is `text/plain` and contains the access token.

* Change processing user token expiry time from 1year to 10min when using internal identity provider.

* Remove the CLI command `fetch_proc_user_token` as it is now replaced by the `/authproxy/v1/noauth` API method.

* Fix issues with the refreshing of expired authentication tokens. Change the age of the service user token from 1yr to 10mins for the internal IDP.

* Issue **#3947** : Fix owner validation of document permissions when cascading permissions. Now the validation requiring a single owner is only applied to the top level document being edited. Descendant documents may have no owners or multiple owners due to legacy behaviour in stroom. If there is no change to the owner of the top level document then the descendant owners will be ignored. If _Cascade_ is set to _All_ or there is a change to the owner of the top level document and _Cascade_ is set to _Changes Only_ then the top level owner will be made the only owner of all descendants replacing any existing owners. This change also adds a confirmation dialog that shows what changes will be made to descendant documents. See the GitHub issue for examples.

* Issue **#3956** : Fix SearchRequestBuilder reuse.

* Add minor performance optimisation to the byte buffer pool used by the reference data store.

* Issue **#3953** : Fix search buffer size issue.

* Issue **#3948** : Add `and` and `or` functions.

* Issue **#3956** : Add debug to diagnose expression parsing issue.

* Issue **#3913** : Change zip data handling so unknown file extensions are treated as part of the base name, e.g. `001.unknown` gets a base name of `001.unknown` (unless we see a known extension like `001.ctx` in which case `001.unknown` gets a base name of `001`), `abc.xyz.10001` gets a base name of `abc.xyz.10001`.

* Issue **#3945** : Fix light theme colours.

* Issue **#3942** : Fix user identity in audit logging.

* Issue **#3939** : Fix issue changing editor them in user preferences.

* Issue **#3938** : Fix `lockInterruptibly` on `CompletableQueue`.


## [v7.3-beta.4] - 2023-12-04

* Issue **#3867** : Add support for Lucene 9.8.0.

* Issue **#3871** : Add support for Java 21.

* Issue **#3843** : Add distributed processing for streaming analytics.

* Uplift the versions of the following dependencies Dropwizard (major), Guice (major), FastInfoSet (patch), Kryo (minor), lmdbjava (minor), zero-allocation-hashing (minor), Hikari (minor), mysql-connector-java (patch), okhttp (minor), kafka-clients (major), Flyway (major), sqlite-jdbc (minor), jooq (minor), commons-compress (minor), FastInfoset (major), commons-csv (minor), commons-io (minor), commons-pool2 (minor), java-jwt (major), jcommander (minor), jose4j (minor), poi (major), simple-java-mail (major) and snake-yaml (major).

* Issue **#3897** : Improve UI field list performance.

* The Hessian based feed status RPC service `/remoting/remotefeedservice.rpc` has been removed as it is using the legacy `javax.servlet` dependency that is incompatible with `jakarta.servlet` that is now in use in stroom.

* Issue **#3920** : Fix rest factory use of type literals.

* Issue **#3933** : Improvements to selection box.

* Issue **#3935** : The `having` clause can now compare two computed row values.

* Remove 5s thread sleep left in the code from testing. This sleep happens when a legacy ref data stream is migrated.

* Issue **#3908** : Add config property to change search result download row limit when sorted.

* Issue **#3918** : Revert Excel heading style.

* Issue **#3926** : Add the CLI command `fetch_proc_user_token` to obtain an OIDC access token for the internal processing user. This command is useful for getting a token to be able to call stroom's APIs to manage a cluster.

* Issue **#3929** : Fix stuck searches.

* Issue **#3909** : Add multiple recipients to rule notification emails.

* Issue **#3898** : Improve task info and debug logging for `Index Shard Delete` job.

* Issue **#3903** : Fix index deadlock.

* Issue **#3901** : Fix concurrent modification exception.

* Change the tables on the following screens to initially fill the available space: Server Tasks, App Permissions, Dependencies, Pipeline Structure properties, Properties, Jobs.

* Issue **#3892** : Add a toggle button to enable/disable line wrapping in Server Tasks Name and Info cells.

* Issue **#3893** : Change Query Helper and editor code completion to insert `$field with spaces}` rather than `"field with spaces"` for field names with spaces.

* Fix Hard coded XSLT editor snippets.

* Add editor snippets for StroomQL. `ids` => `eval StreamId = first(StreamId)\neval EventId = first(EventId)`, `evt` => `eval EventId = first(EventId)` and `str` => `eval StreamId = first(StreamId)`.

* Add XSLT completion snippets for stroom identity skeletons.

* Issue **#3887** : Add option to use default extraction pipeline to dashboard table.

* Issue **#3885** : Allow pane resizing in dashboards without needing to be in design mode.

* Issue **#3889** : Allow use of dashboard datasources with just `Use` permission.

* Issue **#3883** : Fix auto creation of `admin` user when an IDP type of TEST_CREDENTIALS is used.

* Issue **#3863** : Improve layout of dashboard column expression editor, reducing the height of the expression editor box.

* Issue **#3865** : Fix issue caused by applying a filter to a missing field in StroomQL.

* Issue **#3866** : Allow `having` clauses in StroomQL to work on column id plus name.

* Issue **#3862** : Change splitter offset.

* Issue **#3840** : Fix field filtering when used with missing fields.

* Issue **#3864** : Prevent right click from selecting rows in a Dashboard table.

* Allow selection of a dashboard table row using `space` or multiple rows with `space` + `ctrl`/`shift`.

* Issue **#3854** : Fix query info prompt.

* Issue **#3857** : Fix ability to view stream as hex when the stream can be decoded to UTF8 or similar.

* Issue **#3855** : Fix table row selection.

* Issue **#3853** : Fix pipeline structure editor.

* Change the Server Tasks table to wrap the text in the Info column.

* Change the Info tool tip on the Server Tasks screen to also show the task info text.

* Issue **#3868** : Improve the server task info for reference data loads, migrations and purges. Now shows if the task is waiting for a lock and updates with the load/migration/purge progress.

* Issue **#3769** : Change the icon for Rule Set document to a shield, so as not to conflict with the Analytic Rule.

* Issue **#3814** : Add support for adding/removing tags from multiple nodes via the explorer context menu.

* Issue **#3849** : Add fix for `DateExpressionParser` NPE.

* Issue **#3846** : Increase contrast ratio of non-matches in filtered explorer tree when in light mode.

* Issue **#3845** : Fix text box not filling Query Info dialog.

* Issue **#3849** : Add debug to diagnose date parse bug.

* Issue **#3842** : Add support for `field in` to Stroom QL.

* Issue **#3836** : Add support for `field in dictionary` to Stroom QL.


## [v7.3-beta.3] - 2023-10-12

* Issue **#3830** : Add caching of S3 downloads.


## [v7.3-beta.2] - 2023-10-12

* Issue **#3830** : Add S3 Bucket and Key name patterns.

* Add/Change docker managed volumes. For stroom, make `lmdb_library` and `/tmp` tmpfs mounts. For stroom-proxy, add `sqlite_library` as a tmpfs mount, make `/tmp` a tmpfs mount and add `db` and `failures` as managed volumes. Change sqlite connection to use `sqlite_library` instead of `/tmp` for its library location.


## [v7.3-beta.1] - 2023-10-10

* Issue **#3827** : Change the new pipeline element dialog to always suggest a unique element ID.

* Issue **#3830** : Add S3 data storage option.


[Unreleased]: https://github.com/gchq/stroom/compare/v7.11-beta.10...HEAD
[v7.11-beta.10]: https://github.com/gchq/stroom/compare/v7.11-beta.9...v7.11-beta.10
[v7.11-beta.9]: https://github.com/gchq/stroom/compare/v7.11-beta.8...v7.11-beta.9
[v7.11-beta.8]: https://github.com/gchq/stroom/compare/v7.11-beta.7...v7.11-beta.8
[v7.11-beta.7]: https://github.com/gchq/stroom/compare/v7.11-beta.6...v7.11-beta.7
[v7.11-beta.6]: https://github.com/gchq/stroom/compare/v7.11-beta.5...v7.11-beta.6
[v7.11-beta.5]: https://github.com/gchq/stroom/compare/v7.11-beta.4...v7.11-beta.5
[v7.11-beta.4]: https://github.com/gchq/stroom/compare/v7.11-beta.3...v7.11-beta.4
[v7.11-beta.3]: https://github.com/gchq/stroom/compare/v7.11-beta.2...v7.11-beta.3
[v7.11-beta.2]: https://github.com/gchq/stroom/compare/v7.11-beta.1...v7.11-beta.2
[v7.11-beta.1]: https://github.com/gchq/stroom/compare/v7.10-beta.6...v7.11-beta.1
[v7.10-beta.6]: https://github.com/gchq/stroom/compare/v7.10-beta.5...v7.10-beta.6
[v7.10-beta.5]: https://github.com/gchq/stroom/compare/v7.10-beta.4...v7.10-beta.5
[v7.10-beta.4]: https://github.com/gchq/stroom/compare/v7.10-beta.3...v7.10-beta.4
[v7.10-beta.3]: https://github.com/gchq/stroom/compare/v7.10-beta.2...v7.10-beta.3
[v7.10-beta.2]: https://github.com/gchq/stroom/compare/v7.10-beta.1...v7.10-beta.2
[v7.10-beta.1]: https://github.com/gchq/stroom/compare/v7.9-beta.12...v7.10-beta.1
[v7.9-beta.12]: https://github.com/gchq/stroom/compare/v7.9-beta.11...v7.9-beta.12
[v7.9-beta.11]: https://github.com/gchq/stroom/compare/v7.9-beta.10...v7.9-beta.11
[v7.9-beta.10]: https://github.com/gchq/stroom/compare/v7.9-beta.9...v7.9-beta.10
[v7.9-beta.9]: https://github.com/gchq/stroom/compare/v7.9-beta.8...v7.9-beta.9
[v7.9-beta.8]: https://github.com/gchq/stroom/compare/v7.9-beta.7...v7.9-beta.8
[v7.9-beta.7]: https://github.com/gchq/stroom/compare/v7.9-beta.6...v7.9-beta.7
[v7.9-beta.6]: https://github.com/gchq/stroom/compare/v7.9-beta.5...v7.9-beta.6
[v7.9-beta.5]: https://github.com/gchq/stroom/compare/v7.9-beta.4...v7.9-beta.5
[v7.9-beta.4]: https://github.com/gchq/stroom/compare/v7.9-beta.3...v7.9-beta.4
[v7.9-beta.3]: https://github.com/gchq/stroom/compare/v7.9-beta.2...v7.9-beta.3
[v7.9-beta.2]: https://github.com/gchq/stroom/compare/v7.9-beta.1...v7.9-beta.2
[v7.9-beta.1]: https://github.com/gchq/stroom/compare/v7.8-beta.14...v7.9-beta.1
