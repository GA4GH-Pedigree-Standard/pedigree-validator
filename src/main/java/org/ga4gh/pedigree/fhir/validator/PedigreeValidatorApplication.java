package org.ga4gh.pedigree.fhir.validator;

import org.springframework.boot.Banner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PedigreeValidatorApplication implements CommandLineRunner {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(PedigreeValidatorApplication.class);
		app.setBannerMode(Banner.Mode.OFF);
		app.run(args);
	}

	@Override
	public void run(String... args) {
		CommandLineInterface cli = new CommandLineInterface();
		cli.run(args);
	}

}
