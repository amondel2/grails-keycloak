package com.grails.example

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType

/**
 * Offline stand-in for the auto-configured Keycloak client registration.
 *
 * The application config declares
 * spring.security.oauth2.client.provider.keycloak.issuer-uri, which makes
 * Spring Boot call ClientRegistrations.fromIssuerLocation() while building the
 * clientRegistrationRepository bean. That is an HTTP call to Keycloak's
 * /.well-known/openid-configuration endpoint, so every @Integration spec that
 * autowires a secured controller (and therefore forces the whole security
 * filter chain, and with it the OAuth2 client configuration, to be built)
 * fails at context startup whenever Keycloak is not running.
 *
 * Declaring the bean here makes the auto-configuration back off via its
 * @ConditionalOnMissingBean(ClientRegistration) guard, so discovery never
 * happens. The registration below is a hand-built equivalent pointing at
 * unroutable localhost URLs: these specs never perform a real OIDC handshake,
 * they only need the bean to exist so that method security (@PreAuthorize) can
 * be exercised against a mocked security context.
 *
 * end_session_endpoint is supplied because SecurityConfig wires an
 * OidcClientInitiatedLogoutSuccessHandler, which reads that key when building
 * its redirect. Without the metadata it silently falls back to a local logout
 * URL, which would quietly stop testing the Keycloak RP-initiated logout path.
 *
 * This is deliberately test-scoped: the development and production profiles
 * keep issuer-uri, so real discovery (and therefore real end-session and
 * jwks_uri values) is still used everywhere that talks to a real Keycloak.
 *
 * Specs opt in with @Import(TestOAuth2ClientConfiguration).
 */
@TestConfiguration(proxyBeanMethods = false)
class TestOAuth2ClientConfiguration {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
        def registration = ClientRegistration
                .withRegistrationId('keycloak')
                .clientId('test-client')
                .clientSecret('test-secret')
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri('{baseUrl}/login/oauth2/code/{registrationId}')
                .scope('openid', 'profile', 'email')
                .authorizationUri('http://localhost/oauth2/authorize')
                .tokenUri('http://localhost/oauth2/token')
                .userInfoUri('http://localhost/oauth2/userinfo')
                .userNameAttributeName('preferred_username')
                .jwkSetUri('http://localhost/oauth2/jwks')
                .clientName('Keycloak')
                .providerConfigurationMetadata([
                        end_session_endpoint: 'http://localhost/oauth2/logout'
                ])
                .build()

        new InMemoryClientRegistrationRepository(registration)
    }
}
