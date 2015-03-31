build-blocker-plugin
====================

Jenkins build blocker plugin

This plugin uses a QueueTaskDispatcher to block scheduled jobs from starting as long as configured other jobs are running.

These other jobs can be configured in a textarea where each line represents a regular expression of the job names that should block this job from starting.


environment variable support added
========================

* new field was added *Blocking environment variable list*
* jenkins **job** is locked if the environment variable say *ENV_VAR* exist in scheduled or running job

e.g. git branch lock
  environment variable **branchName** or **GIT_BRANCH** matches injected by *Git pull request builder plugin*
