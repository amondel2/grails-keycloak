package com.grails.example

import grails.testing.mixin.integration.Integration
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.annotation.Rollback
import org.springframework.web.context.request.RequestContextHolder
import spock.lang.Specification

/**
 * End-to-end proof that the account pages sit behind Spring Security's
 * ROLE_USER gate.
 *
 * AccountController.index() is annotated with @PreAuthorize("hasRole('USER')"),
 * which a browser integration test cannot satisfy (see MondelSpec -- the only
 * way to reach it is through Keycloak, which is unavailable offline). Instead
 * this spec reproduces what Keycloak yields after login by dropping a real
 * UsernamePasswordAuthenticationToken carrying the desired authorities into
 * SecurityContextHolder, then invoking the proxied controller bean directly.
 * The @PreAuthorize interceptor fires against the mocked token, so:
 *   - without a token the action is rejected with AccessDeniedException,
 *   - with ROLE_USER the view resolves and renders account/index.gsp, and
 *   - with any other role (e.g. ROLE_ADMIN) the action is still denied.
 *
 * Run with: ./gradlew :integrationTest --tests '*.AccountControllerIntegrationSpec'
 */
@Integration
@Rollback
class AccountControllerIntegrationSpec extends Specification {

    @Autowired
    AccountController accountController

    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
        SecurityContextHolder.clearContext()
    }

    void "index renders the account view for an authenticated user with ROLE_USER"() {
        given: 'a request to /account and a mocked token carrying ROLE_USER'
        mockToken([new SimpleGrantedAuthority('ROLE_USER')])
        bindRequest('/account')

        when: 'the secured index action runs'
        accountController.index()

        then: 'the account/index.gsp view resolves with status 200'
        accountController.response.status == 200
        accountController.modelAndView.viewName == '/account/index'
    }

    void "index rejects an authenticated user with only ROLE_ADMIN"() {
        given: 'a request to /account and a mocked token carrying only ROLE_ADMIN'
        mockToken([new SimpleGrantedAuthority('ROLE_ADMIN')])
        bindRequest('/account')

        when: 'the secured index action runs'
        accountController.index()

        then: 'method security denies access'
        thrown(AccessDeniedException)
    }

    void "index rejects an unauthenticated request"() {
        given: 'no authentication token in the security context'
        bindRequest('/account')

        when: 'the secured index action runs'
        accountController.index()

        then: 'method security denies access'
        thrown(AuthenticationCredentialsNotFoundException)
    }

    private static void mockToken(List authorities) {
        SecurityContextHolder.context.authentication =
                new UsernamePasswordAuthenticationToken('mock-user', 'mock-password', authorities)
    }

    private static void bindRequest(String uri) {
        def request = new MockHttpServletRequest('GET', uri)
        def webRequest = new GrailsWebRequest(request, new MockHttpServletResponse(), new MockServletContext())
        RequestContextHolder.setRequestAttributes(webRequest)
    }
}
