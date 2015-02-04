package edu.jhu.tool;

import com.sun.org.apache.xml.internal.serializer.OutputPropertiesFactory;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {
    private static final String defaultDirectory = "/mnt/aorcollection/";
    private static final String defaultName = "filemap.csv";

    public static void main(String[] args) throws Exception {
        Options options = new Options();
        options.addOption("b", "base", true, "Base directory of the collection.");
        options.addOption("n", "name", true, "Name of the file mapping file.");

        CommandLineParser parser = new BasicParser();
        CommandLine cmd = parser.parse(options, args);

        run(cmd);
    }

    private static void run(CommandLine cmd) throws Exception {
        String directory = cmd.hasOption("base") ? cmd.getOptionValue("base") : defaultDirectory;
        String fileMapName = cmd.hasOption("name") ? cmd.getOptionValue("name") : defaultName;
        String[] args = cmd.getArgs();

        if (args.length != 1) {
            System.out.println("The name of the book in the collection must be specified.");
            return;
        }

        String book = args[0];

        Path bookPath = Paths.get(directory).resolve(book);
        if (!Files.exists(bookPath) || !Files.isDirectory(bookPath)) {
            System.out.println("Invalid directory. [" + bookPath.toString() + "]");
            return;
        }

        Path fileMapPath = bookPath.resolve(fileMapName);
        if (!Files.exists(fileMapPath) || !Files.isReadable(fileMapPath)) {
            System.out.println("File mapping not found or not readable. [" + fileMapName + "]");
            return;
        }

        Map<String, String> fileMap = loadFileMap(fileMapPath);
        modifyAllTranscriptions(bookPath, fileMap);
    }

    /**
     * @param fileMapPath full path of the file mapping
     * @return map of original names to new names
     * @throws IOException
     */
    private static Map<String, String> loadFileMap(Path fileMapPath) throws IOException {
        Map<String, String> map = new HashMap<>();

        List<String> lines = Files.readAllLines(fileMapPath, Charset.forName("UTF-8"));
        for (String line : lines) {
            String[] parts = line.split(",");

            if (parts.length != 2 || parts[0].equals("") || parts[1].equals("")) {
                System.out.println("[Error] Malformed line in file map. [" + line + "]");
                continue;
            }

            map.put(parts[0], parts[1]);
        }

        return map;
    }

    /**
     * @param bookPath full path of the book in the archive
     * @throws Exception
     */
    private static void modifyAllTranscriptions(Path bookPath, Map<String, String> fileMap)
            throws Exception {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(bookPath, new DirectoryStream.Filter<Path>() {
            @Override
            public boolean accept(Path entry) throws IOException {
                String name = entry.getFileName().toString();
                return name.endsWith(".xml") && !name.contains("description");
            }})) {
            for (Path path : ds) {
                if (!Files.isReadable(path)) {
                    System.out.println("Failed to read file. [" + path.getFileName().toString() + "]");
                    continue;
                }

                fromXml(path, fileMap);
            }
        }
    }

    /**
     * @param transcriptionPath path of transcription file
     * @throws Exception
     */
    private static void fromXml(Path transcriptionPath, Map<String, String> fileMap) throws Exception {
        String originalName = transcriptionPath.getFileName().toString();

        Document doc = getDocument(transcriptionPath);
        NodeList list = doc.getElementsByTagName("page");

        for (int i = 0; i < list.getLength(); i++) {
            Node node = list.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element pageEl = (Element) node;
            String page = pageEl.getAttribute("filename");
            // Get reference to old image name
            if (page == null || page.equals("")) {
                System.out.println("Transcription file does not refer to any page. [" + originalName + "]");
                continue;
            }

            // Get the new filename of image
            String newPage = fileMap.get(page);
            if (newPage == null || newPage.equals("")) {
                System.out.println("Could not find new name of page. [" + page + "]");
                continue;
            }
            // Set new value for filename reference
            pageEl.setAttribute("filename", newPage);

            // Change file type
            newPage = newPage.replace(".tif", ".xml");

            Path target = transcriptionPath.getParent().resolve(newPage);
            if (Files.exists(target)) {
                System.out.println("Target file already exists! [" + newPage + "]");
                continue;
            }

            // Write modified document to new file
            try (OutputStream out = Files.newOutputStream(target)) {
                System.out.println("Modifying and renaming file. [" + originalName + "]");
                write(doc, out);
            }

            // Remove old file
            Files.delete(transcriptionPath);
        }
    }

    private static Document getDocument(Path transcriptionPath) throws Exception {
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = builderFactory.newDocumentBuilder();

        try (InputStream in = Files.newInputStream(transcriptionPath)) {
            return builder.parse(in);
        }
    }

    /**
     * @param doc document
     * @param out output stream
     */
    private static void write(Document doc, OutputStream out) {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = null;
        try {
            transformer = transformerFactory.newTransformer();

            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            // Options to make it human readable
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputPropertiesFactory.S_KEY_INDENT_AMOUNT, "4");
        } catch (TransformerConfigurationException e) {
            return;
        }

        Source xmlSource = new DOMSource(doc);
        Result result = new StreamResult(out);

        try {
            transformer.transform(xmlSource, result);
        } catch (TransformerException e) {
            return;
        }
    }
}
