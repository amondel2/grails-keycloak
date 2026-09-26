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
 *
 * WHY THIS EXISTS
 * The app authenticates against Keycloak, which is unreachable from a test, so
 * no browser-driven suite can get past the login redirect (see MondelSpec). The
 * only way to reach a secured action is to reproduce what Keycloak hands
 * Spring Security after a successful login: a real
 * UsernamePasswordAuthenticationToken carrying the mapped role authority,
 * dropped into SecurityContextHolder, plus a GrailsWebRequest bound to
 * RequestContextHolder. AccountControllerIntegrationSpec and
 * AdminControllerIntegrationSpec each had their own byte-identical private copy
 * of that plumbing; it is folded in here so the rules that were learned the
 * hard way are stated once and cannot be forgotten at the next call site.
 *
 * THE MOCK TOKEN IS FAITHFUL TO KEYCLOAK, NOT A SHORTCUT
 * KeycloakAuthoritiesMapper turns a realm role into "ROLE_" + role.toUpperCase(),
 * so a realm role called "admin" arrives as ROLE_ADMIN. Passing plain
 * ROLE_USER / ROLE_ADMIN strings here therefore produces exactly the authorities
 * the mapper would have produced for a real login -- the specs are not
 * inventing a role shape the production code never emits.
 *
 * HOW TO USE IT
 * <pre>
 *   class MyControllerIntegrationSpec extends Specification implements SecuredRequestSupport {
 *       &#64;Autowired MyController myController   // AUTOWIRED, never new-ed:
 *                                                   // the method-security proxy
 *                                                   // must be in play
 *
 *       void cleanup() {
 *           clearAuthContext()                      // RequestContextHolder +
 *                                                   // SecurityContextHolder
 *       }
 *
 *       void "the secured action resolves its view for a USER"() {
 *           given: 'a mocked token carrying ROLE_USER and a bound request'
 *           mockKeycloakUser()
 *           GrailsWebRequest webRequest = bindRequest('/my')
 *
 *           when: 'the secured action runs'
 *           myController.index()
 *
 *           then:
 *           webRequest.response.status == 200
 *       }
 *   }
 * </pre>
 *
 * WHY THERE IS NO cleanup() HERE
 * A trait method named cleanup() would be silently shadowed by any spec that
 * defines its own cleanup(). That is a live hazard, not a hypothetical one:
 * these specs are @Rollback, but a sibling spec that writes rows will need its
 * own cleanup() to delete them, and forgetting the reset there would leak an
 * authentication into the next feature. So the reset is exposed as
 * clearAuthContext() and each spec calls it from its own cleanup(), where a
 * missing call is visible in review.
 *
 * WHAT THE URI IN bindRequest() DOES AND DOES NOT DO
 * It documents the GET the spec is standing in for, and it is what any action
 * that inspects the request would see. It does NOT drive the outcome: these
 * specs call the controller bean directly, so the servlet filter chain never
 * runs and no URL-mapping rule is consulted. The view name likewise comes from
 * the action's own render(view:), not from the request -- deliberately setting
 * GrailsWebRequest.controllerName to a wrong value changes nothing, and
 * deliberately pointing the URI at a path that maps to nothing changes nothing.
 * Only the SecurityContextHolder token decides whether the action is allowed to
 * run at all, which is exactly the thing @PreAuthorize governs and the thing
 * these specs exist to pin down.
 *
 * WHY bindRequest() DOES NOT RENDER THE VIEW
 * renderView is deliberately left off. Turning it on makes the GSP engine run,
 * and layouts/main.gsp calls the wondrify asset taglibs (<asset:stylesheet>,
 * <asset:javascript>), which need a real servlet context and a built asset
 * manifest -- neither of which a MockServletContext has. These specs assert that
 * the action *resolves* to the right view, which is the claim @PreAuthorize
 * governs; rendering the bytes is left to a browser suite with a real container.
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