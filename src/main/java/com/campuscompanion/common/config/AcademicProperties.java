package com.campuscompanion.common.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.academic")
public record AcademicProperties(
    @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal attendanceRequiredPercent) {}
