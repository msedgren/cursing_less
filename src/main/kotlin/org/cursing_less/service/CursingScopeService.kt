package org.cursing_less.service

import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.APP)
class CursingScopeService(val coroutineScope: CoroutineScope)
