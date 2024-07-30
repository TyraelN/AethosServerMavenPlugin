package de.aethos;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Repository;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

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
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            setupDirectory();
            setupPaper();
            movePlugin();
            if (dependencies) {
                setupDependencies();
            }
            startServer();
        } catch (Exception e) {
            getLog().error(e);
            throw new MojoExecutionException(e);
        }
    }

    private void setupDependencies() throws IOException {
        Path plugins = Path.of(path).resolve("plugins");
        if (!Files.exists(plugins)) {
            Files.createDirectory(plugins);
        }
        getLog().info("Installing dependencies...");
        for (Dependency dependency : project.getDependencies()) {
            if ("provided".equals(dependency.getScope())) {

                downloadPlugin(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion());
            }
        }
    }

    public void downloadPlugin(String group, String artifact, String version) throws IOException {
        for (Repository repository : project.getRepositories()) {
            downloadPlugin(repository.getUrl(), group, artifact, version);
        }
    }

    public void downloadPlugin(String repository, String group, String artifact, String version) throws IOException {
        if (!repository.endsWith("/")) {
            repository = repository + "/";
        }
        String urlStr = repository + group.replace(".", "/") + "/" + artifact + "/" + version + "/" + artifact + "-" + version + ".jar";
        URL url = new URL(urlStr);

        HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
        httpURLConnection.setRequestMethod("HEAD");
        httpURLConnection.connect();

        int responseCode = httpURLConnection.getResponseCode();
        if (!(responseCode == HttpURLConnection.HTTP_OK)) {
            return;
        }

        httpURLConnection.disconnect();


        if (!isPlugin(url.openStream())) {
            getLog().info("Skipping dependency. " + artifact + " is not a Plugin");
            return;
        }
        InputStream inputStream = url.openStream();
        getLog().info("Downloading : " + url + " ...");
        Path destination = Path.of(path).resolve("plugins").resolve(artifact + "-" + version + ".jar");
        if (!Files.exists(destination)) {
            FileUtils.createParentDirectories(destination.toFile());
            Files.createFile(destination);
        }

        ReadableByteChannel rbc = Channels.newChannel(inputStream);
        FileOutputStream fos = new FileOutputStream(destination.toString());
        fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        rbc.close();
        fos.close();
        getLog().info("Installation finished: " + destination.getFileName());

    }

    public boolean isPlugin(InputStream stream) throws IOException {
        JarEntry entry;
        JarInputStream jis = new JarInputStream(stream);
        while ((entry = jis.getNextJarEntry()) != null) {
            if (entry.getName().equals("plugin.yml") || entry.getName().equals("paper-plugin.yml")) {
                return true;
            }
        }
        return false;
    }

    public void movePlugin() throws MojoExecutionException, IOException {
        Path plugin = Path.of(project.getBuild().getDirectory()).resolve(project.getArtifactId() + "-" + project.getVersion() + ".jar");
        Path pluginFolder = Path.of(path).resolve("plugins");
        Path target = pluginFolder.resolve(plugin.getFileName());

        if (!Files.exists(pluginFolder) || !Files.isDirectory(pluginFolder)) {
            throw new MojoExecutionException("Plugin folder " + pluginFolder.toAbsolutePath() + " does not exist");
        }
        Files.copy(plugin.toAbsolutePath(), target.toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING);
        getLog().info("Plugin " + plugin + " moved to " + target);

    }


    public void setupDirectory() throws IOException, MojoExecutionException {
        Path server = Path.of(path);
        if (Files.exists(server)) {
            if (Files.isDirectory(server)) {
                getLog().info("Server already exists");
                getLog().warn("Check if Server directory is up to date");
            } else {
                throw new MojoExecutionException(server + " is not an directory");
            }
        } else {
            Files.createDirectory(server);
            getLog().info("Server directory created at: " + server);
        }
    }

    public void setupPaper() throws IOException, MojoExecutionException {
        Path server = Path.of(path);

        Path paperJar = server.resolve("paper.jar");
        if (Files.exists(paperJar)) {
            getLog().info("paper.jar already exists");
        } else {
            setEULATrue(server);
            URL url = getPaperUrl(paperVersion);
            try (InputStream in = url.openStream()) {
                Files.copy(in, paperJar);
            }
        }
        Path plugins = Path.of(path).resolve("plugins");
        if (Files.exists(plugins)) {
            getLog().info("Plugin directory already exists");
        } else {
            Files.createDirectory(plugins);
            getLog().info("Plugin directory created at: " + plugins);
        }
        getLog().info("Server setup completed successfully.");
    }

    public void setEULATrue(Path dir) throws IOException {
        // Accept EULA
        getLog().info("Accepting EULA...");
        Path eulaFile = dir.resolve("eula.txt");
        Files.write(eulaFile, "eula=true".getBytes());
        getLog().info("EULA accepted");
    }

    public URL getPaperUrl(String version) throws IOException, MojoExecutionException {
        getLog().info("Fetching latest build number for PaperMC version " + version + "...");

        String paperMcApiUrl = "https://papermc.io/api/v2/projects/paper/versions/" + version;
        HttpURLConnection connection = (HttpURLConnection) new URL(paperMcApiUrl).openConnection();
        connection.setRequestMethod("GET");

        if (connection.getResponseCode() != 200) {
            throw new MojoExecutionException("Failed to fetch PaperMC version info. HTTP error code: " + connection.getResponseCode());
        }

        InputStream responseStream = connection.getInputStream();
        JsonObject responseObject = JsonParser.parseReader(new InputStreamReader(responseStream)).getAsJsonObject();
        JsonArray builds = responseObject.getAsJsonArray("builds");
        int latestBuild = builds.get(builds.size() - 1).getAsInt();

        getLog().info("Downloading PaperMC build " + latestBuild + " for version " + version + "...");
        return new URL(paperMcApiUrl + "/builds/" + latestBuild + "/downloads/paper-" + version + "-" + latestBuild + ".jar");
    }

    public void startServer() throws IOException {
        getLog().info("Starting PaperMC server...");
        Path serverDir = Path.of(path);
        Path paperJar = serverDir.resolve("paper.jar").toAbsolutePath();
        ProcessBuilder processBuilder;
        if (gui) {
            processBuilder = new ProcessBuilder("java", memory, "-jar", paperJar.toString());
        } else {
            processBuilder = new ProcessBuilder("java", memory, "-jar", paperJar.toString(), "nogui");
        }
        processBuilder.directory(serverDir.toFile());
        processBuilder.inheritIO().start();
        getLog().info("PaperMC server started successfully.");
    }

}
