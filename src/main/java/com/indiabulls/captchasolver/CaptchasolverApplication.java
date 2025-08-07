package com.indiabulls.captchasolver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CaptchasolverApplication {

	public static void main(String[] args) {
		com.indiabulls.captchasolver.util.SSLUtil.disableSSLVerification();
		SpringApplication.run(CaptchasolverApplication.class, args);


	}

}
