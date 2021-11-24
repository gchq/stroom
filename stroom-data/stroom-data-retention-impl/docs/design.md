# Data Retention Job Design

The Data Retention Job is concerned with logically deleting (or retaining) meta records according to a set of data retention rules.
Rules have precedence, with a higher rule number meaning the rule is more important.
All rules need to be applied to all meta records in precedence order.
The first matching rule applies.
If a rule matches, no other rules will be tested/applied.
If no rules match then that record will not be deleted as the default is for records to be retained forever.
Rules either have a retention period (or age) specified as an amount and a unit, e.g. days, months, years, etc, or their retention is forever.

## Process Periods

The process of logically deleting meta records that have exceeded the retention period set by the matching rule is broken down into time periods.
The timeline from epoch to now is split up into chunk delimited by the distinct retention periods of the active rules.
For example, with the following active rules:

* Rule 1 -  10 Years - Feed == SPECIAL_DATA
* Rule 2 -  3 Months - Feed == INTERNAL_LOGS
* Rule 3 -  5 Years - Feed == *
* Rule 4 -  Default forever rule

The processing will be split into the following time periods:
* Epoch => 10 years ago
* 10 years ago => 5 years ago
* 5 years ago => 3 months ago

All rules have a retention greater than 3 months so the period 3 months ago => now() is ignored and all that data is therefore retained.

Each time period will be processed in turn starting from the shortest retenion period and working towards the epoch.
For each period the action of each rule will be determined for that period, e.g. for the 5 years ago => 3 months ago period:

* 5 years ago => 3 months ago
  * Rule 1 -  10 Years - RETAIN
  * Rule 2 -  3 Months - DELETE
  * Rule 3 -  5 Years - RETAIN
  * Rule 4 -  Default forever rule - RETAIN
* 10 years ago => 5 years ago
  * Rule 1 -  10 Years - RETAIN
  * Rule 2 -  3 Months - DELETE
  * Rule 3 -  5 Years - DELETE
  * Rule 4 -  Default forever rule - RETAIN
* Epoch => 10 years ago
  * Rule 1 -  10 Years - DELETE
  * Rule 2 -  3 Months - DELETE
  * Rule 3 -  5 Years - DELETE
  * Rule 4 -  Default forever rule - DELETE

The active rules are converted into an ordered list of rule actions for the period being processed with the rule action containing the DELETE/RETAIN outcome.

## Now

All processing is done using a fixed value for the current time.
At the start of processing the current time is evaluated and this fixed time is then used for all subsequent calculations, e.g. when turning retention periods into instants.
This ensures consistency in the calculations.

## Evaluating the rules

The evaluation of the rule actions is done in a single SQL statement that is built up from the active rules and the time period being processed.
The rule actions are converted into a SQL CASE statement with each rule action having a WHEN clause with the predicate being formed from the rule expression and evaluating to either true or false (i.e. delete or retain) acording to the rule action outcome.
The result is a potentially very large and complex SQL UPDATE statement.
To help MySQL utilise indexes, each rule's expression is added as an OR condition.

If the time period is large and the job has not been run for a while then the update statement is likely to impact very many rows.
The updated rows will be locked but it is likely that next-key locks will be used so uneffected rows will also be locked.
To lessen the impact on the system each time period is split into smaller chunks using a configurable batch size.
A select query is run to establish the min and max meta create times for the required batch size.
The update SQL is then run using this batch time slice and the selection of a new time slice and the update are repeated until the end of the period is reached.

## Tracking
Once the rules have been run over all the data then (assuming the rules have not changed) there is no need to scan all the data on next run.

Tracker records are used to keep track of what time the last run happened so we can offset the periods by that amount on the next run.
The trackers are keyed by the rule retention period, e.g. "5 Years" which means if the process is terminated mid way through then all periods that have already been processed will have their tracker position saved.


Using the example rules from above, if the tracker for each retention age had a last run time of 1d ago then the periods become:

* 10 years & 1 day ago => 10 years ago
* 5 years & 1 day ago => 5 years ago
* 3 months & 1 day ago => 3 months ago

This means the processing periods have all been reduced down to 1 day in length which will reduce the query time.

The following diagrams illustrate what is happening on a time line for the 10 yrs ago => 5 yrs ago period.
The first diagram shows the first run with no tracker in place.

```text
now() == 20200101
             
--|----------------------|--
  |                      | 
  |<-----processing----->| 
  |                      | 
 10yrs ago               5yrs ago
 20100101                20150101

 processing == 5yr span
```

The second diagram shows what happens when the job is run exactly one day later with the tracker position used from the previous run.

```text
now() == 20200102

--------|----------------|-----|--
        |                |     | 
        |                |<-p->| 
        |                |     | 
       10yrs ago         |     5yrs ago
       20100102          |     20150102
                         |
                        5yrs & 1 day ago
                        20150101
 processing (p) == 1day span
```
(Not to scale)

## Classes

The following are the main classes for the data retention processing:

* `stroom.data.retention.impl.DataRetentionPolicyExecutor`
* `stroom.meta.impl.db.MetaDaoImpl`
* `stroom.meta.impl.db.MetaRetentionTrackerDaoImpl`
