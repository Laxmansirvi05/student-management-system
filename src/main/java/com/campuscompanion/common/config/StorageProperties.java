package com.campuscompanion.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Supabase Storage settings. Used from task T3-03. The service key is a server-side secret. */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String supabaseUrl, String serviceKey, String bucket) {

  @Override
  public String toString() {
    return "StorageProperties[supabaseUrl=" + supabaseUrl + ", bucket=" + bucket + "]";
  }
}
