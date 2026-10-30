package ch.ubique.uetlib.image.webp.bench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class WebpEncodeBenchmarkCommandTest {
    @Test
    void defaultImg2WebpCommandMatchesLegacyCliSettings() {
        final List<String> command =
                WebpEncodeBenchmark.buildImg2WebpCommand(
                        "img2webp",
                        List.of(Path.of("a.jpg"), Path.of("b.jpg")),
                        Path.of("out.webp"),
                        false,
                        42,
                        null,
                        null);

        assertIterableEquals(
                List.of(
                        "img2webp",
                        "-loop",
                        "0",
                        "-o",
                        "out.webp",
                        "-d",
                        "42",
                        "-lossy",
                        "-q",
                        "75",
                        "-m",
                        "4",
                        "a.jpg",
                        "-d",
                        "42",
                        "-lossy",
                        "-q",
                        "75",
                        "-m",
                        "4",
                        "b.jpg"),
                command);
        assertFalse(command.contains("-kmin"));
        assertFalse(command.contains("-kmax"));
        assertFalse(command.contains("-mt"));
    }

    @Test
    void losslessKeyframeCommandAddsOnlyExplicitKeyframeFlags() {
        final List<String> command =
                WebpEncodeBenchmark.buildImg2WebpCommand(
                        "img2webp",
                        List.of(Path.of("frame.png")),
                        Path.of("out.webp"),
                        true,
                        1000,
                        3,
                        5);

        assertEquals(
                List.of("img2webp", "-loop", "0", "-o", "out.webp", "-kmin", "3", "-kmax", "5"),
                command.subList(0, 9));
        assertTrue(command.contains("-lossless"));
        assertFalse(command.contains("-lossy"));
        assertFalse(command.contains("-mt"));
    }

    @Test
    void partialKeyframeConfigurationIsIgnoredForDefaultFairness() {
        final List<String> command =
                WebpEncodeBenchmark.buildImg2WebpCommand(
                        "img2webp",
                        List.of(Path.of("frame.ppm")),
                        Path.of("out.webp"),
                        false,
                        10,
                        3,
                        null);

        assertFalse(command.contains("-kmin"));
        assertFalse(command.contains("-kmax"));
    }
}
