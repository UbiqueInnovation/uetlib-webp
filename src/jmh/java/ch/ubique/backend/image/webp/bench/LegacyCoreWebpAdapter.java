package ch.ubique.backend.image.webp.bench;

import java.io.Closeable;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class LegacyCoreWebpAdapter implements Closeable {
    private static final String CLASSPATH_PROPERTY = "uetlib.webp.legacy.classpath";
    private static final String CLASSPATH_ENV = "UETLIB_CORE_CLASSPATH";
    private static final String SHORT_CLASSPATH_ENV = "LEGACY_CP";
    private static final Path DEFAULT_CORE_JAR =
            Path.of(
                    System.getProperty("user.home"),
                    "work",
                    "git",
                    "uetlib-core",
                    "target",
                    "uetlib-core-2.0.0-SNAPSHOT.jar");

    private final ChildFirstClassLoader classLoader;
    private final Class<?> webpUtilsClass;
    private final Class<?> webpWriterClass;
    private final Object losslessConfig;
    private final Object lossyConfig;

    LegacyCoreWebpAdapter() {
        try {
            classLoader = new ChildFirstClassLoader(urlsFromClasspath(resolveClasspath()));
            webpUtilsClass = classLoader.loadClass("ch.ubique.backend.image.webp.WebpUtils");
            webpWriterClass = classLoader.loadClass("ch.ubique.backend.image.webp.WebpWriter");
            losslessConfig = createConfig(false);
            lossyConfig = createConfig(true);
        } catch (final Exception e) {
            throw new IllegalStateException(
                    """
                    Failed to load legacy uetlib-core WebP API.
                    Build uetlib-core and provide its full runtime classpath with:
                      -D%s=/path/to/uetlib-core.jar:/path/to/dependency.jar
                    or environment variable:
                      %s=/path/to/uetlib-core.jar:/path/to/dependency.jar
                    or:
                      %s=/path/to/uetlib-core.jar:/path/to/dependency.jar
                    """
                            .formatted(CLASSPATH_PROPERTY, CLASSPATH_ENV, SHORT_CLASSPATH_ENV),
                    e);
        }
    }

    private static String resolveClasspath() {
        String explicit = System.getProperty(CLASSPATH_PROPERTY);
        if (explicit == null || explicit.isBlank()) {
            explicit = System.getenv(CLASSPATH_ENV);
        }
        if (explicit == null || explicit.isBlank()) {
            explicit = System.getenv(SHORT_CLASSPATH_ENV);
        }
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }
        return DEFAULT_CORE_JAR.toString();
    }

    private static URL[] urlsFromClasspath(final String classpath)
            throws java.net.MalformedURLException {
        final List<URL> urls = new ArrayList<>();
        for (final String entry :
                classpath.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (!entry.isBlank()) {
                urls.add(Path.of(entry).toUri().toURL());
            }
        }
        return urls.toArray(URL[]::new);
    }

    byte[] animationFromImagesAsByteArrays(final Collection<byte[]> images, final boolean lossy) {
        try {
            final Method method =
                    webpUtilsClass.getMethod(
                            "animationFromImagesAsByteArrays",
                            Collection.class,
                            classLoader.loadClass(
                                    "ch.ubique.backend.image.webp.WebpEncodingConfig"));
            return (byte[]) method.invoke(null, images, lossy ? lossyConfig : losslessConfig);
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Failed to invoke legacy WebpUtils.animationFromImagesAsByteArrays", e);
        }
    }

    byte[] writerEncodePaths(final List<Path> framePaths, final boolean lossy) {
        Object writer = null;
        try {
            final Class<?> configClass =
                    classLoader.loadClass("ch.ubique.backend.image.webp.WebpEncodingConfig");
            final Constructor<?> constructor = webpWriterClass.getConstructor(configClass);
            writer = constructor.newInstance(lossy ? lossyConfig : losslessConfig);

            final Method encode = webpWriterClass.getMethod("encode", List.class);
            final Path output = (Path) encode.invoke(writer, framePaths);
            return Files.readAllBytes(output);
        } catch (final ReflectiveOperationException | java.io.IOException e) {
            throw new IllegalStateException(
                    "Failed to invoke legacy WebpWriter.encode(List<Path>)", e);
        } finally {
            if (writer != null) {
                closeWriter(writer);
            }
        }
    }

    @Override
    public void close() {
        try {
            classLoader.close();
        } catch (final java.io.IOException e) {
            throw new IllegalStateException("Failed to close legacy classloader", e);
        }
    }

    private Object createConfig(final boolean lossy) throws ReflectiveOperationException {
        final Class<?> configClass =
                classLoader.loadClass("ch.ubique.backend.image.webp.WebpEncodingConfig");
        final Object builder = configClass.getMethod("builder").invoke(null);

        final Class<?> methodClass =
                classLoader.loadClass("ch.ubique.backend.image.webp.CompressionMethod");
        final Object m4 =
                Enum.valueOf((Class<? extends Enum>) methodClass.asSubclass(Enum.class), "M4");
        builder.getClass().getMethod("method", methodClass).invoke(builder, m4);
        builder.getClass().getMethod("compressionFactor", int.class).invoke(builder, 75);
        builder.getClass()
                .getMethod("duration", Duration.class)
                .invoke(builder, Duration.ofMillis(42));

        if (lossy) {
            final Class<?> qualityClass =
                    classLoader.loadClass("ch.ubique.backend.image.webp.WebpQuality");
            final Class<?> lossyClass =
                    classLoader.loadClass("ch.ubique.backend.image.webp.LossyWebp");
            final Object lossyQuality = lossyClass.getConstructor(int.class).newInstance(75);
            builder.getClass().getMethod("quality", qualityClass).invoke(builder, lossyQuality);
        }

        return builder.getClass().getMethod("build").invoke(builder);
    }

    private void closeWriter(final Object writer) {
        try {
            webpWriterClass.getMethod("close").invoke(writer);
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to close legacy WebpWriter", e);
        }
    }

    private static final class ChildFirstClassLoader extends URLClassLoader {
        private ChildFirstClassLoader(final URL[] urls) {
            super(urls, ClassLoader.getSystemClassLoader());
        }

        @Override
        protected Class<?> loadClass(final String name, final boolean resolve)
                throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null && name.startsWith("ch.ubique.backend.")) {
                    try {
                        loaded = findClass(name);
                    } catch (final ClassNotFoundException ignored) {
                        loaded = null;
                    }
                }
                if (loaded == null) {
                    loaded = super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }
    }
}
