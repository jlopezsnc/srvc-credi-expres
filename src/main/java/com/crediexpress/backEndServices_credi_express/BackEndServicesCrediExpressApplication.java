package com.crediexpress.backEndServices_credi_express;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication ( scanBasePackages = "com.crediexpress")
@EnableJpaRepositories(basePackages = "com.crediexpress.dto.repository")

@EntityScan( basePackages = "com.crediexpress.dto.entity")

@EnableAutoConfiguration


public class BackEndServicesCrediExpressApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackEndServicesCrediExpressApplication.class, args);
	}

}
