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

import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterValue;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.plugins.git.GitRevisionBuildParameters;
import hudson.plugins.git.GitSCM;
import hudson.plugins.parameterizedtrigger.BuildTrigger;
import hudson.plugins.parameterizedtrigger.BuildTriggerConfig;
import hudson.plugins.parameterizedtrigger.PredefinedBuildParameters;
import hudson.plugins.parameterizedtrigger.ResultCondition;
import hudson.tasks.Shell;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;
import org.jvnet.hudson.test.HudsonTestCase;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.MockBuilder;

/**
 * Unit tests
 */
public class BlockingJobsMonitorDownstreamTest extends HudsonTestCase {

    /**
     * One test for all for faster execution
     *
     * @throws Exception
     */
    public void testTriggered() throws Exception {
        // clear queue from preceding tests
//        JenkinsRule j = new JenkinsRule();
        Jenkins jj = Jenkins.getInstance();
        jj.setNumExecutors(5);
        jj.getQueue().clear();
        jj.setNodes(jj.getNodes());
        Computer master = jj.getComputer("");
        // init slave
//        LabelAtom label = new LabelAtom("label");
//        DumbSlave slave = this.createSlave(label);
//        SlaveComputer c = slave.getComputer();
//        c.connect(false).get(); // wait until it's connected
//        if (c.isOffline()) {
//            fail("Slave failed to go online: " + c.getLog());
//        }
//        jj.setQuietPeriod(5);
       //main project
        FreeStyleProject mainProject = createFreeStyleProject("somemainjob");
        mainProject.setConcurrentBuild(true);
//        mainProject.setBlockBuildWhenDownstreamBuilding(true);
        mainProject.getBuildersList().add(new MockBuilder(Result.SUCCESS));
        //downstream
        String blockingJobName = "blockingJob";
        String blockingEnvVar = "branchName";
        FreeStyleProject blockingProject = this.createFreeStyleProject(blockingJobName);
//        blockingProject.setAssignedLabel(label);
        blockingProject.setQuietPeriod(3);
        blockingProject.setConcurrentBuild(true);

        Shell shell = new Shell("sleep 22");
        blockingProject.getBuildersList().add(shell);
        ArrayList<ParameterValue> values = new ArrayList<ParameterValue>();

        // Add one standard downstream job:
        String properties = "blockingEnvVars=branchName\n" // 123 in multibytes
                + "branchName=someBlockingBranch\n";    // "KEY" in multibytes

        File dir = File.createTempFile("gitinit", ".test");
        dir.delete();

        Git.init().setDirectory(dir).call();
        Repository repository = FileRepositoryBuilder.create(new File(dir.getAbsolutePath(), ".git"));
        Git git = new Git(repository);

        // create the file
        File myfile = new File(repository.getDirectory().getParent(), "testfile");
        myfile.createNewFile();
        // run the add
        git.add().addFilepattern("testfile").call();
        // and then commit the changes
        git.commit().setMessage("Added testfile").call();

        mainProject.setScm(new GitSCM(dir.getPath()));
        mainProject.getPublishersList().add(
                new BuildTrigger(new BuildTriggerConfig(blockingJobName, ResultCondition.SUCCESS,
                                new GitRevisionBuildParameters(true), new PredefinedBuildParameters(properties))));

        CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        mainProject.getBuildersList().add(builder);
//        BlockingBehaviour neverFail = new BlockingBehaviour("never", "never", "never");
//        List<AbstractBuildParameters> buildParameters = new ArrayList<AbstractBuildParameters>();
//        buildParameters.add(new CurrentBuildParameters());
//        buildParameters.add(new GitRevisionBuildParameters(true));
//        buildParameters.add(new PredefinedBuildParameters(properties));
//        TriggerBuilder trigger  =new TriggerBuilder(new BlockableBuildTriggerConfig(blockingJobName, neverFail, buildParameters));
//        mainProject.getBuildersList().add(trigger);
        jj.rebuildDependencyGraph();
        //START MAIN
        Future<FreeStyleBuild> future = mainProject.scheduleBuild2(0);
        FreeStyleBuild mBuild = future.get();

        List BuildableItems = jj.getQueue().getBuildableItems();
        Queue.Item q1 = jj.getQueue().getItem(blockingProject);
        assertNotNull("q1 should be in queue (quiet period): " + getLog(mBuild), q1);
        // wait until blocking job started
        int i = 1;
//        while (!master.getExecutors().get(1).isBusy()){
//            TimeUnit.SECONDS.sleep(1);
//            System.out.println("waiting task: " + i++);
//        }

        //blockingBranch
        // create the file
        File myfile1 = new File(repository.getDirectory().getParent(), "testfile");
        myfile1.createNewFile();
        // run the add
        git.add().addFilepattern("testfile").call();
        // and then commit the changes
        git.commit().setMessage("Added testfile").call();
        TimeUnit.SECONDS.sleep(2);
//        Future<FreeStyleBuild> future2 = mainProject.scheduleBuild2(3);
        FreeStyleBuild main2 = mainProject.scheduleBuild2(0).get();
        TimeUnit.SECONDS.sleep(1);
        Queue.Item q2 = hudson.getQueue().getItem(blockingProject);

        i = 1;
        assertNotNull("q2 should be in queue (quiet period): " + getLog(main2), q2);
        BlockingJobsMonitor blockingBranchMonitorUsingDifferentHashes = new BlockingJobsMonitor(blockingJobName, null);
        //assert
        assertNull(blockingBranchMonitorUsingDifferentHashes.getBlockingJob(q2));

        TimeUnit.SECONDS.sleep(2);
        Future<FreeStyleBuild> future3 = mainProject.scheduleBuild2(0);
        FreeStyleBuild main3 = mainProject.scheduleBuild2(3).get();
        TimeUnit.SECONDS.sleep(1);
        Queue.Item q3 = hudson.getQueue().getItem(blockingProject);
        assertNotNull("q2 should be in queue (quiet period): " + getLog(main3), q3);

        AbstractProject projectQ3 = (AbstractProject) q3.task;

        System.out.println("is building: " + projectQ3.getLastBuild().isBuilding());
        TimeUnit.SECONDS.sleep(5);
        BlockingJobsMonitor blockingBranchMonitorUsingFullName = new BlockingJobsMonitor(blockingJobName, null);
        assertEquals(blockingJobName, blockingBranchMonitorUsingFullName.getBlockingJob(q3).getDisplayName());
        // wait until blocking job stopped

        while (projectQ3.getLastBuild().isBuilding()) {
            TimeUnit.SECONDS.sleep(1);
        }
        assertNull(blockingBranchMonitorUsingFullName.getBlockingJob(null));
    }
}
