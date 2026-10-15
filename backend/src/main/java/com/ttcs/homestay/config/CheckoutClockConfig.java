package com.ttcs.homestay.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CheckoutClockConfig {
    @Bean
    Clock checkoutClock() {
        return Clock.system(ZoneId.of("Asia/Ho_Chi_Minh"));
    }
}
