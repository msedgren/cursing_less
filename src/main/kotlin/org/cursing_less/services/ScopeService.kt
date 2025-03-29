package org.cursing_less.services

import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.APP)
class ScopeService(val coroutineScope: CoroutineScope) {
}
