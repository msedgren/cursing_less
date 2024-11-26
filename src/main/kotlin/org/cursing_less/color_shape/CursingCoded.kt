package org.cursing_less.color_shape

class CursingCoded<T>(val code: String, val value: T) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CursingCoded<*>

        return code == other.code
    }

    override fun hashCode(): Int {
        return code.hashCode()
    }

    override fun toString(): String {
        return "CursingCoded(code='$code')"
    }

}