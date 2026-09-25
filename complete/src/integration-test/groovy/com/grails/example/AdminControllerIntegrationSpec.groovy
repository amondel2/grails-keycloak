package com.grails.example

import grails.gorm.transactions.Transactional
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
 *   - without a token the action is rejected with AccessDeniedException,
 *   - with ROLE_ADMIN the view resolves and renders admin/index.gsp, and
 *   - with any other role (e.g. ROLE_USER) the action is still denied.
 *
 * Run with: ./gradlew :integrationTest --tests '*.AdminControllerIntegrationSpec'
 */
@Integration
@Rollback
class AdminControllerIntegrationSpec extends Specification {

    @Autowired
    AdminController adminController

    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
        SecurityContextHolder.clearContext()
    }

    void "index renders the admin view for an authenticated user with ROLE_ADMIN"() {
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
        mockToken([new SimpleGrantedAuthority('ROLE_USER')])
        bindRequest('/admin')

        when: 'the secured index action runs'
        adminController.index()

        then: 'method security denies access'
        thrown(AccessDeniedException)
    }

    void "index rejects an unauthenticated request"() {
        given: 'no authentication token in the security context'
        bindRequest('/admin')

        when: 'the secured index action runs'
        adminController.index()

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
