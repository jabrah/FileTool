package edu.jhu.tool;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.UnrecognizedOptionException;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static final String DEFAULT_SCHEMA_URL = "http://www.livesandletters.ac.uk/schema/aor_20141118.xsd";
    private static LSResourceResolver resourceResolver = new CachingUrlLSResourceResolver();

    public static void main(String[] args) throws IOException, ParseException, SAXException {
        Options options = new Options();
        options.addOption(new Option("r", "recurse"));
        options.addOption(new Option("v", "verbose"));
        options.addOption(new Option("schema", true, "URL for a schema to validate against."));

        CommandLineParser parser = new BasicParser();
//        try {
            CommandLine cmd = parser.parse(options, args);
            run(cmd);
//        } catch (UnrecognizedOptionException e) {
//            System.out.println("");
//        }
    }

    private static void run(CommandLine cmd) throws IOException, SAXException {
        String[] args = cmd.getArgs();

        boolean recurse = cmd.hasOption("r");
        boolean verbose = cmd.hasOption("v");
        String schemaUrl = cmd.hasOption("schema") ? cmd.getOptionValue("schema") : DEFAULT_SCHEMA_URL;

        SchemaFactory sFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = sFactory.newSchema(new URL(schemaUrl));

        for (String path : args) {
            Path p = Paths.get(path);

            if (Files.notExists(p)) {
                continue;
            }

            if (Files.isDirectory(p)) {
                handle_directory(p, schema, recurse, verbose);
            } else if (Files.isRegularFile(p)) {
                handle_file(p, schema, verbose);
            }
        }
    }

    private static void handle_directory(Path path, Schema schema, boolean recurse, boolean verbose) throws IOException {
        if (path.getFileName().toString().startsWith(".")) {
            return;
        }

        System.out.println("\n\nValidating files in directory [" + path.toString() + "]");
        try (DirectoryStream<Path> files = Files.newDirectoryStream(path)) {
            for (Path p : files) {
                if (Files.isDirectory(p) && recurse) {
                    handle_directory(p, schema, true, verbose);
                } else if (Files.isRegularFile(p)) {
                    handle_file(p, schema, verbose);
                }
            }
        }
    }

    private static void handle_file(Path path, Schema schema, boolean verbose) {
        String filename = path.getFileName().toString();
        if (filename.startsWith(".") || !filename.endsWith(".xml")) {
            return;
        }

        Validator validator = schema.newValidator();
        validator.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException e) throws SAXException {
                System.out.println("    [WARNING] " + e.getSystemId() + ":" + e.getPublicId() + " ("
                        + e.getLineNumber() + ":" + e.getColumnNumber() + ") - " + e.getMessage());
            }

            @Override
            public void error(SAXParseException e) throws SAXException {
                System.out.println("    [ERROR] " + e.getSystemId() + ":" + e.getPublicId() + " ("
                        + e.getLineNumber() + ":" + e.getColumnNumber() + ") - " + e.getMessage());
            }

            @Override
            public void fatalError(SAXParseException e) throws SAXException {
                System.out.println("    [FATAL ERROR] " + e.getSystemId() + ":" + e.getPublicId() + " ("
                        + e.getLineNumber() + ":" + e.getColumnNumber() + ") - " + e.getMessage());
            }
        });
        validator.setResourceResolver(resourceResolver);

        System.out.println("  Validating file: [" + path.toString() + "]");
        try {
            if (verbose) {
                validator.validate(
                        new StreamSource(Files.newInputStream(path)),
                        new StreamResult(System.out)
                );
            } else {
                validator.validate(new StreamSource(Files.newInputStream(path)));
            }
        } catch (IOException | SAXException e) {
            if (verbose) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                e.printStackTrace(new PrintStream(out));

                System.out.println("    [EXCEPTION] validating file. (" + path.toString() + ")\n    " + out.toString());
            } else {
                System.out.println("    [EXCEPTION] validating file. (" + path.toString() + ")\n    " + e.getMessage());
            }
        }
    }

}
