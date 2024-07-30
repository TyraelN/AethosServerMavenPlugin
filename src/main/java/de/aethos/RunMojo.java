package de.aethos;


import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Mojo(name = "run", defaultPhase = LifecyclePhase.VERIFY)
public class RunMojo extends AbstractMojo {
    @Component
    private MavenProject project;
    @Parameter(property = "paperVersion", required = true)
    private String paperVersion;
    @Parameter(property = "path", defaultValue = "server")
    private String path;
    @Parameter(property = "memory", defaultValue = "-Xmx1024M")
    private String memory;
    @Parameter(property = "gui", defaultValue = "false")
    private boolean gui;
    @Parameter(property = "dependencies", defaultValue = "true")
    private boolean dependencies;

    @Override
    public void execute() throws MojoExecutionException {
        final ExecutorService service = Executors.newSingleThreadExecutor();
        try {
            setupDirectories();
            final PaperDownloader paperDownloader = new PaperDownloader(Path.of(path), paperVersion, getLog());
            final Future<?> paperFuture = service.submit(paperDownloader::download);
            movePlugin();
            if (dependencies) {
                final Path plugins = Path.of(path).resolve("plugins");
                final PluginDownloader pluginDownloader = new PluginDownloader(plugins, getLog(), project.getDependencies(), project.getRepositories());
                pluginDownloader.download();
            }
            paperFuture.get();
            service.shutdown();
            final ServerRunner runner = new ServerRunner(Path.of(path), gui, memory, getLog());
            runner.startServer();
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
        Files.copy(plugin.toAbsolutePath(), target.toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING);
        getLog().info("Plugin " + plugin + " moved to " + target);
    }


    public void setupDirectories() throws IOException, MojoExecutionException {
        final Path server = Path.of(path);
        if (!Files.exists(server)) {
            Files.createDirectory(server);
            getLog().info("Server directory created at: " + server);
        }
        if (!Files.isDirectory(server)) {
            throw new MojoExecutionException(server + " is not an directory");
        } else {
            setEULATrue(server);
        }
        final Path plugins = server.resolve("plugins");
        if (!Files.exists(plugins)) {
            Files.createDirectory(plugins);
            getLog().info("Plugin directory created at: " + plugins);
        }
        if (!Files.isDirectory(plugins)) {
            throw new MojoExecutionException(plugins + " is not an directory");
        }
    }

    public void setEULATrue(Path dir) throws IOException {
        getLog().info("Accepting EULA...");
        final Path eulaFile = dir.resolve("eula.txt");
        Files.write(eulaFile, "eula=true".getBytes());
        getLog().info("EULA accepted");
    }


    @Override
    public Log getLog() {
        return new TimedLog(super.getLog(), new SimpleDateFormat("[HH:mm:ss] ", Locale.GERMAN));
    }
}
