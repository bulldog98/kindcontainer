package com.dajudge.kindcontainer.kubectl;

import com.dajudge.kindcontainer.BaseSidecarContainer.ExecInContainer;
import com.dajudge.kindcontainer.BaseSidecarContainer.FileTarget;
import com.dajudge.kindcontainer.exception.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.shaded.org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static java.lang.Thread.currentThread;
import static java.util.Arrays.asList;
import static java.util.function.Function.identity;

public class ApplyFluent<P> {
    private static final Logger LOG = LoggerFactory.getLogger(ApplyFluent.class);
    private final ExecInContainer exec;
    private final FileTarget fileTarget;
    private final P parent;
    private final List<Runnable> preExecutionRunnables = new ArrayList<>();
    private final List<String> files = new ArrayList<>();
    private String namespace;

    ApplyFluent(final ExecInContainer exec, final FileTarget fileTarget, final P parent) {
        this.exec = exec;
        this.fileTarget = fileTarget;
        this.parent = parent;
    }

    public ApplyFluent<P> namespace(final String namespace) {
        this.namespace = namespace;
        return this;
    }

    public ApplyFluent<P> fileFromClasspath(final String resourceName) {
        return fileFromClasspath(resourceName, identity());
    }

    public ApplyFluent<P> fileFromClasspath(final String resourceName, final Function<byte[], byte[]> transform) {
        final String path = "/tmp/classpath:" + resourceName
                .replaceAll("_", "__")
                .replaceAll("[^a-zA-Z0-9.]", "_");
        files.add(path);
        final byte[] bytes = transform.apply(resourceToByteArray(resourceName));
        final Transferable transferable = Transferable.of(bytes);
        preExecutionRunnables.add(() -> fileTarget.copyFileToContainer(transferable, path));
        return this;
    }

    private byte[] resourceToByteArray(final String resourceName) {
        final List<ClassLoader> classloaders = asList(
                ApplyFluent.class.getClassLoader(),
                currentThread().getContextClassLoader(),
                ClassLoader.getSystemClassLoader()
        );
        for (final ClassLoader classloader : classloaders) {
            try {
                return IOUtils.resourceToByteArray(resourceName, ApplyFluent.class.getClassLoader());
            } catch (final IOException e) {
                LOG.debug("Failed to read classpath resource '{}' via classloader '{}'", resourceName, classloader, e);
            }
        }
        throw new IllegalArgumentException("Unable to locate resource: " + resourceName);
    }

    public P run() throws IOException, ExecutionException, InterruptedException {
        final List<String> cmdline = new ArrayList<>();
        cmdline.add("kubectl");
        cmdline.add("apply");
        if (namespace != null) {
            cmdline.add("--namespace");
            cmdline.add(namespace);
        }
        files.forEach(file -> {
            cmdline.add("-f");
            cmdline.add(file);
        });
        preExecutionRunnables.forEach(Runnable::run);
        exec.safeExecInContainer(cmdline.toArray(new String[]{}));
        return parent;
    }
}
