package net.minecraftforge.gradle.userdev.legacy;

import net.minecraftforge.gradle.common.util.MinecraftExtension;
import net.minecraftforge.gradle.userdev.UserDevExtension;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.bundling.Jar;

/**
 * Modifies run tasks' classpath before they run.
 * {@link TaskContainer#whenTaskAdded(Action)} runs before the task is configured,
 * therefore any changes made to the classpath there would be overwritten.
 * Instead, we declare a task dependency on prepareRuns and modify each 
 * run task's classpath just before it runs.
 */
public abstract class FixClasspathTask extends DefaultTask {

    @TaskAction
    public void run() {
        final Project project = getProject();
        final TaskContainer tasks = project.getTasks();
        // Find the output jar file
        final Jar jarTask = (Jar) tasks.getByName("jar");
        // Get the classpath and create a composite with the found jar file
        final ConfigurableFileCollection classpath = project.files(
                project.getConfigurations().getByName("runtimeClasspath"),
                jarTask.getArchiveFile().get().getAsFile()
        );
        // Get the Userdev plugin for the run configs
        final MinecraftExtension minecraftExtension = (MinecraftExtension) project.getExtensions().getByName(UserDevExtension.EXTENSION_NAME);

        // For all defined run configurations..
        minecraftExtension.getRuns().stream()
                // Get the Task created by it
                .map(run -> tasks.getByName(run.getTaskName()))
                // Filter for those that define a JavaExec run
                .filter(t -> t instanceof JavaExec)
                // Set the run's classpath to the composite we made
                .forEach(t -> ((JavaExec) t).setClasspath(classpath));
    }
}
