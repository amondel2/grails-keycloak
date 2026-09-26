package com.grails.example

import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.context.request.RequestContextHolder

/**
 * Shared plumbing for integration specs that drive a @PreAuthorize-guarded
 * controller action offline.
 */
trait SecuredRequestSupport {

    // --- authentication ----------------------------------------------------

    /**
     * Puts an authenticated principal carrying the given authorities into the
     * security context, reproducing what Keycloak yields after login.
     *
     * Use this to prove a NEGATIVE: pass authorities that lack the role the
     * action requires and the @PreAuthorize interceptor will throw
     * AccessDeniedException. That is the point of autowiring the controller
     * rather than new-ing it -- the proxy has to be in the call path.*/
    void mockToken(List authorities) {
        SecurityContextHolder.context.authentication =
                new UsernamePasswordAuthenticationToken('mock-user', 'not-a-real-password', authorities)
    }

    /**
     * The default: the ROLE_USER that AccountController's
     * @PreAuthorize ("hasRole('USER')") requires.
     */
    void mockKeycloakUser() {
        mockToken([new SimpleGrantedAuthority('ROLE_USER')])
    }

    /**
     * Resets both thread locals. Call from your spec's cleanup().
     *
     * Call this in the `given:` block of an unauthenticated feature too, rather
     * than trusting that the previous feature's cleanup() already ran: the
     * unauthenticated case is the one where a leaked token turns a
     * CredentialsNotFound into a silent pass.*/
    void clearAuthContext() {
        RequestContextHolder.resetRequestAttributes()
        SecurityContextHolder.clearContext()
    }

    // --- request binding ---------------------------------------------------

    /**
     * Binds a GrailsWebRequest to RequestContextHolder and returns it, so the
     * caller can assert on both the response and the ModelAndView the action
     * leaves behind. See the class comment for what the uri does and does not
     * affect.
     *
     * A FRESH MockHttpServletResponse per call: a response accumulates
     * everything written to it, so a shared one would let a later assertion see
     * an earlier call's output.*/
    void bindRequest(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest('GET', uri)
        MockHttpServletResponse response = new MockHttpServletResponse()
        GrailsWebRequest webRequest = new GrailsWebRequest(request, response, new MockServletContext())
        RequestContextHolder.setRequestAttributes(webRequest)
    }

}