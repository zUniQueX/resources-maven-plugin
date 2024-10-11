package com.github.zuniquex.resources.maven.plugin;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mojo( name = "resources", defaultPhase = LifecyclePhase.PROCESS_RESOURCES )
public class ResourceMojo extends AbstractMojo {

    @Parameter(defaultValue = "${session}", required = true, readonly = true)
    private MavenSession mavenSession;

    @Parameter(defaultValue = "${project.build.directory}/generated-sources")
    private String generatedSourcesDirectory;

    public void execute() throws MojoExecutionException {

        String classContent;
        try {
            classContent = buildResourceClass(mavenSession.getCurrentProject().getResources());
        } catch (UncheckedIOException ioException) {
            throw new MojoExecutionException("Unable to build resources class", ioException.getCause());
        }

        try {
            writeClass(classContent);
        } catch (IOException ioException) {
            throw new MojoExecutionException("Unable to write class", ioException);
        }
    }

    private void writeClass(String content) throws IOException {

        Path generatedSourcesPath = Paths.get(generatedSourcesDirectory);
        Path generatedResourcesRoot = generatedSourcesPath.resolve("resources");
        Path generatedResourcesPath = generatedResourcesRoot.resolve(
                mavenSession.getCurrentProject().getGroupId().replace(".", FileSystems.getDefault().getSeparator()));

        Files.createDirectories(generatedResourcesPath);

        Path resourcesClassPath = generatedResourcesPath.resolve("Resources.java");

        Files.write(resourcesClassPath, content.getBytes(StandardCharsets.UTF_8));
    }

    private String buildResourceClass(List<Resource> resources) {

        String resourceConstants = resources.stream()
                .map(this::buildResourceConstants)
                .collect(Collectors.joining(System.lineSeparator()));

        return String.format("package %s;%n%npublic class Resources {%n%s%n}%n",
                mavenSession.getCurrentProject().getGroupId(), resourceConstants);
    }

    private String buildResourceConstants(Resource resource) {

        Path resourceDirectory = Paths.get(resource.getDirectory());
        try (Stream<Path> walker = Files.walk(resourceDirectory)) {
            return walker
                    .filter(path -> !Files.isDirectory(path))
                    .map(path -> "\t" + mapResourceToConstant(path, resourceDirectory))
                    .collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException ioException) {
            throw new UncheckedIOException(ioException);
        }
    }

    private String mapResourceToConstant(Path path, Path resourceDirectory) {

        Path relativePath = resourceDirectory.relativize(path).normalize();
        String constantName = relativePath.toString().replaceAll("\\W", "_").toUpperCase();

        String resourcePath = relativePath.toString();
        if (!resourcePath.startsWith(FileSystems.getDefault().getSeparator())) {
            resourcePath = FileSystems.getDefault().getSeparator() + resourcePath;
        }

        return String.format("public static final String %s = \"%s\";", constantName, resourcePath);
    }
}
