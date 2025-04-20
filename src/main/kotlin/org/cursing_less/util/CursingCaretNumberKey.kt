package org.cursing_less.util

import com.intellij.openapi.util.Key

// Key for storing the caret number in user data
const val CARET_NUMBER_KEY_NAME = "CURSING_CARET_NUMBER"
val CARET_NUMBER_KEY = Key.create<Int>(CARET_NUMBER_KEY_NAME)