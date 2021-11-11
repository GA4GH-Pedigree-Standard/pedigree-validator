package org.ga4gh.pedigree.fhir.validator;

import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import org.apache.commons.cli.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.System.exit;

/**
 * Main implementation of the command line interface.
 *
 * @author Alejandro Metke Jimenez
 */
public class CommandLineInterface {

  /** Logger. */
  private static final Log log = LogFactory.getLog(CommandLineInterface.class);

  public void run(String[] args) {

    Options options = new Options();
    options.addOption("r", "reasoner", false, "Flag to indicate if the reasoner should be used in validation");
    options.addOption("t", "terminologyServer", true, "The URL of an external terminology server to use for " +
      "validation");
    options.addOption(new Option("help", "Print this message"));

    CommandLineParser parser = new DefaultParser();

    try {
      // Parse the command line arguments
      CommandLine line = parser.parse(options, args);
      if (line.getArgList().isEmpty()) {
        printError("No argument was supplied");
        printUsage(options);
        exit(0);
      }

      // Get base folder where the validator will run
      File baseFolderOrFile = new File(line.getArgList().get(0));
      if (!baseFolderOrFile.exists()) {
        printError("Folder or file " + baseFolderOrFile.getAbsolutePath() + " does not exist");
        exit(0);
      }

      List<File> jsonFiles = null;
      if (baseFolderOrFile.isFile()) {
        jsonFiles = new ArrayList<>();
        jsonFiles.add(baseFolderOrFile);
      } else {
        // Get .json files
        try (Stream<Path> walk = Files.walk(baseFolderOrFile.toPath())) {
          jsonFiles = walk
            .filter(p -> !Files.isDirectory(p))
            .map(Path::toFile)
            .filter(f -> f.getName().endsWith(".json"))
            .collect(Collectors.toList());
        } catch (IOException e) {
          printError("There was an I/O issue: " + e.getLocalizedMessage());
          System.exit(-1);
        }
      }

      if (jsonFiles.isEmpty()) {
        printError("There are no pedigree files to validate! Files should be in FHIR JSON format");
        System.exit(0);
      }

      try {
        for (File jsonFile : jsonFiles) {
          boolean useReasoner = line.hasOption("r");
          String terminologyServer = line.getOptionValue("t");
          printInfo("Validating pedigree file " + jsonFile + (useReasoner ? " with" : "without") + " reasoner support"
            + ((terminologyServer != null) ? " using terminology server " + terminologyServer : ""));
          ValidatorService service = new ValidatorService(useReasoner, terminologyServer);
          ValidationResult vr = service.validate(jsonFile);
          if (vr.isSuccessful()) {
            printInfo("Validation was successful");
            for(SingleValidationMessage msg : vr.getMessages()) {
              if (msg.getSeverity().equals(ResultSeverityEnum.WARNING)) {
                printValidationMessage(msg);
              }
            }
          } else {
            for(SingleValidationMessage msg : vr.getMessages()) {
              printValidationMessage(msg);
            }
          }
        }

      } catch (Throwable t) {
        log.error("There was a problem validating the pedigree files: " + t.getLocalizedMessage());
        t.printStackTrace();
      }

    } catch (ParseException exp) {
      // oops, something went wrong
      log.error(exp.getMessage());
      printUsage(options);
    }

    exit(0);
  }

  private static void printUsage(Options options) {
    HelpFormatter formatter = new HelpFormatter();
    final PrintWriter writer = new PrintWriter(System.out);
    formatter.printUsage(writer, 80, "pedigree-validator", options);
    writer.flush();
  }

  private void printValidationMessage(SingleValidationMessage msg) {
    String s = msg.getMessage() + "[" + msg.getLocationLine() + "," + msg.getLocationCol() + "]";
    switch (msg.getSeverity()) {
      case INFORMATION:
        log.info(s);
        break;
      case WARNING:
        log.warn(s);
        break;
      case ERROR:
        log.error(s);
        break;
      case FATAL:
        log.fatal(s);
        break;
    }
  }

  private void printInfo(String msg) {
    log.info(msg);
  }

  private void printError(String msg) {
    log.error(msg);
  }

}
