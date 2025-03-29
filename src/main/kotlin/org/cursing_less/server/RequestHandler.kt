package org.cursing_less.server

import com.intellij.openapi.application.ApplicationManager
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import org.cursing_less.services.CommandService

class RequestHandler : HttpHandler {


    override fun handle(httpExchange: HttpExchange) {
        val commandService =
            ApplicationManager.getApplication().getService(CommandService::class.java)
        commandService.handleCommandRequest(httpExchange)
    }

}