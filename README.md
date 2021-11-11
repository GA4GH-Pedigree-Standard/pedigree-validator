# Pedigree Validator

This is a simple command line application that shows how validation of a FHIR pedigree file can be implemented using the HAPI FHIR libraries and the artifacts produced by the FHIR implementation guide.

It also shows how an OWL reasoner can be used to implement additional validation based on the KIN ontology.

The application is written in Java and can be built using Maven. To build the application run:

```mvn package```

You can then run the command line application using:

```java -jar pedigree-validator-0.0.1-SNAPSHOT.jar [pedigree file]```

An example pedigree file can be found in `src\test\resources\open-pedigree-GA4GH-fhir.json`.

By default, the application does not use a terminology server or the reasoner to validate the pedigree file. To use a terminology server you can add the `-t [server]` flag. A public instance of Ontoserver, CSIRO's terminology server, is available at `https://r4.ontoserver.csiro.au/`. To validate using the reasoner and the KIN ontology you can add the `-r` flag.

