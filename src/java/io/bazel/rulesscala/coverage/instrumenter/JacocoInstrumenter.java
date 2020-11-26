package io.bazel.rulesscala.coverage.instrumenter;

import io.bazel.rulesscala.io_utils.DeleteRecursively;
import io.bazel.rulesscala.jar.JarCreator;
import io.bazel.rulesscala.worker.Worker;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import org.jacoco.core.instr.Instrumenter;
import org.jacoco.core.runtime.OfflineInstrumentationAccessGenerator;

public final class JacocoInstrumenter implements Worker.Interface {

    public static void main(String[] args) throws Exception {
	Worker.workerMain(args, new JacocoInstrumenter());
    }

    @Override
    public void work(String[] args) throws Exception {
        Instrumenter jacoco = new Instrumenter(new OfflineInstrumentationAccessGenerator());
        for (String arg : args) {
            processArg(jacoco, arg);
	}
    }

    private void processArg(Instrumenter jacoco, String arg) throws Exception {
        String[] parts = arg.split("=");
        if (parts.length != 3) {
            throw new Exception("expected `in_path=out_path=srcs` form for argument: " + arg);	
        }
        Path inPath = Paths.get(parts[0]);
        Path outPath = Paths.get(parts[1]);
        String srcs = parts[2];

        // Use a directory for coverage metadata that is unique to each built jar. Avoids
        // multiple threads performing read/write/delete actions on the instrumented classes directory.
        Path instrumentedClassesDirectory = getMetadataDirRelativeToJar(outPath);
        Files.createDirectories(instrumentedClassesDirectory);

        JarCreator jarCreator = new JarCreator(outPath);

        try (
            FileSystem inFS = FileSystems.newFileSystem(inPath, null)
        ) {
            FileVisitor fileVisitor = createInstrumenterVisitor(jacoco, instrumentedClassesDirectory, jarCreator);
            inFS.getRootDirectories().forEach(root -> {
                try {
                    Files.walkFileTree(root, fileVisitor);
                } catch (final Exception e) {
                    throw new RuntimeException(e);
                }
            });

            /* 
            * https://github.com/bazelbuild/bazel/blob/567ca633d016572f5760bfd027c10616f2b8c2e4/src/java_tools/junitrunner/java/com/google/testing/coverage/JacocoCoverageRunner.java#L411
            * 
            * Bazel / JacocoCoverageRunner will look for any file that ends with '-paths-for-coverage.txt' within the JAR to be later used for reconstructing the path for source files.
            * This is a fairly undocumented feature within bazel at this time, but in essence, it opens all the jars, searches for all files matching '-paths-for-coverage.txt'
            * and then adds them to a single in memory set. 
            * 
            * https://github.com/bazelbuild/bazel/blob/567ca633d016572f5760bfd027c10616f2b8c2e4/src/java_tools/junitrunner/java/com/google/testing/coverage/JacocoLCOVFormatter.java#L70
            * Which is then used in the formatter to find the corresponding source file from the set of sources we wrote in all the JARs.
            */
            Path pathsForCoverage = instrumentedClassesDirectory.resolve("-paths-for-coverage.txt");
            Files.write(
                pathsForCoverage,
                srcs.replace(",", "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );

            jarCreator.addEntry(instrumentedClassesDirectory.relativize(pathsForCoverage).toString(), pathsForCoverage);
            jarCreator.setCompression(true);
            jarCreator.execute();
        } finally {
            DeleteRecursively.run(instrumentedClassesDirectory);
        }
    }

    // Return the path of the coverage metadata directory relative to the output jar path.
    private static Path getMetadataDirRelativeToJar(Path outputJar) {
        return outputJar.resolveSibling(outputJar + "-coverage-metadata");
    }

    private SimpleFileVisitor createInstrumenterVisitor(Instrumenter jacoco, Path instrumentedClassesDirectory, JarCreator jarCreator) {
        return new SimpleFileVisitor <Path> () {
            @Override
            public FileVisitResult visitFile(Path inPath, BasicFileAttributes attrs) {
                try {
                    return actuallyVisitFile(inPath, attrs);
                } catch (final Exception e) {
                    throw new RuntimeException(e);
                }
            }

            private FileVisitResult actuallyVisitFile(Path inPath, BasicFileAttributes attrs) throws Exception {
                if (inPath.toString().endsWith(".class")) {
                    // Create a tempPath (that is independent of the name), to avoid "File name too long" exceptions.
                    Path tempPath = Files.createTempFile(instrumentedClassesDirectory, "instrumented", ".jar");
                    Files.delete(tempPath);

                    try (
                        BufferedInputStream inStream = new BufferedInputStream(
                            Files.newInputStream(inPath)); BufferedOutputStream outStream = new BufferedOutputStream(
                            Files.newOutputStream(tempPath, StandardOpenOption.CREATE_NEW));
                    ) {
                        jacoco.instrument(inStream, outStream, inPath.toString());
                    }
                    jarCreator.addEntry(inPath.toString(), tempPath);
                    jarCreator.addEntry(inPath.toString() + ".uninstrumented", inPath);
                } else {
                    jarCreator.addEntry(inPath.toString(), inPath);
                }
                return FileVisitResult.CONTINUE;
            }
        };
    }
}
