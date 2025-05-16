package org.cursing_less.service

data class CursingDirectionState(val direction: CursingUserDirection, val timeInMs: Long) {

    companion object {
        val NONE = CursingDirectionState(CursingUserDirection.NONE, 0L)
    }
}