package edu.jhu.tool;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) throws Exception {
        Scanner in = new Scanner(System.in);

        // Get total number of images
        System.out.print("Book path in archive: ");
        String bookPath = in.next();
        List<String> fileNames = getFileNames(bookPath);

        System.out.print("Has a frontcover + front pastedown image? (true|false) ");
        boolean hasFrontCover = in.nextBoolean();
        System.out.print("Has a back cover and back pastedown image? (true|false) ");
        boolean hasBackCover = in.nextBoolean();

        // Get number of front/end matter flyleaves
        System.out.print("Number of frontmatter flyleaves: ");
        int frontmatter = in.nextInt();

        System.out.print("Number of endmatter flyleaves: ");
        int endmatter = in.nextInt();

        // Get number of misc images
        System.out.print("Number of misc images: ");
        int misc = in.nextInt();

        System.out.print("Book ID: ");
        String id = in.next();

        List<String> mappingLines =
                generateMappingCSVLines(fileNames, id, frontmatter, endmatter, misc, hasFrontCover, hasBackCover);

        System.out.println("Writing file map.");
        writeFileMap(bookPath, mappingLines);
    }

    /**
     *
     *
     * @param bookPath path to the book in archive
     * @return list of images
     * @throws IOException
     */
    private static List<String> getFileNames(String bookPath) throws IOException{
        Path path = Paths.get(bookPath);

        if (!Files.exists(path) || !Files.isDirectory(path)) {
            System.out.println("Specified book path in archive is invalid. [" + bookPath + "]");
            System.exit(-1);
        }

        List<String> filenames = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(path, new DirectoryStream.Filter<Path>() {
            @Override
            public boolean accept(Path entry) throws IOException {
                return entry.toString().endsWith(".tif")
                        && !entry.getFileName().toString().startsWith(".");
            }
        })) {
            for (Path p : ds) {
                filenames.add(p.getFileName().toString());
            }
        }

        Collections.sort(filenames);
        return filenames;
    }

    private static List<String> generateMappingCSVLines(
            List<String> fileNames, String id, int frontmatter, int endmatter,
            int misc, boolean hasFront, boolean hasBack) {
        // assume front/back cover + pastedown!
        List<String> lines = new ArrayList<>();

        if (hasFront) {
            lines.add(fileNames.get(0) + "," + id + ".binding.frontcover.tif");
            lines.add(fileNames.get(1) + "," + id + ".frontmatter.pastedown.tif");
        }

        int total = fileNames.size();
        int front_flyleaf_end = hasFront ? 2 + frontmatter : frontmatter;
        int end_flyleaf_end = hasBack ? total - misc - 2 : total - misc;
        int end_flyleaf_start = end_flyleaf_end - endmatter;

        System.out.println("Generating file mapping for book. [" + id + "]");
        System.out.println("Total images: " + total);
        System.out.println("End of frontmatter flyleaves: " + front_flyleaf_end);
        System.out.println("End of printed material: " + end_flyleaf_start);
        System.out.println("End of endmatter flyleaves: " + end_flyleaf_end);
        System.out.println();

        int nextseq = 1;
        char nextrv = 'r';
        int i;
        for (i = hasFront ? 2 : 0; i < front_flyleaf_end; i++) {
            lines.add(fileNames.get(i) + "," + id + ".frontmatter.flyleaf." + String.format("%03d", nextseq) + nextrv + ".tif");

            if (nextrv == 'v') {
                nextseq++;
                nextrv = 'r';
            } else {
                nextrv = 'v';
            }
        }

        nextseq = 1;
        nextrv = 'r';
        for (i = front_flyleaf_end; i < end_flyleaf_start; i++) {
            lines.add(fileNames.get(i) + "," + id + "." + String.format("%03d", nextseq) + nextrv + ".tif");

            if (nextrv == 'v') {
                nextseq++;
                nextrv = 'r';
            } else {
                nextrv = 'v';
            }
        }

        nextseq = 1;
        nextrv = 'r';
        for (i = end_flyleaf_start; i < end_flyleaf_end; i++) {
            lines.add(fileNames.get(i) + "," + id + ".endmatter.flyleaf." + String.format("%03d", nextseq) + nextrv + ".tif");

            if (nextrv == 'v') {
                nextseq++;
                nextrv = 'r';
            } else {
                nextrv = 'v';
            }
        }

        if (hasBack) {
            lines.add(fileNames.get(i++) + "," + id + ".endmatter.pastedown.tif");
            lines.add(fileNames.get(i) + "," + id + ".binding.backcover.tif");
        }

        for (i = end_flyleaf_end + 2; i < total; i++) {
            lines.add(fileNames.get(i) + "," + id + ".misc.LABEL.tif");
        }

        return lines;
    }

    private static void writeFileMap(String bookPath, List<String> lines) throws IOException {
        Path outPath = Paths.get(bookPath).resolve("filemap.csv");

        if (Files.exists(outPath)) {
            System.out.println("File mapping already exists!");
            System.exit(-1);
        }

        try (OutputStream out = Files.newOutputStream(outPath)) {
            for (String line : lines) {
                String toWrite = line + "\n";
                out.write(toWrite.getBytes("UTF-8"));
            }
        }
    }
}
