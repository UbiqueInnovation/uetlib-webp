package ch.ubique.backend.image.webp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Resolves and loads the {@code uetlib_webp_native} shared library.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>{@code -Duetlib.webp.native.path=/abs/path/to/lib} — explicit override.</li>
 *   <li>{@code native/build/lib&lt;name&gt;.&lt;ext&gt;} — local CMake build, used in-tree.</li>
 *   <li>Classpath resource under {@code /native/&lt;os&gt;-&lt;arch&gt;/lib&lt;name&gt;.&lt;ext&gt;}
 *       — the packaged JAR layout. The resource is streamed into a temp file and loaded.</li>
 *   <li>{@link System#loadLibrary(String)} — relies on {@code java.library.path} /
 *       {@code LD_LIBRARY_PATH} / {@code DYLD_LIBRARY_PATH}.</li>
 * </ol>
 */
public final class NativeLibraryLoader {
    private static volatile boolean loaded;

    private NativeLibraryLoader() {
    }

    public static void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (NativeLibraryLoader.class) {
            if (loaded) {
                return;
            }
            String explicitPath = System.getProperty("uetlib.webp.native.path");
            Path buildPath = Path.of("native", "build", System.mapLibraryName(resolveLibraryName()));
            if (explicitPath != null && !explicitPath.isBlank()) {
                System.load(explicitPath);
            } else if (Files.isRegularFile(buildPath)) {
                System.load(buildPath.toAbsolutePath().toString());
            } else {
                Path extracted = extractClasspathResource(classpathResourcePath());
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

    static Path extractClasspathResource(String resourcePath) {
        try (InputStream in = NativeLibraryLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            Path tempFile = Files.createTempFile("uetlib-webp-", suffixFor(resolveLibraryName()));
            tempFile.toFile().deleteOnExit();
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            return tempFile;
        } catch (IOException e) {
            throw new IllegalStateException(
                "Failed to extract native library from classpath resource " + resourcePath, e);
        }
    }

    private static String resolveLibraryName() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("linux") || os.contains("mac")) {
            return "uetlib_webp_native";
        }
        throw new IllegalStateException("Unsupported OS for native lib: " + os);
    }

    private static String resolveClassifier() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String osToken;
        if (os.contains("mac")) {
            osToken = "macos";
        } else if (os.contains("linux")) {
            osToken = "linux";
        } else {
            throw new IllegalStateException("Unsupported OS for native lib: " + os);
        }
        String archToken;
        if (arch.equals("aarch64") || arch.equals("arm64")) {
            archToken = "aarch64";
        } else if (arch.equals("x86_64") || arch.equals("amd64")) {
            archToken = "x86_64";
        } else {
            throw new IllegalStateException("Unsupported arch for native lib: " + arch);
        }
        return osToken + "-" + archToken;
    }

    private static String suffixFor(String libName) {
        String mapped = System.mapLibraryName(libName);
        int dot = mapped.lastIndexOf('.');
        return dot >= 0 ? mapped.substring(dot) : "";
    }
}
