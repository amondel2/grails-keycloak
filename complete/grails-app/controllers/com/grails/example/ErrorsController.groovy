package com.grails.example

import org.springframework.security.authorization.AuthorizationDeniedException

class ErrorsController {

    def authorizationDenied(AuthorizationDeniedException exception) {
        response.status = 403
        render view: '/notFound'
    }
}
