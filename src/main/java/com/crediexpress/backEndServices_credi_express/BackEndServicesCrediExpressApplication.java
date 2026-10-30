package com.crediexpress.backEndServices_credi_express;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// La configuración de JPA vive en com.crediexpress.config.JpaConfig para que las pruebas
// de controllers (@WebMvcTest) no intenten levantar la base de datos.
@SpringBootApplication(scanBasePackages = "com.crediexpress")
public class BackEndServicesCrediExpressApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackEndServicesCrediExpressApplication.class, args);
	}

}
