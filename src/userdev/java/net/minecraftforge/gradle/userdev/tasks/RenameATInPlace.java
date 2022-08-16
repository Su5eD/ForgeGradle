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

package net.minecraftforge.gradle.userdev.tasks;

import net.minecraftforge.gradle.common.tasks.RenameAccessTransformers;
import net.minecraftforge.srgutils.IMappingFile;
import org.apache.commons.io.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public abstract class RenameATInPlace extends DefaultTask {

    @TaskAction
    public void apply() throws IOException {
        File jar = getJar().get().getAsFile();
        byte[] bytes = Files.readAllBytes(jar.toPath());
        IMappingFile mapping = IMappingFile.load(getSrg().get().getAsFile());

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes));
             ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(jar))) {
            for (ZipEntry entry; (entry = zis.getNextEntry()) != null; ) {
                ZipEntry newEntry = new ZipEntry(entry.getName());

                zout.putNextEntry(newEntry);
                if (entry.getName().endsWith("_at.cfg")) {
                    List<String> lines = IOUtils.readLines(zis, StandardCharsets.UTF_8);
                    List<String> mappedLines = RenameAccessTransformers.renameAccessTransformer(lines, mapping, false);
                    IOUtils.writeLines(mappedLines, null, zout, StandardCharsets.UTF_8);
                } else {
                    IOUtils.copy(zis, zout);
                }
                zout.closeEntry();

                zis.closeEntry();
            }
        }
    }

    @InputFile
    public abstract RegularFileProperty getJar();

    @InputFile
    public abstract RegularFileProperty getSrg();
}
