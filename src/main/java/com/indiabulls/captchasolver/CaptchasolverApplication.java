package com.indiabulls.captchasolver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CaptchasolverApplication {

	public static void main(String[] args) {
		com.indiabulls.captchasolver.util.SSLUtil.disableSSLVerification();
		SpringApplication.run(CaptchasolverApplication.class, args);


	}

}
