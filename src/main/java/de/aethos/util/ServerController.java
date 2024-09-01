package de.aethos.util;

import nl.vv32.rcon.Rcon;
import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.Random;

public class ServerController {


    private final Path dir;
    private final Log log;
    private final Path propertiesFile;

    public ServerController(Path dir, Log log) {
        this.dir = dir;
        this.log = log;
        this.propertiesFile = dir.resolve("server.properties");
    }


    public void setup() {
        try {
            final Properties properties = readProperties();
            properties.setProperty("enable-rcon", "true");
            properties.setProperty("rcon.port", "25575");
            properties.setProperty("rcon.password", Password.generateRandom16());

            try (OutputStream output = Files.newOutputStream(propertiesFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                properties.store(output, "Updated by RConSetup");
                log.info("RCON enabled and password set in server.properties.");
            }
        } catch (IOException e) {
            log.error("Error updating server.properties", e);
        }
    }

    public String readPassword() {
        return readProperties().getProperty("rcon.password");
    }

    public int readPort() {
        return Integer.parseInt(readProperties().getProperty("rcon.port"));
    }

    public void sendCommand(String str) {
        try (Rcon rcon = Rcon.newBuilder()
                .withChannel(SocketChannel.open(
                        new InetSocketAddress("localhost", readPort())))
                .withCharset(StandardCharsets.UTF_8)
                .withReadBufferCapacity(1234)
                .withWriteBufferCapacity(1234)
                .build()) {

            rcon.authenticate(readPassword());
            rcon.sendCommand(str);
            rcon.close();
            log.info("Sent " + str + "  command to PaperMC server.");
        } catch (IOException e) {
            log.error("Failed to send" + str + " command to PaperMC server.", e);
        }
    }

    // Methode, um die Standardwerte der server.properties-Datei zu erstellen
    public void createDefaultProperties() {
        Path propertiesFile = dir.resolve("server.properties");
        if (Files.exists(propertiesFile)) {
            log.info("server.properties already exists, skipping default creation.");
            return;
        }
        // Standardwerte für die server.properties
        Properties defaultProperties = new Properties();
        defaultProperties.setProperty("rcon.port", "25575");
        defaultProperties.setProperty("enable-rcon", "false");  // Wird später auf true gesetzt
        defaultProperties.setProperty("rcon.password", "");  // Platzhalter, wird später gesetzt

        // Überprüfen, ob die Datei existiert, andernfalls Standardwerte schreiben

        try (OutputStream output = Files.newOutputStream(propertiesFile, StandardOpenOption.CREATE_NEW)) {
            defaultProperties.store(output, "Minecraft server default properties");
            log.info("Default server.properties created.");
        } catch (IOException e) {
            log.error("Error creating server.properties with default values", e);
        }
    }

    private Properties readProperties() {
        final Path propertiesFile = dir.resolve("server.properties");
        Properties properties = new Properties();
        try {
            if (Files.exists(propertiesFile)) {
                try (InputStream input = Files.newInputStream(propertiesFile)) {
                    properties.load(input);
                    return properties;
                }
            } else {
                log.warn("server.properties file does not exist at: " + propertiesFile);
                throw new IllegalStateException("server.properties file does not exist at: " + propertiesFile);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error reading server.properties", e);
        }
    }

    private static final class Password {
        private final static String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

        private static String generateRandom16() {
            final Random random = new Random();
            final StringBuilder password = new StringBuilder(16);
            password.append("gen");
            for (int i = 0; i < 13; i++) {
                password.append(CHARS.charAt(random.nextInt(CHARS.length())));
            }
            return password.toString();
        }
    }

}
