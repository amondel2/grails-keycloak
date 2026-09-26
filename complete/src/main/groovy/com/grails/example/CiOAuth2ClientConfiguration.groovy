package com.grails.example

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType

@Configuration(proxyBeanMethods = false)
@Profile('ci')
class CiOAuth2ClientConfiguration {

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