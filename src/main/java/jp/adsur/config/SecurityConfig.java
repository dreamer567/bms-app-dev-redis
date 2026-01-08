//package jp.adsur.config;
//
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.http.HttpMethod;
//import org.springframework.security.config.Customizer;
//import org.springframework.security.config.annotation.web.builders.HttpSecurity;
//import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
//import org.springframework.security.config.http.SessionCreationPolicy;
//import org.springframework.security.web.SecurityFilterChain;
//
//@Configuration
//@EnableWebSecurity
//public class SecurityConfig {
//
////    @Value("${scim.token}")
////    private String scimToken;
////
////    @Bean
////    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
////        http
////                .csrf(csrf -> csrf.disable())
////                .cors(cors -> cors.disable())
////                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
////                .authorizeHttpRequests(auth -> auth
////                        .requestMatchers("/scim/**").permitAll() // 必须
////                        .requestMatchers("/greeting").permitAll() // 可选
////                        .requestMatchers("/test**").permitAll() // 可选
////                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // 必须
////                        .anyRequest().authenticated()
////                );
////        http.httpBasic(Customizer.withDefaults());
////
////        return http.build();
////    }
//
//    @Bean
//    public SecurityFilterChain filterChainNew(HttpSecurity http) throws Exception {
//        http
//                // ✅ 6.1+対応：Lambda式でBasic認証を無効化（非推奨のメソッド代替）
//                .httpBasic(httpBasic -> httpBasic.disable())
//                // CSRF無効（内部テスト/API用に推奨）
//                .csrf(csrf -> csrf.disable())
//                // すべての接口を認証なしでアクセス可能に
//                .authorizeHttpRequests(auth -> auth
//                        .anyRequest().permitAll()
//                )
//                // 不要なセキュリティ機能を無効化（追加安定化）
//                .sessionManagement(session -> session.disable())
//                .formLogin(form -> form.disable())
//                .logout(logout -> logout.disable());
//
//        return http.build();
//    }
//}
