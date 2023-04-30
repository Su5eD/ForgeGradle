/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.userdev.transforms;

import net.minecraftforge.gradle.common.util.AccessTransformerUtils;
import net.minecraftforge.srgutils.IMappingFile;
import org.apache.commons.io.FilenameUtils;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.internal.jvm.Jvm;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public abstract class AccessTransformAction implements TransformAction<AccessTransformAction.Parameters> {
    private static final Logger LOGGER = Logging.getLogger(AccessTransformAction.class);

    @PathSensitive(PathSensitivity.NAME_ONLY)
    @InputArtifact
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Override
    public void transform(TransformOutputs outputs) {
        final Parameters parameters = getParameters();

        final ConfigurableFileCollection accessTransformers = parameters.getAccessTransformers();
        final ConfigurableFileCollection classpath = parameters.getClasspath();
        final String mainClass = parameters.getMainClass().get();
        final String atArgumentPrefix = parameters.getATArgumentPrefix().get();
        final IMappingFile mappings;
        try {
            mappings = IMappingFile.load(parameters.getMappings().get().getAsFile());
        } catch (IOException e) {
            LOGGER.error("Failed to load mappings file", e);
            throw new RuntimeException(e);
        }

        final List<String> originalArguments = parameters.getArguments().get();
        if (!(originalArguments.contains("{input}") && originalArguments.contains("{output}"))) {
            throw new IllegalArgumentException("Arguments must have one {input} and one {output}");
        }

        final File inputArtifact = getInputArtifact().get().getAsFile();
        if (!inputArtifact.isFile()) {
            throw new IllegalArgumentException("Input artifact must be a file");
        }
        final String inputFileName = FilenameUtils.getBaseName(inputArtifact.getName());

        // If we don't have access transformer files, do nothing and pass in the input as the output
        if (accessTransformers.isEmpty()) {
            outputs.file(inputArtifact);
            return;
        }
        final File outputArtifact = outputs.file(inputFileName + "-accesstransformed.jar");

        final List<String> arguments = new ArrayList<>();
        for (String originalArgument : originalArguments) {
            if (originalArgument.equals("{input}")) {
                arguments.add(inputArtifact.getAbsolutePath());
            } else if (originalArgument.equals("{output}")) {
                arguments.add(outputArtifact.getAbsolutePath());
            } else {
                arguments.add(originalArgument);
            }
        }

        for (File accessTransformerFile : accessTransformers) {
            try {
                File tempATFile = File.createTempFile("forgegradle-accesstranformer", ".cfg");
                tempATFile.deleteOnExit();
                remapATFile(mappings, accessTransformerFile.toPath(), tempATFile.toPath());

                arguments.add(atArgumentPrefix);
                arguments.add(tempATFile.getAbsolutePath());
            } catch (IOException e) {
                LOGGER.error("Failed to handle temporary access transformer file", e);
                throw new RuntimeException(e);
            }
        }

        getExecOperations().javaexec(spec -> {
            spec.setExecutable(Jvm.current().getJavaExecutable().getAbsolutePath());
            spec.setArgs(arguments);
            spec.setClasspath(classpath);
            spec.getMainClass().set(mainClass);
        }).rethrowFailure().assertNormalExitValue();
    }

    private void remapATFile(IMappingFile mappings, Path input, Path output) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(input);
                BufferedWriter writer = Files.newBufferedWriter(output)) {
            while (true) {
                String line = reader.readLine();
                if (line == null)
                    break;

                writer.write(AccessTransformerUtils.remapATLine(mappings, line));
                writer.newLine();
            }
        }
    }

    public interface Parameters extends TransformParameters {
        @InputFiles
        ConfigurableFileCollection getAccessTransformers();

        @InputFiles
        ConfigurableFileCollection getClasspath();

        @InputFile
        RegularFileProperty getMappings();

        @Input
        Property<String> getATArgumentPrefix();

        @Input
        ListProperty<String> getArguments();

        @Input
        Property<String> getMainClass();
    }
}
