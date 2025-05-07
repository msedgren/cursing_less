package org.cursing_less.color_shape

sealed interface CursingShape {
    val name: String

    data object Circle : CursingShape {
        override val name = "circle"
    }

    data object Square : CursingShape {
        override val name = "square"
    }

    data object Slash : CursingShape {
        override val name = "slash"
    }

    data object BackSlash : CursingShape {
        override val name = "backslash"
    }

    data object Line : CursingShape {
        override val name = "line"
    }

    data object X : CursingShape {
        override val name = "x"
    }
}
