package de.aethos.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;


public class PaperDownloader {
    private static final String API_URL = "https://api.papermc.io/v2/projects/paper/versions/${MINECRAFT_VERSION}/builds";
    private final Path dir;
    private final String version;
    private final Log log;

    public PaperDownloader(Path dir, String version, Log log) {
        this.dir = dir;
        this.version = version;
        this.log = log;
    }

    public void download() {
        final Path paperJar = dir.resolve("paper.jar");
        if (Files.exists(paperJar)) {
            getLog().info("paper.jar already exists");
        } else {
            try {
                URL url = fetchDOwnloadURL(version);
                try (InputStream in = url.openStream()) {
                    Files.copy(in, paperJar);
                }
            } catch (MojoExecutionException | IOException e) {
                getLog().error(e);
            }
        }
        getLog().info("Server setup completed successfully.");
    }

    public URL fetchDOwnloadURL(String version) throws IOException, MojoExecutionException {
        getLog().info("Fetching latest build number for PaperMC version " + version);
        final String string = API_URL.replace("${MINECRAFT_VERSION}", version);
        final URL buildsURL = new URL(string);
        getLog().info("PaperMC API URL: " + buildsURL);
        final URLConnection connection = buildsURL.openConnection();
        if (connection instanceof HttpURLConnection httpsConnection) {
            httpsConnection.setRequestMethod("GET");
            if (httpsConnection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new MojoExecutionException("Failed to fetch PaperMC version info. HTTP error code: " + httpsConnection.getResponseCode());
            }
            final InputStream responseStream = httpsConnection.getInputStream();
            final InputStreamReader inputStream = new InputStreamReader(responseStream);
            final JsonObject responseObject = JsonParser.parseReader(inputStream).getAsJsonObject();
            final JsonArray builds = responseObject.getAsJsonArray("builds");
            final JsonObject object = builds.get(builds.size() - 1).getAsJsonObject();
            final JsonElement element = object.get("build");
            final int latestBuild = element.getAsInt();
            getLog().info("Downloading PaperMC build " + latestBuild + " for version " + version + "...");
            String url = string + "/${LATEST_BUILD}/downloads/${JAR_NAME}".replace("${LATEST_BUILD}", String.valueOf(latestBuild)).replace("${JAR_NAME}", "paper-" + version + "-" + latestBuild + ".jar");
            return new URL(url);
        } else {
            throw new MojoExecutionException("Failed to fetch PaperMC version info.");
        }
    }

    public String getVersion() {
        return version;
    }

    public Path getDir() {
        return dir;
    }

    public Log getLog() {
        return log;
    }
}
