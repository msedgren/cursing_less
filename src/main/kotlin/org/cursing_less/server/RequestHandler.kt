package org.cursing_less.server

import com.intellij.openapi.application.ApplicationManager
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import org.cursing_less.services.CursingCommandService

class RequestHandler : HttpHandler {


    override fun handle(httpExchange: HttpExchange) {
        val cursingCommandService =
            ApplicationManager.getApplication().getService(CursingCommandService::class.java)
        cursingCommandService.handleCommandRequest(httpExchange)
    }

}