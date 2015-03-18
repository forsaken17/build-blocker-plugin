build-blocker-plugin
====================

Jenkins build blocker plugin

This plugin uses a QueueTaskDispatcher to block scheduled jobs from starting as long as configured other jobs are running.

These other jobs can be configured in a textarea where each line represents a regular expression of the job names that should block this job from starting.


git branch support added
========================

* new field was added *Blocking environment variable list*
* jenkins **job** is locked if the environment variable **branchName** or **GIT_BRANCH** matches injected with *EnvInject Plugin*
