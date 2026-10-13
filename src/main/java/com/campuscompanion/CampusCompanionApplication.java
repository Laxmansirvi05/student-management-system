package com.campuscompanion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CampusCompanionApplication {

  public static void main(String[] args) {
    SpringApplication.run(CampusCompanionApplication.class, args);
  }
}
