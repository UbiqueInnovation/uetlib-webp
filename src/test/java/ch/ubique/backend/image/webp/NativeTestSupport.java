package ch.ubique.backend.image.webp;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Shared {@code @BeforeAll} helper for native-integration tests.
 *
 * <p>Accepts three sources of the native library, in priority order:
 * <ol>
 *   <li>{@code -Duetlib.webp.native.path} — explicit override</li>
 *   <li>{@code native/build/lib…} — local CMake build output (dev convenience)</li>
 *   <li>Classpath resource at {@code /native/&lt;classifier&gt;/lib…} — the packaged JAR
 *       layout, which is what the tag-deploy workflow stages before running tests</li>
 * </ol>
 *
 * <p>If none of these are present, tests are aborted via {@link org.junit.jupiter.api.Assumptions}
 * so local builds without a native lib skip gracefully instead of failing.
 */
final class NativeTestSupport {
    private NativeTestSupport() {}

    static void configureNativePath() {
        String explicit = System.getProperty("uetlib.webp.native.path");
        if (explicit != null && !explicit.isBlank()) {
            assumeTrue(Files.exists(Path.of(explicit)),
                "Native library path is missing: " + explicit);
            return;
        }

        String libName = System.getProperty("os.name", "").toLowerCase().contains("mac")
            ? "libuetlib_webp_native.dylib"
            : "libuetlib_webp_native.so";
        Path defaultPath = Path.of("native", "build", libName).toAbsolutePath();
        if (Files.exists(defaultPath)) {
            System.setProperty("uetlib.webp.native.path", defaultPath.toString());
            return;
        }

        // Fall through to classpath-resource loader. This is the CI tag-deploy path:
        // the deploy workflow downloads per-classifier artifacts into
        // src/main/resources/native/<classifier>/ but never produces native/build/,
        // so we must allow NativeLibraryLoader to find the lib as a resource.
        String resourcePath = NativeLibraryLoader.classpathResourcePath();
        boolean resourceAvailable =
            NativeTestSupport.class.getResource(resourcePath) != null;
        assumeTrue(
            resourceAvailable,
            "No native library available. Build locally via `cmake -S native -B native/build "
                + "&& cmake --build native/build`, or package it as the classpath resource "
                + resourcePath + ".");
    }
}
