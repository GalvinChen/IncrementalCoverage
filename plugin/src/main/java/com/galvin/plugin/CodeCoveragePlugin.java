package com.galvin.plugin;

import com.android.build.gradle.AppExtension;
import com.android.build.gradle.AppPlugin;
import com.galvin.plugin.task.CodeCoverageReportTask;
import com.galvin.plugin.task.PullExecutionFileTask;
import com.galvin.plugin.transform.JacocoInstrumentTransform;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class CodeCoveragePlugin implements Plugin<Project> {

    private Logger mLogger = Logging.getLogger(CodeCoveragePlugin.class);

    @Override
    public void apply(Project project) {
        try {
            int status = Runtime.getRuntime()
                    .exec("git status", null, project.getRootDir())
                    .getInputStream().read();
            if (status == -1) {
                mLogger.warn(project.getRootDir().getAbsolutePath() + " is not a git repository");
                mLogger.warn("the repository can not apply incremental code coverage plugin");
                return;
            }
        } catch (IOException e) {
            mLogger.error(e.getMessage(), e);
        }
        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(@NotNull Project target) {
                if (target.getPlugins().hasPlugin(AppPlugin.class)) {
                    AppExtension appExtension = target.getExtensions().findByType(AppExtension.class);
                    if (appExtension != null) {
                        appExtension.registerTransform(new JacocoInstrumentTransform(target));
                        // implementation 'org.jacoco:org.jacoco.agent:0.8.4:runtime'
                        target.getDependencies().add("implementation",
                                "org.jacoco:org.jacoco.agent:0.8.4:runtime");

                        TaskProvider<PullExecutionFileTask> pullExecutionFileTaskTaskProvider =
                                target.getTasks().register("pullExecutionFile", PullExecutionFileTask.class);
                        target.getTasks().register("generateCoverageReport", CodeCoverageReportTask.class)
                                .configure(codeCoverageReportTask -> {
                                    codeCoverageReportTask.dependsOn(pullExecutionFileTaskTaskProvider.get().getPath());
                                });
                    } else {
                        mLogger.error("the appExtension of " + target.getName() + " is null");
                    }
                }
            }
        });

        project.getExtensions().create("coverage", CoverageExtension.class, project.getObjects());
    }
}