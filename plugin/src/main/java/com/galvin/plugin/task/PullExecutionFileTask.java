package com.galvin.plugin.task;

import com.galvin.plugin.PluginConstants;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;

// adb pull /storage/emulated/0/coverage.exec ./app/build/reports/code_coverage
public class PullExecutionFileTask extends DefaultTask {
    @TaskAction
    void pullExecutionFile() {
        Project project = getProject();
        String command = "adb pull " + PluginConstants.DEVICE_EXECUTION_DATA_FILE + " " + getProject().getBuildDir() +
                PluginConstants.INPUT_EXECUTION_DATA_DIRECTORY;
        try {
            int value = Runtime.getRuntime().exec(command, null, getProject().getRootDir()).waitFor();
            if (value == 0) {
                CodeCoverageReportTask codeCoverageReportTask = (CodeCoverageReportTask) getProject().getTasks().getByName("generateCoverageReport");
                codeCoverageReportTask.getExecutionDataFile().set(new File(getProject().getBuildDir() + PluginConstants.INPUT_EXECUTION_DATA_FILE));

                File classesDir = new File(project.getBuildDir() + PluginConstants.INPUT_CLASSES_DIRECTORY);
                File reportDir = new File(project.getBuildDir() + PluginConstants.OUTPUT_REPORT_DIRECTORY);
                if (!reportDir.exists()) {
                    reportDir.mkdirs();
                }

                codeCoverageReportTask.getClassesDirectory().set(classesDir);
                codeCoverageReportTask.getReportDirectory().set(reportDir);
            }
        } catch (IOException e) {
            getProject().getLogger().error("no connected devices");
            getProject().getLogger().error("exec command error for " + command, e.getMessage(), e);
        } catch (InterruptedException e) {
            getProject().getLogger().error(command + " has been interrupted", e.getMessage(), e);
        }
    }
}
