package de.aethos;


import com.google.common.base.Preconditions;
import de.aethos.util.ServerController;
import de.aethos.util.TimedLog;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Locale;

@Mojo(name = "minecraft-server", defaultPhase = LifecyclePhase.VERIFY)
public class MinecraftServerMojo extends AbstractMojo {

    @Component
    private MavenProject project;
    @Parameter(property = "path", defaultValue = "server")
    private String path;
    @Parameter(property = "memory", defaultValue = "-Xmx1024M")
    private String memory;
    @Parameter(property = "gui", defaultValue = "false")
    private boolean gui;
    @Parameter(property = "reload", defaultValue = "true")
    private boolean reload;


    @Override
    public void execute() throws MojoExecutionException {
        try {

            Path path = Path.of(this.path);
            Preconditions.checkArgument(Files.isDirectory(path));
            ServerController controller = new ServerController(path, getLog());
            movePlugin();
            if (reload && controller.isRunning()) {
                controller.reload();
                return;
            }
            controller.setup();
            ProcessBuilder builder;
            Path paperJar = path.resolve("paper.jar").toAbsolutePath();
            if (gui) {
                builder = new ProcessBuilder("java", memory, "-jar", paperJar.toString());
            } else {
                builder = new ProcessBuilder("java", memory, "-jar", paperJar.toString(), "nogui");
            }

            builder.directory(path.toFile());
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);
            builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            builder.redirectInput(ProcessBuilder.Redirect.INHERIT);
            getLog().info("PaperMC server started successfully.");
            Runtime.getRuntime().addShutdownHook(new Thread(controller::stop));
            builder.start().waitFor();
        } catch (Exception e) {
            getLog().error(e);
            throw new MojoExecutionException(e);
        }
    }

    public void movePlugin() throws MojoExecutionException, IOException {
        final Path plugin = Path.of(project.getBuild().getDirectory()).resolve(project.getArtifactId() + "-" + project.getVersion() + ".jar");
        final Path pluginFolder = Path.of(path).resolve("plugins");
        final Path target = pluginFolder.resolve(plugin.getFileName());
        if (!Files.exists(pluginFolder) || !Files.isDirectory(pluginFolder)) {
            throw new MojoExecutionException("Plugin folder " + pluginFolder.toAbsolutePath() + " does not exist");
        }

        OutputStream outputStream = Files.newOutputStream(target);
        Files.copy(plugin.toAbsolutePath(), outputStream);
        getLog().info("Plugin " + plugin + " moved to " + target);
    }

    @Override
    public Log getLog() {
        return new TimedLog(super.getLog(), new SimpleDateFormat("[HH:mm:ss] ", Locale.GERMAN));
    }
}
