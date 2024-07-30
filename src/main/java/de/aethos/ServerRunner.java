package de.aethos;


import com.google.common.base.Preconditions;
import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class ServerRunner {
    private final Log log;
    private final Path path;
    private final boolean gui;
    private final String memory;
    private Process server;

    public ServerRunner(Path path, boolean gui, String memory, Log log) {
        this.log = log;
        this.path = path;
        this.gui = gui;
        this.memory = memory;
        getLog().debug("Starting server...");
    }

    public void startServer() throws IOException {
        Preconditions.checkArgument(Files.isDirectory(path));
        ProcessBuilder builder;
        Path paperJar = path.resolve("paper.jar").toAbsolutePath();
        if (gui) {
            builder = new ProcessBuilder("java", memory, "-jar", paperJar.toString());
        } else {
            builder = new ProcessBuilder("java", memory, "-jar", paperJar.toString(), "nogui");
        }
        builder.directory(path.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        builder.redirectInput(ProcessBuilder.Redirect.PIPE);
        server = builder.start();
        getLog().info("PaperMC server started successfully.");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            getLog().info("Shutdown Hook: Stopping PaperMC server...");
            stopServer();
        }));
        while (server.isAlive()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void stopServer() {
        if (server != null && server.isAlive()) {
            try (OutputStream os = server.getOutputStream()) {
                os.write("stop\n".getBytes());
                os.flush();
                getLog().info("Sent 'stop' command to PaperMC server.");
            } catch (IOException e) {
                getLog().error("Failed to send 'stop' command to PaperMC server.", e);
            }
        }
    }

    public Log getLog() {
        return log;
    }
}
