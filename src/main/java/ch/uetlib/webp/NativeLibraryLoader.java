package ch.uetlib.webp;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Resolves and loads the {@code uetlib_webp_native} shared library.
 *
 * <p>Resolution order:
 *
 * <ol>
 *   <li>{@code -Duetlib.webp.native.path=/abs/path/to/lib} — explicit override.
 *   <li>{@code native/build/lib<name>.<ext>} — local CMake build, used in-tree.
 *   <li>Classpath resource under {@code /native/<os>-<arch>/lib<name>.<ext>} — the packaged JAR
 *       layout. The resource is streamed into a temp file and loaded.
 *   <li>{@link System#loadLibrary(String)} — relies on {@code java.library.path} / {@code
 *       LD_LIBRARY_PATH} / {@code DYLD_LIBRARY_PATH}.
 * </ol>
 */
public final class NativeLibraryLoader {
    private static volatile boolean loaded;

    private NativeLibraryLoader() {}

    public static void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (NativeLibraryLoader.class) {
            if (loaded) {
                return;
            }
            final var explicitPath = System.getProperty("uetlib.webp.native.path");
            final var buildPath =
                    Path.of("native", "build", System.mapLibraryName(resolveLibraryName()));
            if (explicitPath != null && !explicitPath.isBlank()) {
                System.load(explicitPath);
            } else if (Files.isRegularFile(buildPath)) {
                System.load(buildPath.toAbsolutePath().toString());
            } else {
                final var extracted = extractClasspathResource(classpathResourcePath());
                if (extracted != null) {
                    System.load(extracted.toAbsolutePath().toString());
                } else {
                    System.loadLibrary(resolveLibraryName());
                }
            }
            loaded = true;
        }
    }

    static String classpathResourcePath() {
        return "/native/" + resolveClassifier() + "/" + System.mapLibraryName(resolveLibraryName());
    }

    @Nullable
    static Path extractClasspathResource(final String resourcePath) {
        try (final var in = NativeLibraryLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            final var tempFile =
                    Files.createTempFile("uetlib-webp-", suffixFor(resolveLibraryName()));
            tempFile.toFile().deleteOnExit();
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            return tempFile;
        } catch (final IOException e) {
            throw new IllegalStateException(
                    "Failed to extract native library from classpath resource " + resourcePath, e);
        }
    }

    private static String resolveLibraryName() {
        final var os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("linux") || os.contains("mac")) {
            return "uetlib_webp_native";
        }
        throw new IllegalStateException("Unsupported OS for native lib: " + os);
    }

    private static String resolveClassifier() {
        final var os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        final var arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        final String osToken;
        if (os.contains("mac")) {
            osToken = "macos";
        } else if (os.contains("linux")) {
            osToken = "linux";
        } else {
            throw new IllegalStateException("Unsupported OS for native lib: " + os);
        }
        final String archToken;
        if ("aarch64".equals(arch) || "arm64".equals(arch)) {
            archToken = "aarch64";
        } else if ("x86_64".equals(arch) || "amd64".equals(arch)) {
            archToken = "x86_64";
        } else {
            throw new IllegalStateException("Unsupported arch for native lib: " + arch);
        }
        return osToken + "-" + archToken;
    }

    private static String suffixFor(final String libName) {
        final var mapped = System.mapLibraryName(libName);
        final var dot = mapped.lastIndexOf('.');
        return dot >= 0 ? mapped.substring(dot) : "";
    }
}
