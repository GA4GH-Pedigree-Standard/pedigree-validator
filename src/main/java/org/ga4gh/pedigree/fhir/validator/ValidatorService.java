package org.ga4gh.pedigree.fhir.validator;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hl7.fhir.common.hapi.validation.support.*;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.semanticweb.owl.explanation.api.Explanation;
import org.semanticweb.owl.explanation.api.ExplanationGenerator;
import org.semanticweb.owl.explanation.impl.blackbox.checker.InconsistentOntologyExplanationGeneratorFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import uk.ac.manchester.cs.jfact.JFactFactory;

import java.io.*;
import java.util.*;

/**
 * Main validator service.
 *
 * @author Alejandro Metke
 */
public class ValidatorService {

  /** Logger. */
  private static final Log log = LogFactory.getLog(ValidatorService.class);

  public static final IRI FH_IRI = IRI.create("http://purl.org/ga4gh/kin.owl#");
  public static String FH_PAT_REC_EXT = "http://hl7.org/fhir/StructureDefinition/familymemberhistory-patient-record";

  private final FhirContext ctx;
  private final FhirValidator validator;
  private OWLOntologyManager manager;
  private OWLOntology fhOntology;
  private OWLDataFactory dataFactory;
  private OWLReasonerFactory reasonerFactory = null;
  private OWLReasoner reasoner;
  private final boolean useReasoner;

  public ValidatorService(boolean useReasoner, String terminologyServer) {
    this.ctx = FhirContext.forR4();
    this.useReasoner = useReasoner;

    try {
      log.info("Initialising validator");
      NpmPackageValidationSupport npmPackageSupport = new NpmPackageValidationSupport(ctx);
      npmPackageSupport.loadPackageFromClasspath("package.tgz");

      RemoteTerminologyServiceValidationSupport terminologyValidationSupport = null;
      if (terminologyServer != null) {
        terminologyValidationSupport = new RemoteTerminologyServiceValidationSupport(ctx);
        terminologyValidationSupport.setBaseUrl(terminologyServer);
      }
      UnknownCodeSystemWarningValidationSupport unknown = new UnknownCodeSystemWarningValidationSupport(ctx);
      unknown.setAllowNonExistentCodeSystem(true);

      // Create a support chain including the NPM Package Support
      ValidationSupportChain validationSupportChain = null;
      if (terminologyValidationSupport != null) {
        validationSupportChain = new ValidationSupportChain(
          new DefaultProfileValidationSupport(ctx),
          terminologyValidationSupport,
          new InMemoryTerminologyServerValidationSupport(ctx),
          npmPackageSupport,
          new SnapshotGeneratingValidationSupport(ctx),
          unknown
        );
      } else {
        validationSupportChain = new ValidationSupportChain(
          new DefaultProfileValidationSupport(ctx),
          new InMemoryTerminologyServerValidationSupport(ctx),
          npmPackageSupport,
          new SnapshotGeneratingValidationSupport(ctx),
          unknown
        );
      }
      CachingValidationSupport validationSupport = new CachingValidationSupport(validationSupportChain);

      this.validator = ctx.newValidator();
      FhirInstanceValidator instanceValidator = new FhirInstanceValidator(validationSupport);
      validator.registerValidatorModule(instanceValidator);
      log.info("Done");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public ValidationResult validate(Bundle pedigree) {
    // Validate using FHIR validator
    ValidationResult vr =  validator.validateWithResult(pedigree);

    // Validate using reasoner
    if (useReasoner) {
      if (reasoner == null) {
        log.info("Initialising reasoner");
        initResoner();
        log.info("Done");
      }

      // Build OWL graph based on FHIR model
      manager.addAxioms(fhOntology, fhirToOwl(pedigree).stream());

      // Flush, classify and check consistency
      reasoner.flush();
      reasoner.precomputeInferences();

      if (!reasoner.isConsistent()) {
        InconsistentOntologyExplanationGeneratorFactory genFac
          = new InconsistentOntologyExplanationGeneratorFactory(
          reasonerFactory,
          dataFactory,
          this::getOntologyManager,
          9223372036854775807L
        );
        ExplanationGenerator<OWLAxiom> gen = genFac.createExplanationGenerator(fhOntology);
        OWLAxiom ax = dataFactory.getOWLSubClassOfAxiom(dataFactory.getOWLThing(), dataFactory.getOWLNothing());
        StringBuilder sb = new StringBuilder();
        Set<Explanation<OWLAxiom>> explanations = gen.getExplanations(ax, 5);
        for (Explanation<OWLAxiom> explanation : explanations) {
          int i = 1;
          for(OWLAxiom justification : explanation.getAxioms()) {
            sb.append(" - ");
            sb.append(justification.toString());
            sb.append(System.lineSeparator());
          }
        }

        List<SingleValidationMessage> allMessages = new ArrayList<>(vr.getMessages());
        SingleValidationMessage reasonerMessage = new SingleValidationMessage();
        reasonerMessage.setMessage("The pedigree is inconsistent:" + System.lineSeparator() + sb.toString().trim());
        reasonerMessage.setSeverity(ResultSeverityEnum.ERROR);
        reasonerMessage.setLocationCol(0);
        reasonerMessage.setLocationLine(0);
        allMessages.add(reasonerMessage);

        return new ValidationResult(ctx, allMessages);
      }
    }

    return vr;
  }

  public ValidationResult validate(File pedigreeFile) throws IOException {
    log.info("Validating file " + pedigreeFile.getName());

    // Load pedigree bundle
    Bundle pedigree = null;
    try(FileReader fr = new FileReader(pedigreeFile)) {
      IBaseResource res = ctx.newJsonParser().parseResource(fr);
      if (res instanceof Bundle) {
        pedigree = (Bundle) res;
      } else {
        SingleValidationMessage msg = new SingleValidationMessage();
        msg.setMessage("File " + pedigreeFile.getName() + " does not seem to contain a pedigree (resource type is "
          + res.fhirType() + " but should be Composition");
        msg.setSeverity(ResultSeverityEnum.FATAL);
        return new ValidationResult(ctx, Collections.singletonList(msg));
      }
    }
    return validate(pedigree);
  }

  public Set<OWLAxiom> fhirToOwl(Bundle pedigree) {
    Set<OWLAxiom> axioms = new HashSet<>();

    OWLClass person = getNamedClass("KIN_998");

    Map<String, Patient> patientsMap = getPatients(pedigree);
    Map<String, OWLNamedIndividual> individualsMap = new HashMap<>();

    for (Patient p : patientsMap.values()) {
      OWLNamedIndividual ind = getNamedIndividual(p.getId());
      individualsMap.put(p.getId(), ind);
      axioms.add(dataFactory.getOWLClassAssertionAxiom(person, ind));
    }

    for (FamilyMemberHistory rel : getRelationships(pedigree)) {
      OWLNamedIndividual individual = individualsMap.get(rel.getPatient().getResource().getIdElement().getValue());
      Reference relativeRef = (Reference) rel.getExtensionByUrl(FH_PAT_REC_EXT).getValue();
      OWLNamedIndividual relative = individualsMap.get(relativeRef.getResource().getIdElement().getValue());
      String relCode = rel.getRelationship().getCodingFirstRep().getCode();

      OWLObjectProperty biologicalParent = getNamedObjectProperty(relCode.replace(':', '_'));
      axioms.add(dataFactory.getOWLObjectPropertyAssertionAxiom(biologicalParent, individual, relative));
    }

    return axioms;
  }

  private Map<String, Patient> getPatients(Bundle b) {
    Map<String, Patient> patients = new HashMap<>();
    for (Bundle.BundleEntryComponent bec : b.getEntry()) {
      if(bec.hasResource()) {
        Resource res = bec.getResource();
        if (res instanceof Patient) {
          Patient p = (Patient) res;
          if (p.hasMeta() &&
            p.getMeta().hasProfile("http://purl.org/ga4gh/pedigree-fhir-ig/StructureDefinition/PedigreeIndividual")) {
            patients.put(p.getId(), p);
          }
        }
      }
    }
    return patients;
  }

  private List<FamilyMemberHistory> getRelationships(Bundle b) {
    List<FamilyMemberHistory> rels = new ArrayList<>();
    for (Bundle.BundleEntryComponent bec : b.getEntry()) {
      if(bec.hasResource()) {
        Resource res = bec.getResource();
        if (res instanceof FamilyMemberHistory) {
          FamilyMemberHistory r = (FamilyMemberHistory) res;
          if (r.hasMeta() &&
            r.getMeta().hasProfile("http://purl.org/ga4gh/pedigree-fhir-ig/StructureDefinition/PedigreeRelationship")) {
            rels.add(r);
          }
        }
      }
    }
    return rels;
  }

  private void initResoner() {
    try {
      this.reasonerFactory = new JFactFactory();
      this.manager = OWLManager.createOWLOntologyManager();
      this.dataFactory = manager.getOWLDataFactory();
      try (InputStream is = getFileFromResourceAsStream("kin.owl")) {
        this.fhOntology = manager.loadOntologyFromOntologyDocument(is);
      }
      reasoner = reasonerFactory.createReasoner(fhOntology);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private InputStream getFileFromResourceAsStream(String fileName) {
    ClassLoader classLoader = getClass().getClassLoader();
    InputStream inputStream = classLoader.getResourceAsStream(fileName);

    if (inputStream == null) {
      throw new IllegalArgumentException("File not found: " + fileName);
    } else {
      return inputStream;
    }
  }

  public OWLOntologyManager getOntologyManager() {
    return this.manager;
  }

  private OWLClass getNamedClass(String id) {
    return dataFactory.getOWLClass(IRI.create(FH_IRI + id));
  }

  private OWLObjectProperty getNamedObjectProperty(String id) {
    return dataFactory.getOWLObjectProperty(IRI.create(FH_IRI + id));
  }

  private OWLNamedIndividual getNamedIndividual(String id) {
    return dataFactory.getOWLNamedIndividual(IRI.create(FH_IRI + id));
  }
}
