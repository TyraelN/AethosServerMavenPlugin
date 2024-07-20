package de.aethos;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.IOException;
import java.nio.file.Path;

@Mojo(name = "clean", defaultPhase = LifecyclePhase.CLEAN)
public class CleanMojo extends AbstractMojo {
    
    @Parameter(property = "path", defaultValue = "server")
    private String path;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            FileUtils.deleteDirectory(Path.of(path).toFile());
        } catch (IOException e) {
            throw new MojoExecutionException(e);
        }
    }
}
