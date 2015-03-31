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
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.queue.SubTask;
import hudson.util.RunList;
import java.io.IOException;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
    private List<String> blockingEnvVars;

    /**
     * Constructor using the job configuration entry for blocking jobs
     *
     * @param blockingJobs line feed separated list og blocking jobs
     */
    public BlockingJobsMonitor(String blockingJobs, String blockingEnvVarRaw) {
        if (StringUtils.isNotBlank(blockingJobs)) {
            this.blockingJobs = Arrays.asList(blockingJobs.split("\n"));
        }
        if (StringUtils.isNotBlank(blockingEnvVarRaw)) {
            this.blockingEnvVars = Arrays.asList(blockingEnvVarRaw.split("\n"));
        }
    }

    /**
     * Returns the name of the first blocking job. If not found, it returns null.
     *
     * @param item The queue item for which we are checking whether it can run or not. or null if we are not checking a
     * job from the queue (currently only used by testing).
     * @return the name of the first blocking job.
     * @throws java.lang.InterruptedException
     */
    public SubTask getBlockingJob(Queue.Item item) throws InterruptedException {
        if (this.blockingJobs == null && this.blockingEnvVars == null) {
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
                    if (this.blockingEnvVars != null && !this.blockingEnvVars.isEmpty()) {
                        for (String envVar : this.blockingEnvVars) {
                            Map<String, String> itemParamsMap = new HashMap<String, String>();
                            if (item == null) {
                                throw new InterruptedException("Queue.Item item; nothing to test");
                            }
                            Logger.getLogger(BlockingJobsMonitor.class.getName()).log(Level.INFO, "item name: " + item.toString());
                            for (ParametersAction pa : item.getActions(ParametersAction.class)) {
                                for (ParameterValue p : pa.getParameters()) {
                                    String valueRaw = p.getShortDescription();
                                    if (StringUtils.isNotBlank(valueRaw) && valueRaw.contains("=")) {
                                        String[] keyValue = valueRaw.split("=", -1);
                                        String value = keyValue[1].replaceAll("^\'|\'$", "");
                                        if (value != null) {
                                            itemParamsMap.put(p.getName(), value);
                                        }
                                    }
                                }
                            }
                            Logger.getLogger(BlockingJobsMonitor.class.getName()).log(Level.INFO, "itemParamsMap: " + itemParamsMap);
                            try {
                                String blockingVarValue = itemParamsMap.get(envVar);
                                RunList buildList = project.getBuilds();

                                for (Iterator it = buildList.iterator(); it.hasNext();) {
                                    Run build = (Run) it.next();
                                    Logger.getLogger(BlockingJobsMonitor.class.getName()).log(Level.INFO, "build Status: " + build.isBuilding() + ", " + build.toString());
                                    if (build.isBuilding()) {
                                        EnvVars environment = build.getEnvironment(TaskListener.NULL);
                                        String existingEnvVarValue = environment.get(envVar);
                                        if (existingEnvVarValue != null && blockingVarValue != null && existingEnvVarValue.matches(blockingVarValue)) {
                                            Logger.getLogger(BlockingJobsMonitor.class.getName()).log(Level.INFO, "envVar:{0} with existingEnvVarValue:{1}  - LOCKED", new Object[]{envVar, existingEnvVarValue});
                                            return subTask;
                                        }
                                    }
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
            if (item != buildableItem && this.blockingJobs != null && !this.blockingJobs.isEmpty()) {
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
