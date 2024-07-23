package com.cool.core.security;

import com.cool.core.annotation.CoolRestController;
import com.cool.core.annotation.TokenIgnore;
import com.cool.modules.base.security.JwtAuthenticationTokenFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Predicate;

@EnableWebSecurity
@Configuration
@Slf4j
@RequiredArgsConstructor
public class JwtSecurityConfig {

	// 用户详情
	final private UserDetailsService userDetailsService;
	final private JwtAuthenticationTokenFilter jwtAuthenticationTokenFilter;
	// 401
	final private EntryPointUnauthorizedHandler entryPointUnauthorizedHandler;
	// 403
	final private RestAccessDeniedHandler restAccessDeniedHandler;
	// 忽略权限控制的地址
	final private IgnoredUrlsProperties ignoredUrlsProperties;

	final private RequestMappingHandlerMapping requestMappingHandlerMapping;

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity, ApplicationContext applicationContext) throws Exception {
		// 动态获取忽略的URL
		configureIgnoredUrls();

		return httpSecurity
				.authorizeHttpRequests(
						conf -> conf.requestMatchers(
										ignoredUrlsProperties.getUrls().toArray(String[]::new))
								.permitAll().anyRequest().authenticated())
				.headers(config -> config.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable))
				// 允许网页iframe
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(conf -> conf.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.addFilterBefore(jwtAuthenticationTokenFilter,
						UsernamePasswordAuthenticationFilter.class)
				.exceptionHandling(config -> {
					config.authenticationEntryPoint(entryPointUnauthorizedHandler);
					config.accessDeniedHandler(restAccessDeniedHandler);
				}).build();
	}

	private void configureIgnoredUrls() {
		Map<RequestMappingInfo, HandlerMethod> mappings = requestMappingHandlerMapping.getHandlerMethods();
		mappings.forEach((requestMappingInfo, handlerMethod) -> {
			Method method = handlerMethod.getMethod();
			TokenIgnore tokenIgnore = AnnotatedElementUtils.findMergedAnnotation(method, TokenIgnore.class);
			if (tokenIgnore != null) {
				StringBuilder url = new StringBuilder();
				RequestMapping classRequestMapping = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), RequestMapping.class);
				if (classRequestMapping != null) {
					for (String path : classRequestMapping.value()) {
						url.append(path);
					}
				}
				if (requestMappingInfo.getPathPatternsCondition() == null) {
					return;
				}
				// requestMappingInfo.getPathPatternsCondition().getPatterns()
				for (PathPattern path : requestMappingInfo.getPathPatternsCondition().getPatterns()) {
					url.append(path);
				}
				ignoredUrlsProperties.getUrls().add(url.toString());
			}
		});
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new PasswordEncoder() {
			@Override
			public String encode(CharSequence rawPassword) {
				return DigestUtils.md5DigestAsHex(((String) rawPassword).getBytes());
			}

			@Override
			public boolean matches(CharSequence rawPassword, String encodedPassword) {
				return encodedPassword.equals(
						DigestUtils.md5DigestAsHex(((String) rawPassword).getBytes()));
			}
		};
	}

	@Bean
	public AuthenticationProvider authenticationProvider() {
		DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
		authProvider.setUserDetailsService(userDetailsService);
		authProvider.setPasswordEncoder(passwordEncoder());
		return authProvider;
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
			throws Exception {
		return config.getAuthenticationManager();
	}
}
