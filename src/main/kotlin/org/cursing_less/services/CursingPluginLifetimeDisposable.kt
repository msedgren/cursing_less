package org.cursing_less.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project


@Service(value = [Service.Level.APP, Service.Level.PROJECT])
class CursingPluginLifetimeDisposable : Disposable {
    companion object {
        fun getInstance(): Disposable {
            return ApplicationManager.getApplication().getService(CursingPluginLifetimeDisposable::class.java)
        }

        fun getInstance(project: Project): Disposable {
            return project.getService(CursingPluginLifetimeDisposable::class.java)
        }
    }

    override fun dispose() {
    }
}
