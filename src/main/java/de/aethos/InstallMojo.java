package de.aethos;

import de.aethos.util.PaperDownloader;
import de.aethos.util.PluginDownloader;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Mojo(name = "install", defaultPhase = LifecyclePhase.VERIFY)
public class InstallMojo extends AbstractMojo {

    @Component
    private MavenProject project;
    @Parameter(property = "paperVersion", required = true)
    private String paperVersion;
    @Parameter(property = "path", defaultValue = "server")
    private String path;
    @Parameter(property = "dependencies", defaultValue = "true")
    private boolean dependencies;

    @Override
    public void execute() throws MojoExecutionException {
        final ExecutorService service = Executors.newSingleThreadExecutor();

        try {
            setupDirectories();
            final PaperDownloader paperDownloader = new PaperDownloader(Path.of(path), paperVersion, getLog());
            final Future<?> paperFuture = service.submit(paperDownloader::download);
            if (dependencies) {
                final Path plugins = Path.of(path).resolve("plugins");
                final PluginDownloader pluginDownloader = new PluginDownloader(plugins, getLog(), project.getDependencies(), project.getRepositories());
                pluginDownloader.download();
            }
            ServerController control = new ServerController(Path.of(path), getLog());
            control.createDefaultProperties();
            paperFuture.get();
            service.shutdown();
        } catch (Exception e) {
            getLog().error(e);
            throw new MojoExecutionException(e);
        }
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
            acceptEula();
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

    public void acceptEula() throws IOException {
        final Path eulaFile = Path.of(path).resolve("eula.txt");
        getLog().info("Accepting EULA...");
        Files.write(eulaFile, "eula=true".getBytes());
        getLog().info("EULA accepted");
    }

    @Override
    public Log getLog() {
        return new TimedLog(super.getLog(), new SimpleDateFormat("[HH:mm:ss] ", Locale.GERMAN));
    }
}
