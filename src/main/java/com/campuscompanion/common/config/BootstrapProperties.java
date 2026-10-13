package com.campuscompanion.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** First-administrator settings. Used from task T1-08. The password is a secret. */
@ConfigurationProperties(prefix = "app.bootstrap")
public record BootstrapProperties(String adminEmail, String adminPassword) {

  @Override
  public String toString() {
    return "BootstrapProperties[adminEmail=" + adminEmail + "]";
  }
}
