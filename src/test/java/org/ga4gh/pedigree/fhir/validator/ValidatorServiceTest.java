package org.ga4gh.pedigree.fhir.validator;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class ValidatorServiceTest {

  private static final Log log = LogFactory.getLog(ValidatorServiceTest.class);

  @Test
  public void testValidPedigree() {
    log.info("Running testValidPedigree");
    ValidatorService validator = new ValidatorService(false, null);

    Bundle pedigree = new Bundle();
    pedigree.getMeta().addProfile("http://purl.org/ga4gh/pedigree-fhir-ig/StructureDefinition/Pedigree");
    pedigree.setType(Bundle.BundleType.DOCUMENT);
    pedigree.setIdentifier(new Identifier().setSystem("http://purl.org/ga4gh/pedigree-fhir-ig").setValue("1"));
    pedigree.setTimestamp(Calendar.getInstance().getTime());

    Organization csiro = new Organization();
    csiro.setName("CSIRO");
    csiro.setId("csiro");

    Patient bart = new Patient();
    bart.getMeta().addProfile("http://purl.org/ga4gh/pedigree-fhir-ig/StructureDefinition/PedigreeIndividual");
    bart.setId("bart");

    Patient homer = new Patient();
    homer.getMeta().addProfile("http://purl.org/ga4gh/pedigree-fhir-ig/StructureDefinition/PedigreeIndividual");
    homer.setId("homer");

    FamilyMemberHistory rel = new FamilyMemberHistory();
    rel.setId("rel");
    rel.setStatus(FamilyMemberHistory.FamilyHistoryStatus.COMPLETED);
    rel.getMeta().addProfile("http://purl.org/ga4gh/pedigree-fhir-ig/StructureDefinition/PedigreeRelationship");
    rel.setPatient(new Reference(bart));
    Extension relative = rel.addExtension();
    relative
      .setUrl("http://hl7.org/fhir/StructureDefinition/familymemberhistory-patient-record")
      .setValue(new Reference(homer));
    CodeableConcept relCode = new CodeableConcept();
    relCode.addCoding()
      .setSystem("http://purl.org/ga4gh/kin.fhir")
      .setCode("KIN:040")
      .setDisplay("hasBiologicalFather");
    rel.setRelationship(relCode);

    Condition adhd = new Condition();
    adhd.setId("adhd");
    CodeableConcept adhdCode = new CodeableConcept();
    adhdCode.addCoding().setSystem("http://snomed.info/sct").setCode("406506008");
    adhd.setCode(adhdCode);
    adhd.setSubject(new Reference(bart));

    Composition comp = new Composition();
    comp.setStatus(Composition.CompositionStatus.FINAL);
    CodeableConcept t = new CodeableConcept();
    t.addCoding().setSystem("http://snomed.info/sct").setCode("422432008");
    comp.setType(t);
    comp.setDate(Calendar.getInstance().getTime());
    comp.addAuthor(new Reference(csiro));
    comp.setTitle("Pedigree");
    comp.setSubject(new Reference(bart));

    CodeableConcept probandCode = new CodeableConcept();
    probandCode.addCoding()
      .setSystem("http://purl.org/ga4gh/pedigree-fhir-ig/CodeSystem/SectionType")
      .setCode("proband");
    comp.addSection()
      .setTitle("Proband")
      .setCode(probandCode)
      .addEntry(new Reference(bart));

    CodeableConcept reasonCode = new CodeableConcept();
    reasonCode.addCoding()
      .setSystem("http://purl.org/ga4gh/pedigree-fhir-ig/CodeSystem/SectionType")
      .setCode("reason");
    comp.addSection()
      .setTitle("Reason")
      .setCode(reasonCode)
      .addEntry(new Reference(adhd));

    CodeableConcept individualsCode = new CodeableConcept();
    individualsCode.addCoding()
      .setSystem("http://purl.org/ga4gh/pedigree-fhir-ig/CodeSystem/SectionType")
      .setCode("individuals");
    Composition.SectionComponent individualsSection = comp.addSection()
      .setTitle("Individuals")
      .setCode(individualsCode);
    individualsSection.addEntry(new Reference(bart));
    individualsSection.addEntry(new Reference(homer));

    CodeableConcept relationshipsCode = new CodeableConcept();
    relationshipsCode.addCoding()
      .setSystem("http://purl.org/ga4gh/pedigree-fhir-ig/CodeSystem/SectionType")
      .setCode("relationships");
    Composition.SectionComponent relationshipsSection = comp.addSection()
      .setTitle("Relationships")
      .setCode(relationshipsCode);
    relationshipsSection.addEntry(new Reference(rel));

    Bundle.BundleEntryComponent bec = pedigree.addEntry().setResource(comp).setFullUrl("http://purl.org/ga4gh/pedigree-fhir-ig/test/pedigree");
    bec.addLink().setUrl("http://purl.org/ga4gh/pedigree-fhir-ig/test/csiro").setRelation("item");
    bec.addLink().setUrl("http://purl.org/ga4gh/pedigree-fhir-ig/test/bart").setRelation("item");
    bec.addLink().setUrl("http://purl.org/ga4gh/pedigree-fhir-ig/test/homer").setRelation("item");
    bec.addLink().setUrl("http://purl.org/ga4gh/pedigree-fhir-ig/test/rel").setRelation("item");
    bec.addLink().setUrl("http://purl.org/ga4gh/pedigree-fhir-ig/test/adhd").setRelation("item");
    pedigree.addEntry().setResource(csiro).setFullUrl("http://purl.org/ga4gh/pedigree-fhir-ig/test/csiro");
    pedigree.addEntry().setResource(bart).setFullUrl("http://purl.org/ga4gh/pedigree-fhir-ig/test/bart");
    pedigree.addEntry().setResource(homer).setFullUrl("http://purl.org/ga4gh/pedigree-fhir-ig/test/homer");
    pedigree.addEntry().setResource(rel).setFullUrl("http://purl.org/ga4gh/pedigree-fhir-ig/test/rel");
    pedigree.addEntry().setResource(adhd).setFullUrl("http://purl.org/ga4gh/pedigree-fhir-ig/test/adhd");

    ValidationResult res = validator.validate(pedigree);
    for (SingleValidationMessage message : res.getMessages()) {
      System.out.println(message.toString());
    }

    assertTrue(res.isSuccessful());
  }

  @Test
  public void testPedigreeWithCycle() {
    log.info("Running testPedigreeWithCycle");
    ValidatorService validator = new ValidatorService(false, null);
    Bundle pedigree = createPedigreeWithCycle();

    System.out.println(FhirContext.forR4().newJsonParser().setPrettyPrint(true).encodeResourceToString(pedigree));
    System.exit(1);

    ValidationResult res = validator.validate(pedigree);
    for (SingleValidationMessage message : res.getMessages()) {
      System.out.println(message.toString());
    }

    assertTrue(res.isSuccessful());
  }

  @Test
  public void testPedigreeWithCycleAndReasoner() {
    log.info("Running testPedigreeWithCycleAndReasoner");
    ValidatorService validator = new ValidatorService(true, null);
    Bundle pedigree = createPedigreeWithCycle();

    ValidationResult res = validator.validate(pedigree);
    for (SingleValidationMessage message : res.getMessages()) {
      System.out.println(message.toString());
    }

    assertFalse(res.isSuccessful());
  }

  @Test
  public void testValidPedigreeFromFile() throws IOException {
    log.info("Running testValidPedigreeFromFile");
    ValidatorService validator = new ValidatorService(false, null);

    File file = loadFileFromClassPath("open-pedigree-GA4GH-fhir.json");

    ValidationResult res = validator.validate(file);
    for (SingleValidationMessage message : res.getMessages()) {
      System.out.println(message.toString());
    }

    assertTrue(res.isSuccessful());
  }

  public static File loadFileFromClassPath(String name) {
    ClassLoader classLoader = ValidatorServiceTest.class.getClassLoader();
    return new File(Objects.requireNonNull(classLoader.getResource(name)).getFile());
  }

  private Bundle createPedigreeWithCycle() {
    Bundle pedigree = new Bundle();
    pedigree.getMeta().addProfile("http://purl.org/ga4gh/pedigree-fhir-ig/StructureDefinition/Pedigree");
    pedigree.setType(Bundle.BundleType.DOCUMENT);
    pedigree.setIdentifier(new Identifier().setSystem("http://purl.org/ga4gh/pedigree-fhir-ig").setValue("1"));
    pedigree.setTimestamp(Calendar.getInstance().getTime());

    Organization csiro = new Organization();
    csiro.setName("CSIRO");
    csiro.setId("csiro");

    Patient bart = new Patient();
    bart.getMeta().addProfile("http://purl.org/ga4gh/pedigree-fhir-ig/StructureDefinition/PedigreeIndividual");
    bart.setId("bart");

    Patient homer = new Patient();
    homer.getMeta().addProfile("http://purl.org/ga4gh/pedigree-fhir-ig/StructureDefinition/PedigreeIndividual");
    homer.setId("homer");

    FamilyMemberHistory rel = new FamilyMemberHistory();
    rel.setId("rel");
    rel.setStatus(FamilyMemberHistory.FamilyHistoryStatus.COMPLETED);
    rel.getMeta().addProfile("http://purl.org/ga4gh/pedigree-fhir-ig/StructureDefinition/PedigreeRelationship");
    rel.setPatient(new Reference(bart));
    Extension relative = rel.addExtension();
    relative
      .setUrl("http://hl7.org/fhir/StructureDefinition/familymemberhistory-patient-record")
      .setValue(new Reference(homer));
    CodeableConcept relCode = new CodeableConcept();
    relCode.addCoding()
      .setSystem("http://purl.org/ga4gh/kin.fhir")
      .setCode("KIN:003")
      .setDisplay("isBiologicalParent");
    rel.setRelationship(relCode);

    FamilyMemberHistory invRel = new FamilyMemberHistory();
    invRel.setId("invRel");
    invRel.setStatus(FamilyMemberHistory.FamilyHistoryStatus.COMPLETED);
    invRel.getMeta().addProfile("http://purl.org/ga4gh/pedigree-fhir-ig/StructureDefinition/PedigreeRelationship");
    invRel.setPatient(new Reference(bart));
    Extension invRelative = invRel.addExtension();
    invRelative
      .setUrl("http://hl7.org/fhir/StructureDefinition/familymemberhistory-patient-record")
      .setValue(new Reference(homer));
    CodeableConcept invRelCode = new CodeableConcept();
    invRelCode.addCoding()
      .setSystem("http://purl.org/ga4gh/kin.fhir")
      .setCode("KIN:032")
      .setDisplay("isBiologicalChild");
    invRel.setRelationship(invRelCode);

    Condition adhd = new Condition();
    adhd.setId("adhd");
    CodeableConcept adhdCode = new CodeableConcept();
    adhdCode.addCoding().setSystem("http://snomed.info/sct").setCode("406506008");
    adhd.setCode(adhdCode);
    adhd.setSubject(new Reference(bart));

    Composition comp = new Composition();
    comp.setStatus(Composition.CompositionStatus.FINAL);
    CodeableConcept t = new CodeableConcept();
    t.addCoding().setSystem("http://snomed.info/sct").setCode("422432008");
    comp.setType(t);
    comp.setDate(Calendar.getInstance().getTime());
    comp.addAuthor(new Reference(csiro));
    comp.setTitle("Pedigree");
    comp.setSubject(new Reference(bart));

    CodeableConcept probandCode = new CodeableConcept();
    probandCode.addCoding()
      .setSystem("http://purl.org/ga4gh/pedigree-fhir-ig/CodeSystem/SectionType")
      .setCode("proband");
    comp.addSection()
      .setTitle("Proband")
      .setCode(probandCode)
      .addEntry(new Reference(bart));

    CodeableConcept reasonCode = new CodeableConcept();
    reasonCode.addCoding()
      .setSystem("http://purl.org/ga4gh/pedigree-fhir-ig/CodeSystem/SectionType")
      .setCode("reason");
    comp.addSection()
      .setTitle("Reason")
      .setCode(reasonCode)
      .addEntry(new Reference(adhd));

    CodeableConcept individualsCode = new CodeableConcept();
    individualsCode.addCoding()
      .setSystem("http://purl.org/ga4gh/pedigree-fhir-ig/CodeSystem/SectionType")
      .setCode("individuals");
    Composition.SectionComponent individualsSection = comp.addSection()
      .setTitle("Individuals")
      .setCode(individualsCode);
    individualsSection.addEntry(new Reference(bart));
    individualsSection.addEntry(new Reference(homer));

    CodeableConcept relationshipsCode = new CodeableConcept();
    relationshipsCode.addCoding()
      .setSystem("http://purl.org/ga4gh/pedigree-fhir-ig/CodeSystem/SectionType")
      .setCode("relationships");
    Composition.SectionComponent relationshipsSection = comp.addSection()
      .setTitle("Relationships")
      .setCode(relationshipsCode);
    relationshipsSection.addEntry(new Reference(rel));
    relationshipsSection.addEntry(new Reference(invRel));

    Bundle.BundleEntryComponent bec = pedigree.addEntry().setResource(comp).setFullUrl("http://purl.org/ga4gh/pedigree-fhir-ig/test/pedigree");
    bec.addLink().setUrl("http://purl.org/ga4gh/pedigree-fhir-ig/test/csiro").setRelation("item");
    bec.addLink().setUrl("http://purl.org/ga4gh/pedigree-fhir-ig/test/bart").setRelation("item");
    bec.addLink().setUrl("http://purl.org/ga4gh/pedigree-fhir-ig/test/homer").setRelation("item");
    bec.addLink().setUrl("http://purl.org/ga4gh/pedigree-fhir-ig/test/rel").setRelation("item");
    bec.addLink().setUrl("http://purl.org/ga4gh/pedigree-fhir-ig/test/inv-rel").setRelation("item");
    bec.addLink().setUrl("http://purl.org/ga4gh/pedigree-fhir-ig/test/adhd").setRelation("item");
    pedigree.addEntry().setResource(csiro).setFullUrl("http://purl.org/ga4gh/pedigree-fhir-ig/test/csiro");
    pedigree.addEntry().setResource(bart).setFullUrl("http://purl.org/ga4gh/pedigree-fhir-ig/test/bart");
    pedigree.addEntry().setResource(homer).setFullUrl("http://purl.org/ga4gh/pedigree-fhir-ig/test/homer");
    pedigree.addEntry().setResource(rel).setFullUrl("http://purl.org/ga4gh/pedigree-fhir-ig/test/rel");
    pedigree.addEntry().setResource(invRel).setFullUrl("http://purl.org/ga4gh/pedigree-fhir-ig/test/inv-rel");
    pedigree.addEntry().setResource(adhd).setFullUrl("http://purl.org/ga4gh/pedigree-fhir-ig/test/adhd");
    return pedigree;
  }
}
