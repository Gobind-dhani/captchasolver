package com.indiabulls.captchasolver;

import org.springframework.boot.SpringApplication;

public class TestCaptchasolverApplication {

	public static void main(String[] args) {
		SpringApplication.from(CaptchasolverApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
