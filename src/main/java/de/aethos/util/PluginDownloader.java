package de.aethos.util;


import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Repository;
import org.apache.maven.model.RepositoryBase;
import org.apache.maven.plugin.logging.Log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.stream.Stream;

public class PluginDownloader {
    private static final Predicate<Dependency> NOT_PAPER = dependency -> !dependency.getArtifactId().equals("paper-api");
    private static final Predicate<Dependency> IS_PROVIDED = dependency -> "provided".equals(dependency.getScope());
    private final Log log;
    private final Path dir;
    private final Predicate<Dependency> NO_FILE;
    private final List<Dependency> dependencies;
    private final List<Repository> repositories;
    private final Function<Dependency, Stream<Map.Entry<URL, Path>>> GET_URLS_AND_PATHS;

    public PluginDownloader(Path dir, Log logger, Collection<Dependency> dependencies, List<Repository> repositories) {
        this.log = logger;
        this.dir = dir;
        NO_FILE = dependency -> !Files.exists(dir.resolve(dependency.getArtifactId() + "-" + dependency.getVersion() + ".jar"));
        this.repositories = repositories;
        this.dependencies = dependencies.stream().filter(NOT_PAPER).filter(IS_PROVIDED).filter(NO_FILE).toList();
        GET_URLS_AND_PATHS = dependency -> repositories.stream()
                .map(RepositoryBase::getUrl)
                .flatMap(str -> {
                    URL url = url(str.endsWith("/") ? str : str + "/", dependency);
                    if (url != null) {
                        return Stream.of(Map.entry(url, dir.resolve(dependency.getArtifactId() + "-" + dependency.getVersion() + ".jar")));
                    }
                    return Stream.empty();
                });
    }

    private static URL url(String repository, Dependency dependency) {
        try {
            return URI.create(repository + dependency.getGroupId().replace(".", "/") + "/" + dependency.getArtifactId() + "/" + dependency.getVersion() + "/" + dependency.getArtifactId() + "-" + dependency.getVersion() + ".jar").toURL();
        } catch (MalformedURLException e) {
            return null;
        }
    }

    public void download() {
        getLog().info("Installing dependencies...");
        ExecutorService service = Executors.newFixedThreadPool(3);
        dependencies.stream()
                .filter(NOT_PAPER)
                .filter(IS_PROVIDED)
                .filter(NO_FILE)
                .flatMap(GET_URLS_AND_PATHS)
                .map(entry -> downloader(entry.getKey(), entry.getValue()))
                .map(service::submit)
                .forEach(future -> {
                    try {
                        future.get();
                    } catch (InterruptedException | ExecutionException e) {
                        getLog().error(e);
                    }
                });
        service.shutdown();
    }

    public Runnable downloader(URL url, Path path) {
        return () -> {
            try {
                downloadPlugin(url, path);
            } catch (IllegalArgumentException e) {
                getLog().debug(e);
            }
        };
    }

    public void downloadPlugin(URL url, Path path) {
        try {
            HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.setRequestMethod("HEAD");
            httpURLConnection.connect();

            int responseCode = httpURLConnection.getResponseCode();
            if (!(responseCode == HttpURLConnection.HTTP_OK)) {
                return;
            }

            httpURLConnection.disconnect();

            if (!isPlugin(url.openStream())) {
                getLog().info("Skipping dependency. " + path + " is not a Plugin");
                return;
            }
            InputStream inputStream = url.openStream();
            getLog().info("Downloading : " + url + " ...");
            if (!Files.exists(path)) {
                FileUtils.createParentDirectories(path.toFile());
                Files.createFile(path);
            }

            ReadableByteChannel rbc = Channels.newChannel(inputStream);
            FileOutputStream fos = new FileOutputStream(path.toString());
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            rbc.close();
            fos.close();
            getLog().info("Installation finished: " + path.getFileName());
        } catch (IOException e) {
            getLog().error(e);
        }
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

    public Log getLog() {
        return log;
    }

    public Path getDir() {
        return dir;
    }

    public List<Repository> getRepositories() {
        return repositories;
    }
}
