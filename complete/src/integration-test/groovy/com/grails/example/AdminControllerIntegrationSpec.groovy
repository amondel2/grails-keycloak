package com.grails.example

import grails.testing.mixin.integration.Integration
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.test.annotation.Rollback
import spock.lang.Specification

/**
 * End-to-end proof that the admin pages sit behind Spring Security's
 * ROLE_ADMIN gate.
 *
 * AdminController.index() is annotated with @PreAuthorize("hasRole('ADMIN')"),
 * which a browser integration test cannot satisfy (see MondelSpec -- the only
 * way to reach it is through Keycloak, which is unavailable offline). Instead
 * this spec reproduces what Keycloak yields after login by dropping a real
 * UsernamePasswordAuthenticationToken carrying the desired authorities into
 * SecurityContextHolder, then invoking the proxied controller bean directly.
 * The @PreAuthorize interceptor fires against the mocked token, so:
 *   - with ROLE_ADMIN the action resolves the admin/index.gsp view,
 *   - with any other role (e.g. ROLE_USER) the action is denied, and
 *   - with no token at all the action is denied for want of credentials.
 *
 * The shared plumbing (mockToken / mockKeycloakUser / bindRequest /
 * clearAuthContext) lives in SecuredRequestSupport.
 *
 * Run with: ./gradlew :integrationTest --tests '*.AdminControllerIntegrationSpec'
 */
@Integration
@Rollback
class AdminControllerIntegrationSpec extends Specification implements SecuredRequestSupport {

    @Autowired
    AdminController adminController

    void cleanup() {
        clearAuthContext()
    }

    void "index resolves the admin view for an authenticated user with ROLE_ADMIN"() {
        given: 'a request to /admin and a mocked token carrying ROLE_ADMIN'
        mockToken([new SimpleGrantedAuthority('ROLE_ADMIN')])
        bindRequest('/admin')

        when: 'the secured index action runs'
        adminController.index()

        then: 'the admin/index.gsp view resolves with status 200'
        adminController.response.status == 200
        adminController.modelAndView.viewName == '/admin/index'
    }

    void "index rejects an authenticated user with only ROLE_USER"() {
        given: 'a request to /admin and a mocked token carrying only ROLE_USER'
        mockKeycloakUser()
        bindRequest('/admin')

        when: 'the secured index action runs'
        adminController.index()

        then: 'method security denies access'
        thrown(AccessDeniedException)
    }

    void "index rejects an unauthenticated request"() {
        given: 'no authentication token in the security context'
        clearAuthContext()
        bindRequest('/admin')

        when: 'the secured index action runs'
        adminController.index()

        then: 'method security denies access'
        thrown(AuthenticationCredentialsNotFoundException)
    }
}
