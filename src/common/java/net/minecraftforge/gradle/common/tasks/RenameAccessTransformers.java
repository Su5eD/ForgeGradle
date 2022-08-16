/*
 * ForgeGradle
 * Copyright (C) 2018 Forge Development LLC
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */

package net.minecraftforge.gradle.common.tasks;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import net.minecraftforge.srgutils.IMappingFile;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class RenameAccessTransformers extends DefaultTask {
    protected final Provider<Directory> workDir = getProject().getLayout().getBuildDirectory().dir(getName());

    public RenameAccessTransformers() {
        getReverse().convention(false);
        getOutput().set(workDir);
    }

    @TaskAction
    public void apply() throws IOException {
        Set<File> inputFiles = getInput().getFiles();
        Directory outputDir = getOutput().get();
        IMappingFile mapping = getMappings();
        
        if (outputDir.getAsFile().exists()) {
            FileUtils.cleanDirectory(outputDir.getAsFile());
        }
        
        for (File file : inputFiles) {
            File output = outputDir.file(file.getName()).getAsFile();
            List<String> lines = Files.readAllLines(file.toPath());
            List<String> mappedLines = renameAccessTransformer(lines, mapping, true);
            FileUtils.writeLines(output, mappedLines);
        }
    }
    
    public static List<String> renameAccessTransformer(List<String> lines, IMappingFile mapping, boolean isSrg) {
        int linePartLimit = isSrg ? 3 : 2;
        
        return lines.stream()
            .map(lineText -> {
                String line = Iterables.getFirst(Splitter.on('#').limit(2).split(lineText), "").trim();
                if (line.length() == 0) return lineText;

                List<String> parts = Lists.newArrayList(Splitter.on(" ").trimResults().split(line));
                if (parts.size() <= linePartLimit) {
                    String modifier = parts.get(0);
                    List<String> adjustedParts = isSrg ? parts.subList(1, parts.size()) : Arrays.asList(parts.get(1).split("\\."));

                    String className = adjustedParts.get(0).replace('.', '/');
                    String mappedClassName = mapping.remapClass(className);
                    String mappedMemberName = null;
                    String mappedDesc = null;

                    if (adjustedParts.size() > 1) {
                        IMappingFile.IClass cls = mapping.getClass(className);
                        if (cls != null) {
                            String nameReference = adjustedParts.get(1);
                            int parenIdx = nameReference.indexOf('(');
                            // Method
                            if (parenIdx > 0) {
                                String methodName = nameReference.substring(0, parenIdx);
                                String desc = nameReference.substring(parenIdx);

                                IMappingFile.IMethod method = cls.getMethod(methodName, desc);
                                if (method != null) methodName = method.getMapped();
                                desc = mapping.remapDescriptor(desc);

                                mappedMemberName = methodName;
                                mappedDesc = desc;
                            }
                            // Field
                            else {
                                String fieldName = nameReference;
                                IMappingFile.IField field = cls.getField(fieldName);
                                if (field != null) {
                                    fieldName = field.getMapped();
                                }
                                mappedMemberName = fieldName;
                            }
                        }
                    }

                    String mappedLine = modifier + " " + mappedClassName;
                    if (mappedMemberName != null) {
                        mappedLine += "." + mappedMemberName;
                        if (mappedDesc != null) {
                            mappedLine += mappedDesc;
                        }
                    }

                    return lineText.replace(line, mappedLine);
                }
                return lineText;
            })
            .collect(Collectors.toList());
    }
    
    private IMappingFile getMappings() throws IOException {
        IMappingFile mappings = IMappingFile.load(getSrg().get().getAsFile());
        boolean reverse = getReverse().get();
        return reverse ? mappings.reverse() : mappings;
    }
    
    @InputFiles
    public abstract ConfigurableFileCollection getInput();

    @InputFile
    public abstract RegularFileProperty getSrg();
    
    @Input
    public abstract Property<Boolean> getReverse();

    @OutputDirectory
    public abstract DirectoryProperty getOutput();
}
