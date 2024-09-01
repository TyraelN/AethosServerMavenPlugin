package de.aethos.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;


public class PaperDownloader {
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
                URL url = getPaperUrl(version);
                try (InputStream in = url.openStream()) {
                    Files.copy(in, paperJar);
                }
            } catch (MojoExecutionException | IOException e) {
                getLog().error(e);
            }
        }
        getLog().info("Server setup completed successfully.");
    }

    public URL getPaperUrl(String version) throws IOException, MojoExecutionException {
        getLog().info("Fetching latest build number for PaperMC version " + version + "...");
        final String paperMcApiUrl = "https://papermc.io/api/v2/projects/paper/versions/" + version;
        if (new URL(paperMcApiUrl).openConnection() instanceof HttpURLConnection connection) {
            connection.setRequestMethod("GET");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new MojoExecutionException("Failed to fetch PaperMC version info. HTTP error code: " + connection.getResponseCode());
            }
            final InputStream responseStream = connection.getInputStream();
            final InputStreamReader inputStream = new InputStreamReader(responseStream);
            final JsonObject responseObject = JsonParser.parseReader(inputStream).getAsJsonObject();
            final JsonArray builds = responseObject.getAsJsonArray("builds");
            final int latestBuild = builds.get(builds.size() - 1).getAsInt();
            getLog().info("Downloading PaperMC build " + latestBuild + " for version " + version + "...");
            return new URL(paperMcApiUrl + "/builds/" + latestBuild + "/downloads/paper-" + version + "-" + latestBuild + ".jar");
        }
        return null;
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
