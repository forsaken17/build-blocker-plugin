/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Frederik Fromm
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.buildblocker;

import hudson.EnvVars;
import hudson.matrix.MatrixConfiguration;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Queue;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.queue.SubTask;
import java.io.IOException;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class represents a monitor that checks all running jobs if one of their names matches with one of the given
 * blocking job's regular expressions.
 *
 * The first hit returns the blocking job's name.
 */
public class BlockingJobsMonitor {

    /**
     * the list of regular expressions from the job configuration
     */
    private List<String> blockingJobs;
    /**
     * the list of regular expressions from the job configuration
     */
    private List<String> blockingBranches;

    /**
     * Constructor using the job configuration entry for blocking jobs
     *
     * @param blockingJobs line feed separated list og blocking jobs
     */
    public BlockingJobsMonitor(String blockingJobs, String blockingBranches) {
        if (StringUtils.isNotBlank(blockingJobs)) {
            this.blockingJobs = Arrays.asList(blockingJobs.split("\n"));
        }
        if (StringUtils.isNotBlank(blockingBranches)) {
            this.blockingBranches = Arrays.asList(blockingBranches.split("\n"));
        }
    }

    /**
     * Returns the name of the first blocking job. If not found, it returns null.
     *
     * @param item The queue item for which we are checking whether it can run or not. or null if we are not checking a
     * job from the queue (currently only used by testing).
     * @return the name of the first blocking job.
     */
    public SubTask getBlockingJob(Queue.Item item) {
        if (this.blockingJobs == null && this.blockingBranches == null) {
            return null;
        }

        Computer[] computers = Jenkins.getInstance().getComputers();

        for (Computer computer : computers) {
            List<Executor> executors = computer.getExecutors();

            executors.addAll(computer.getOneOffExecutors());

            for (Executor executor : executors) {
                if (executor.isBusy()) {
                    Queue.Executable currentExecutable = executor.getCurrentExecutable();

                    SubTask subTask = currentExecutable.getParent();
                    Queue.Task task = subTask.getOwnerTask();

                    if (task instanceof MatrixConfiguration) {
                        task = ((MatrixConfiguration) task).getParent();
                    }

                    AbstractProject project = (AbstractProject) task;
                    if (this.blockingJobs != null && !this.blockingJobs.isEmpty()) {
                        for (String blockingJob : this.blockingJobs) {
                            try {
                                if (project.getFullName().matches(blockingJob)) {
                                    return subTask;
                                }
                            } catch (java.util.regex.PatternSyntaxException pse) {
                                return null;
                            }
                        }
                    }
                    if (this.blockingBranches != null && !this.blockingBranches.isEmpty()) {
                        for (String blockingBranch : this.blockingBranches) {
                            try {
                                Run build = project.getLastBuild();
                                EnvVars environment = build.getEnvironment(TaskListener.NULL);
                                String gitBranch = environment.get("GIT_BRANCH");
                                String branchName = environment.get("branchName");
                                if (branchName != null && branchName.matches(blockingBranch)) {
                                    Logger.getLogger(BlockingJobsMonitor.class.getName()).log(Level.INFO, "branchName: "+branchName+" - LOCKED");
                                    return subTask;
                                }
                                if (gitBranch != null && gitBranch.matches(blockingBranch)) {
                                    Logger.getLogger(BlockingJobsMonitor.class.getName()).log(Level.INFO, "GIT_BRANCH: "+gitBranch+" - LOCKED");
                                    return subTask;
                                }
                            } catch (java.util.regex.PatternSyntaxException pse) {
                                return null;
                            } catch (IOException ex) {
                                Logger.getLogger(BlockingJobsMonitor.class.getName()).log(Level.SEVERE, null, ex);
                                return null;
                            } catch (InterruptedException ex) {
                                Logger.getLogger(BlockingJobsMonitor.class.getName()).log(Level.SEVERE, null, ex);
                                return null;
                            }
                        }
                    }
                }
            }
        }

        /**
         * check the list of items that have already been approved for building (but haven't actually started yet)
         */
        List<Queue.BuildableItem> buildableItems
                = Jenkins.getInstance().getQueue().getBuildableItems();

        for (Queue.BuildableItem buildableItem : buildableItems) {
            if (item != buildableItem) {
                for (String blockingJob : this.blockingJobs) {
                    AbstractProject project = (AbstractProject) buildableItem.task;
                    if (project.getFullName().matches(blockingJob)) {
                        return buildableItem.task;
                    }
                }
            }
        }

        return null;
    }
}
