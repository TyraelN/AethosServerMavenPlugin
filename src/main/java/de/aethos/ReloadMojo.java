package de.aethos;

import de.aethos.util.ServerController;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Mojo(name = "reload", defaultPhase = LifecyclePhase.VERIFY)
public class ReloadMojo extends AbstractMojo {

    @Component
    MavenProject project;
    @Parameter(property = "path", defaultValue = "server")
    private String path;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            movePlugin();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        new ServerController(Path.of(path), getLog()).sendCommand("reload confirm");
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
}
