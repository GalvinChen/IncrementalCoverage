package com.galvin.plugin.task;

import com.android.build.gradle.AppExtension;
import com.galvin.plugin.CodeCoveragePlugin;
import com.galvin.plugin.PluginConstants;

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
import org.jacoco.core.analysis.CoverageNodeImpl;
import org.jacoco.core.analysis.IBundleCoverage;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.analysis.ICoverageNode;
import org.jacoco.core.analysis.IMethodCoverage;
import org.jacoco.core.analysis.IPackageCoverage;
import org.jacoco.core.analysis.ISourceFileCoverage;
import org.jacoco.core.internal.analysis.CounterImpl;
import org.jacoco.core.internal.analysis.LineImpl;
import org.jacoco.core.internal.analysis.SourceNodeImpl;
import org.jacoco.core.tools.ExecFileLoader;
import org.jacoco.report.DirectorySourceFileLocator;
import org.jacoco.report.FileMultiReportOutput;
import org.jacoco.report.IReportVisitor;
import org.jacoco.report.html.HTMLFormatter;
import org.jacoco.report.internal.html.resources.Styles;
import org.jacoco.report.internal.html.table.BarColumn;
import org.jacoco.report.internal.html.table.LabelColumn;
import org.jacoco.report.internal.html.table.PercentageColumn;
import org.jacoco.report.internal.html.table.Table;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    @Internal
    private Map<String, List<Integer>> mChangedLinesPerFile;

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
            File diffNameToLinesFile = new File(getProject().getBuildDir() + PluginConstants.INPUT_CHANGED_LINES_PER_FILE);
            if (diffNameToLinesFile.exists()) {
                ObjectInputStream objectInputStream = new ObjectInputStream(new FileInputStream(diffNameToLinesFile));
                mChangedLinesPerFile = (HashMap<String, List<Integer>>) objectInputStream.readObject();
            }
            create();
        } catch (IOException | ClassNotFoundException e) {
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

        try {
            if (mChangedLinesPerFile != null && mChangedLinesPerFile.size() > 0) {
                for (Map.Entry<String, List<Integer>> entry : mChangedLinesPerFile.entrySet()) {
                    mLogger.lifecycle("changedFile:" + entry.getKey());
                    mLogger.lifecycle("changedLines:" + entry.getValue());
                }
                modifyCoverageForOnlyShowChangedLines(bundleCoverage);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            mLogger.error(e.getMessage(), e);
        }

        createReport(bundleCoverage);
    }

    private void modifyCoverageForOnlyShowChangedLines(IBundleCoverage bundleCoverage) throws NoSuchFieldException,
            IllegalAccessException {
        Field lineCounterField = CoverageNodeImpl.class.getDeclaredField("lineCounter");
        lineCounterField.setAccessible(true);
        Field linesField = SourceNodeImpl.class.getDeclaredField("lines");
        linesField.setAccessible(true);

        int bundleMissed = 0;
        int bundleTotal = 0;
        Collection<IPackageCoverage> packages = bundleCoverage.getPackages();
        for (IPackageCoverage packageCoverage : packages) {
            Collection<ISourceFileCoverage> sourceFilesCoverage = packageCoverage.getSourceFiles();
            int packageMissed = 0;
            int packageTotal = 0;
            for (ISourceFileCoverage sourceFileCoverage : sourceFilesCoverage) {
                String fileName = sourceFileCoverage.getName();
                if (!mChangedLinesPerFile.containsKey(fileName)) {
                    continue;
                }
                List<Integer> lineNumbers = mChangedLinesPerFile.get(fileName);

                LineImpl[] lines = (LineImpl[]) linesField.get(sourceFileCoverage);
                int sourceFileMissed = 0;
                for (int i = sourceFileCoverage.getFirstLine(); i <= sourceFileCoverage.getLastLine(); i++) {
                    if (!lineNumbers.contains(i)) {
                        lines[i - sourceFileCoverage.getFirstLine()] = LineImpl.EMPTY;
                    } else {
                        LineImpl line = lines[i - sourceFileCoverage.getFirstLine()];
                        if (line.getStatus() == ICounter.NOT_COVERED) {
                            sourceFileMissed++;
                        }
                    }
                }
                lineCounterField.set(sourceFileCoverage, CounterImpl.getInstance(sourceFileMissed, lineNumbers.size() - sourceFileMissed));
                packageMissed += sourceFileMissed;
                packageTotal += lineNumbers.size();
            }
            lineCounterField.set(packageCoverage, CounterImpl.getInstance(packageMissed, packageTotal - packageMissed));
            bundleMissed += packageMissed;
            bundleTotal += packageTotal;

            Collection<IClassCoverage> classes = packageCoverage.getClasses();
            for (IClassCoverage classCoverage : classes) {
                String fileName = classCoverage.getSourceFileName();
                if (!mChangedLinesPerFile.containsKey(fileName)) {
                    continue;
                }
                List<Integer> lineNumbers = mChangedLinesPerFile.get(fileName);

                Collection<IMethodCoverage> methods = classCoverage.getMethods();
                int classMissed = 0;
                int classTotal = 0;
                for (IMethodCoverage methodCoverage : methods) {
                    LineImpl[] lines = (LineImpl[]) linesField.get(methodCoverage);
                    if (lines == null) continue;
                    int methodMissed = 0;
                    int methodTotal = 0;
                    for (int i = methodCoverage.getFirstLine(); i <= methodCoverage.getLastLine(); i++) {
                        if (!lineNumbers.contains(i)) {
                            lines[i - methodCoverage.getFirstLine()] = LineImpl.EMPTY;
                        } else {
                            LineImpl line = lines[i - methodCoverage.getFirstLine()];
                            if (line != null) {
                                methodTotal++;
                                if (line.getStatus() == ICounter.NOT_COVERED) {
                                    methodMissed++;
                                }
                            }
                        }
                    }
                    lineCounterField.set(methodCoverage, CounterImpl.getInstance(methodMissed, methodTotal - methodMissed));
                    classMissed += methodMissed;
                    classTotal += methodTotal;
                }
                lineCounterField.set(classCoverage, CounterImpl.getInstance(classMissed, classTotal - classMissed));
            }
        }
        lineCounterField.set(bundleCoverage, CounterImpl.getInstance(bundleMissed, bundleTotal - bundleMissed));
    }

    private void createReport(final IBundleCoverage bundleCoverage)
            throws IOException {

        // Create a concrete report visitor based on some supplied
        // configuration. In this case we use the defaults
        final HTMLFormatter htmlFormatter = new HTMLFormatter() {
            @Override
            public Table getTable() {
                if (mChangedLinesPerFile == null || mChangedLinesPerFile.size() == 0) {
                    return super.getTable();
                }
                final Table t = new Table();
                t.add("Element", null, new LabelColumn(), false);
                t.add("Missed Lines", Styles.BAR, new BarColumn(ICoverageNode.CounterEntity.LINE,
                        Locale.getDefault()), true);
                t.add("Cov.", Styles.CTR2,
                        new PercentageColumn(ICoverageNode.CounterEntity.LINE, Locale.getDefault()), false);
                return t;
            }
        };
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
