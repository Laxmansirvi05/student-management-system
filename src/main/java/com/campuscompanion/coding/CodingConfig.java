package com.campuscompanion.coding;

import com.campuscompanion.coding.platform.CodeChefReader;
import com.campuscompanion.coding.platform.CodeforcesReader;
import com.campuscompanion.coding.platform.GeeksforGeeksReader;
import com.campuscompanion.coding.platform.GitHubReader;
import com.campuscompanion.coding.platform.HackerRankReader;
import com.campuscompanion.coding.platform.LeetCodeReader;
import com.campuscompanion.coding.platform.PlatformHttp;
import com.campuscompanion.coding.platform.PlatformReaders;
import com.campuscompanion.common.config.CodingProperties;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The readers of the six coding sites, sharing one HTTP client. */
@Configuration
public class CodingConfig {

  @Bean
  public PlatformReaders platformReaders(CodingProperties properties) {
    PlatformHttp http = new PlatformHttp(properties.timeout());
    return new PlatformReaders(
        List.of(
            new GitHubReader(http, properties.githubToken()),
            new LeetCodeReader(http),
            new CodeforcesReader(http),
            new CodeChefReader(http),
            new GeeksforGeeksReader(http),
            new HackerRankReader(http)));
  }
}
