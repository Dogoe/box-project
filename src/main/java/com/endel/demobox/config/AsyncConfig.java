package com.endel.demobox.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Lets slow side effects (sending order emails) run off the request thread. */
@Configuration
@EnableAsync
public class AsyncConfig {
}
