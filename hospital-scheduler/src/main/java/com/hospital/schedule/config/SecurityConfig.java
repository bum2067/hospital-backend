package com.hospital.schedule.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            // 🔓 CSRF 비활성화 (개발용)
            .csrf(csrf -> csrf.disable())

            // 🔓 모든 요청 허용 (로그인 없이 접근 가능)
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            )

            // 🔒 로그인 화면, 세션 등 기본 기능 비활성화
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable());

        return http.build();
    }
}
