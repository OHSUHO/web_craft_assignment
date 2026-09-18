package com.gameexpert;

import com.gameexpert.bootstrap.EngineComponentFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@ComponentScan(excludeFilters = @ComponentScan.Filter(type = FilterType.CUSTOM,
        classes = {EngineComponentFilter.class, TypeExcludeFilter.class,
                AutoConfigurationExcludeFilter.class}))
@EnableJpaAuditing
public class GameExpertApplication {
    public static void main(String[] args) {
        SpringApplication.run(GameExpertApplication.class, args);
    }
}
