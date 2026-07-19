package com.hana.omniconnect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HanaOmniConnectApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(HanaOmniConnectApiApplication.class, args);
	}

}
