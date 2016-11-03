-----
Copyright 2016 Crown Copyright

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at: http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
-----

Stream Store Assumptions
======================== 

Deletes within the stream store delete database records and leave the associated
files on the file system.  The application only uses the stream task API to 
perform a delete and this action deletes all related information.

So for example deleting an index task will delete the associated translation 
task and the related raw and processed streams records.  Re-processing deletes 
the target stream data and allows the stream task to be rerun.

The important fact to understand is that no deletes occur on the file system 
and this is handled later with the stream tasks.

We no longer try and rectify old locked raw or processed data.  The GUI will be 
enhanced to allow all streams that are locked to be selected for deletion.

A stream can have the following status:
LOCKED - Currently being written and is not typically visible any other process
UNLOCKED - Complete written stream

Status ARCHIVED and DELETED are no longer supported.

The stream task's will ensure that:
old (2+ days) files on the file system that are not recognised or the stream 
has been deleted are removed.
Streams that have passed their retention period are removed

In detail the following tasks exist

FileSystemCleanTask
===================

This task runs on each node and processes the volumes owned by that node.
On a daily basis looks for files with a last modified older than 2 days and 
checks that a stream exists in the stream store for them.  
It does this by directory scanning, for every directory the database is queried 
and then matches not in the database are removed.

We also build our FileVolumeIndex as we do this.  This record allows the 
application to show the status via the UI.

StreamArchiveTask
=================

This tasks runs for the cluster and looks for unlocked streams older than their 
stream retention period.  We use the same delete stream task API the GUI calls 
to delete the data.   
