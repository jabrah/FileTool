package edu.jhu.tool;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    private static String base = "/mnt/";
    private static String fileMapName = "filemap.csv";

    public static void main(String[] args) throws Exception {
        Options options = new Options();
        options.addOption(new Option("b", "base", true, "Base directory"));
        options.addOption(new Option("n", "filemap_name", true, "Name of filemap file with file extension."));
        options.addOption(new Option("f", "force", false, "Force renaming."));
        options.addOption("I", "ID", false, "Files are already in the form needed, but the ID must change.");
        options.addOption("d", "dry-run", false, "Dry run. Tool will go through the motions, but will not actually change files. Can be used to inspect output to make sure things will go well.");

        CommandLineParser parser = new BasicParser();
        CommandLine cmd = parser.parse(options, args);

        run(cmd);
    }

    private static void run(CommandLine cmd) throws IOException {
        String[] args = cmd.getArgs();
        if (args.length != 2) {
            System.out.println("Must specify collection and book.");
            return;
        }

        setDefaults(cmd);

        Path basePath = Paths.get(base).resolve(args[0]).resolve(args[1]);

        if (cmd.hasOption("ID")) {
            changeFileIds(basePath);
            return;
        }

        List<String> fileMapLines = loadFileMap(basePath);

        int lineCount = 0;
        for (String line : fileMapLines) {
            if (line.startsWith("#")) {
                continue;
            }

            String[] parts = line.split(",");
            lineCount++;

            // Validate line
            if (parts.length != 2 || parts[0].equals("") || parts[1].equals("")) {
                System.out.println("[Error: line " + lineCount + "] Malformed line in file map. " + line);
                continue;
            }

            Path filePath = basePath.resolve(parts[0]);
            if (!Files.exists(filePath) || !Files.isRegularFile(filePath) || !Files.isReadable(filePath)) {
                System.out.println("[Error: line " + lineCount + "] File does not exist or cannot be loaded. "
                        + parts[0]);
                continue;
            }

            Path renamedPath = basePath.resolve(parts[1]);
            if (Files.exists(renamedPath)) {
                System.out.println("[Error: line " + lineCount + "] Target name already exists. "
                        + parts[0] + " --> " + parts[1]);
                continue;
            }

            // Rename file by moving it to a renamed target.
            System.out.println("Renaming file. " + parts[0] + " --> " + parts[1]);
            Files.move(filePath, renamedPath);
        }
    }

    private static void renameFile(Path original, Path renamed, boolean dryRun) throws IOException {
        if (!dryRun) {
            Files.move(original, renamed);
        }
    }

    private static void setDefaults(CommandLine cmd) {
        if (cmd.hasOption("base")) {
            base = cmd.getOptionValue("base");
        }

        if (cmd.hasOption("filemap_name")) {
            fileMapName = cmd.getOptionValue("filemap_name");
        }
    }

    private static List<String> loadFileMap(Path basePath) throws IOException {
        Path fileMapPath = basePath.resolve(fileMapName);

        if (!Files.exists(fileMapPath) || !Files.isRegularFile(fileMapPath)) {
            System.out.println("File map not found.");
            System.exit(-1);
        }

        return Files.readAllLines(fileMapPath, Charset.forName("UTF-8"));
    }

    private static void changeFileIds(final Path basePath) throws IOException {
        final String newId = basePath.getFileName().toString();

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(basePath, new DirectoryStream.Filter<Path>() {
            @Override
            public boolean accept(Path entry) throws IOException {
                String filename = entry.getFileName().toString();
                return Files.isRegularFile(entry) && goodToRename(filename) && !filename.startsWith(newId);
            }
        })) {
            for (Path path : ds) {
                String oldName = path.getFileName().toString();
                String newName = newId + oldName.substring(oldName.indexOf('.'));

                Path newPath = path.getParent().resolve(newName);
                if (Files.exists(newPath)) {
                    continue;
                }

                System.out.println("Renaming file. " + oldName + " --> " + newName);
                Files.move(path, newPath);
            }
        }
    }

    private static boolean goodToRename(String name) {
        return !name.contains("filemap");
    }
}
