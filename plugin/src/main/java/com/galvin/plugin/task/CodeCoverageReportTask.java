package com.galvin.plugin.task;

import com.android.build.gradle.AppExtension;
import com.galvin.plugin.CodeCoveragePlugin;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IBundleCoverage;
import org.jacoco.core.tools.ExecFileLoader;
import org.jacoco.report.DirectorySourceFileLocator;
import org.jacoco.report.FileMultiReportOutput;
import org.jacoco.report.IReportVisitor;
import org.jacoco.report.html.HTMLFormatter;

import java.io.File;
import java.io.IOException;

public class CodeCoverageReportTask extends DefaultTask {

    private Logger mLogger = Logging.getLogger(CodeCoveragePlugin.class);

    @InputFile
    final RegularFileProperty executionDataFile = getProject().getObjects().fileProperty();

    @InputDirectory
    final DirectoryProperty classesDirectory = getProject().getObjects().directoryProperty();

    @OutputDirectory
    final DirectoryProperty reportDirectory = getProject().getObjects().directoryProperty();

    public RegularFileProperty getExecutionDataFile() {
        return executionDataFile;
    }

    public DirectoryProperty getClassesDirectory() {
        return classesDirectory;
    }

    public DirectoryProperty getReportDirectory() {
        return reportDirectory;
    }

    @Internal
    private ExecFileLoader execFileLoader;

    @Internal
    private File sourceDirectory;

    @TaskAction
    void generateReport() {
        AppExtension appExtension = getProject().getExtensions().findByType(AppExtension.class);
        if (appExtension != null) {
            sourceDirectory = appExtension.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).getJava()
                    .getSourceDirectoryTrees().get(0).getDir();
        }
        mLogger.lifecycle("executionDataFile:" + executionDataFile.get().getAsFile().getPath());
        mLogger.lifecycle("classesDirectory" + classesDirectory.get().getAsFile().getPath());
        mLogger.lifecycle("sourceDirectory" + sourceDirectory.getPath());
        mLogger.lifecycle("reportDirectory" + reportDirectory.get().getAsFile().getPath());
        try {
            create();
        } catch (IOException e) {
            mLogger.error(e.getMessage(), e);
        }
        File indexHtml = new File(reportDirectory.get().getAsFile(), "index.html");
        if (!indexHtml.exists()) return;
        try {
            Runtime.getRuntime().exec("open " + indexHtml);
        } catch (IOException e) {
            mLogger.error(e.getMessage(), e);
        }
    }

    /**
     * Create the report.
     *
     * @throws IOException
     */
    public void create() throws IOException {

        // Read the jacoco.exec file. Multiple data files could be merged
        // at this point
        loadExecutionData();

        // Run the structure analyzer on a single class folder to build up
        // the coverage model. The process would be similar if your classes
        // were in a jar file. Typically you would create a bundle for each
        // class folder and each jar you want in your report. If you have
        // more than one bundle you will need to add a grouping node to your
        // report
        final IBundleCoverage bundleCoverage = analyzeStructure();

        createReport(bundleCoverage);

    }

    private void createReport(final IBundleCoverage bundleCoverage)
            throws IOException {

        // Create a concrete report visitor based on some supplied
        // configuration. In this case we use the defaults
        final HTMLFormatter htmlFormatter = new HTMLFormatter();
        final IReportVisitor visitor = htmlFormatter
                .createVisitor(new FileMultiReportOutput(reportDirectory.getAsFile().get()));

        // Initialize the report with all of the execution and session
        // information. At this point the report doesn't know about the
        // structure of the report being created
        visitor.visitInfo(execFileLoader.getSessionInfoStore().getInfos(),
                execFileLoader.getExecutionDataStore().getContents());

        // Populate the report structure with the bundle coverage information.
        // Call visitGroup if you need groups in your report.
        visitor.visitBundle(bundleCoverage,
                new DirectorySourceFileLocator(sourceDirectory, "utf-8", 4));

        // Signal end of structure information to allow report to write all
        // information out
        visitor.visitEnd();

    }

    private void loadExecutionData() throws IOException {
        execFileLoader = new ExecFileLoader();
        execFileLoader.load(executionDataFile.getAsFile().get());
    }

    private IBundleCoverage analyzeStructure() throws IOException {
        final CoverageBuilder coverageBuilder = new CoverageBuilder();
        final Analyzer analyzer = new Analyzer(
                execFileLoader.getExecutionDataStore(), coverageBuilder);

        analyzer.analyzeAll(classesDirectory.getAsFile().get());

        return coverageBuilder.getBundle(getProject().getName());
    }
}
