package com.campuscompanion.common.web;

import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * How long a browser may keep the static files. Without this, every response carries Spring
 * Security's "no-store", and each student page downloaded the robot's 2.5 MB again.
 *
 * <ul>
 *   <li>The landing page's built files have a hash of their content in their name, so a changed
 *       file always has a new name: they are kept for a year.
 *   <li>Every other script, style, font, picture and the robot's files are kept but checked again
 *       on each use ("no-cache"). An unchanged file then costs a short 304 answer instead of its
 *       bytes, and a changed one is fetched at once, so a new version is never missed.
 * </ul>
 *
 * Pages (HTML) and API answers keep Spring Security's "no-store": they are never kept. Files that
 * need a login stay "private", so a shared cache never stores them.
 */
@Configuration
public class StaticResourceCaching implements WebMvcConfigurer {

  private static final CacheControl FINGERPRINTED =
      CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();
  private static final CacheControl CHECK_EACH_TIME = CacheControl.noCache().cachePrivate();

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/js/landing/**")
        .addResourceLocations("classpath:/static/js/landing/")
        .setCacheControl(FINGERPRINTED);
    registry
        .addResourceHandler("/css/landing/**")
        .addResourceLocations("classpath:/static/css/landing/")
        .setCacheControl(FINGERPRINTED);
    for (String folder : new String[] {"js", "css", "fonts", "img", "vendor", "robot"}) {
      registry
          .addResourceHandler("/" + folder + "/**")
          .addResourceLocations("classpath:/static/" + folder + "/")
          .setCacheControl(CHECK_EACH_TIME);
    }
  }
}
