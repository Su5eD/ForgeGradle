package net.minecraftforge.gradle.userdev.legacy;

import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.common.util.MinecraftExtension;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.mcp.MCPRepo;
import net.minecraftforge.gradle.userdev.UserDevPlugin;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;

/**
 * Extracts CSV mapping files to a folder in the build directory for further use
 * by the <code>net.minecraftforge.gradle.GradleStart.csvDir</code> property.
 * @see UserDevPlugin#runRetrogradleFixes
 */
public abstract class ExtractMappingsTask extends DefaultTask {

    public ExtractMappingsTask() {
        getInputFile().fileProvider(getProject().provider(() -> {
            MinecraftExtension minecraftExtension = getProject().getExtensions().getByType(MinecraftExtension.class);
            // create maven dependency notation for mappings
            String dep = MCPRepo.getMappingDep(minecraftExtension.getMappingChannel().get(), minecraftExtension.getMappingVersion().get());
            // download and cache the mappings
            return MavenArtifactDownloader.generate(getProject(), dep, false);
        }));
        
        // mappings are extracted to <root>/build/<taskName> by default
        getOutputDirectory().convention(getProject().getLayout().getBuildDirectory().dir(getName()));
    }

    @TaskAction
    public void run() throws IOException {
        File inputFile = getInputFile().get().getAsFile();
        File outputDirectory = getOutputDirectory().get().getAsFile();
        
        Utils.extractZip(inputFile, outputDirectory, true);
    }
    
    @InputFile
    public abstract RegularFileProperty getInputFile();
    
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();
}
