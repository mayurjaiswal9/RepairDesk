package com.acetelecom.repairdesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RepairdeskApplication {

	public static void main(String[] args) {
		SpringApplication.run(RepairdeskApplication.class, args);
	}

}
