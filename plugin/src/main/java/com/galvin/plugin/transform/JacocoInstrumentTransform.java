package com.galvin.plugin.transform;

import com.android.build.api.transform.Format;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.utils.FileUtils;
import com.galvin.plugin.CoverageExtension;
import com.galvin.plugin.PluginConstants;
import com.galvin.plugin.utils.DiffUtils;
import com.google.common.io.Files;

import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.jacoco.core.instr.Instrumenter;
import org.jacoco.core.runtime.OfflineInstrumentationAccessGenerator;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class JacocoInstrumentTransform extends Transform {

    private Project project;
    private Logger logger;
    private File rootDir;
    private Map<String, List<Integer>> mChangedLinesPerFile;

    public JacocoInstrumentTransform(Project project) {
        this.project = project;
        logger = Logging.getLogger(JacocoInstrumentTransform.class);
        rootDir = project.getRootDir();
    }

    @Override
    public String getName() {
        return "CodeCoverage";
    }

    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.PROJECT_ONLY;
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    @Override
    public void transform(TransformInvocation transformInvocation) {
        String baseline = "HEAD~1";
        String revision = "HEAD";
        Properties properties = new Properties();
        File localProperties = new File(project.getRootDir(), "local.properties");
        if (localProperties.exists()) {
            try {
                properties.load(new FileReader(localProperties));
                if (properties.containsKey("baseline")) {
                    baseline = properties.getProperty("baseline");
                }
                if (properties.containsKey("revision")) {
                    revision = properties.getProperty("revision");
                }
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }

        CoverageExtension coverageExtension = project.getExtensions().findByType(CoverageExtension.class);
        if (coverageExtension != null) {
            if (coverageExtension.getBaseline().isPresent()) {
                baseline = coverageExtension.getBaseline().get();
            }
            if (coverageExtension.getRevision().isPresent()) {
                revision = coverageExtension.getRevision().get();
            }
            logger.info("CoverageExtension: " + baseline + "->" + revision);
        }
        String command = "git diff " + baseline + " " + revision + " -U0";
        try {
            InputStream inputStream = Runtime.getRuntime()
                    .exec(command, null, rootDir)
                    .getInputStream();
            mChangedLinesPerFile = DiffUtils.findChangedLinesPerFile(inputStream);
            for (Map.Entry<String, List<Integer>> entry : mChangedLinesPerFile.entrySet()) {
                logger.info("changedFile:" + entry.getKey());
                logger.info("changedLines:" + entry.getValue());
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }

        File classesDir = new File(project.getBuildDir() + PluginConstants.INPUT_CLASSES_DIRECTORY);
        if (!classesDir.exists()) {
            classesDir.mkdirs();
        }

        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        Collection<TransformInput> inputs = transformInvocation.getInputs();
        for (TransformInput input : inputs) {
            input.getDirectoryInputs().forEach(directoryInput -> {
                File inputDir = directoryInput.getFile();
                File outputDir = outputProvider.getContentLocation(directoryInput.getName(), directoryInput.getContentTypes(),
                        directoryInput.getScopes(), Format.DIRECTORY);
                if (mChangedLinesPerFile.keySet().isEmpty()) {
                    logger.warn("There are no java or kotlin files have changed");
                    try {
                        FileUtils.copyDirectory(inputDir, outputDir);
                    } catch (IOException e) {
                        logger.error(e.getMessage(), e);
                    }
                } else {
                    List<File> toInstrumentClassFiles = new ArrayList<>();
                    for (File file : FileUtils.getAllFiles(inputDir)) {
                        try {
                            InputStream inputStream =
                                    Files.asByteSource(file).openBufferedStream();
                            // filter changed source files
                            ClassReader classReader = new ClassReader(inputStream);
                            ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM7) {
                                @Override
                                public void visitSource(String source, String debug) {
                                    if (mChangedLinesPerFile.containsKey(source)) {
                                        toInstrumentClassFiles.add(file);
                                    }
                                }
                            };
                            classReader.accept(classVisitor, ClassReader.SKIP_CODE);
                        } catch (IOException e) {
                            logger.error(e.getMessage(), e);
                        }
                    }
                    for (File file : FileUtils.getAllFiles(inputDir)) {
                        try {
                            File outputFile =
                                    new File(outputDir, FileUtils.relativePath(file, inputDir));
                            Files.createParentDirs(outputFile);
                            if (file.getName().endsWith(".class") && !file.getName().contains("BuildConfig")
                                    && toInstrumentClassFiles.contains(file)) {
                                // copy to dir build/reports/instrumental_classes for generating code coverage report
                                File toReportClassFile = new File(classesDir, FileUtils.relativePath(file, inputDir));
                                Files.createParentDirs(toReportClassFile);
                                FileUtils.copyFile(file, toReportClassFile);

                                Instrumenter instrumenter =
                                        new Instrumenter(new OfflineInstrumentationAccessGenerator());
                                InputStream inputStream =
                                        Files.asByteSource(file).openBufferedStream();
                                byte[] instrumented =
                                        instrumenter.instrument(inputStream, file.toString());
                                Files.write(instrumented, outputFile);
                            } else {
                                FileUtils.copyFile(file, outputFile);
                            }
                        } catch (IOException e) {
                            logger.error(e.getMessage(), e);
                        }
                    }
                }
            });

            input.getJarInputs().forEach(jarInput -> {
                File dest = outputProvider.getContentLocation(jarInput.getName(), jarInput.getContentTypes(),
                        jarInput.getScopes(), Format.JAR);
                try {
                    FileUtils.copyFile(jarInput.getFile(), dest);
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                }
            });
        }
    }
}
