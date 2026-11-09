package com.example.calendar.widget

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF

/** Три контура 1×1 виджета — как типичные формы иконок на рабочем столе. */
enum class LauncherIconShape {
    SQUARE,
    ROUNDED,
    CIRCLE,
}

internal data class LauncherIconMask(
    val shape: LauncherIconShape,
    /** Контур в квадрате 0…100. */
    val path100: Path,
)

internal object LauncherIconMasks {

    fun forShape(shape: LauncherIconShape): LauncherIconMask {
        val path = Path()
        val bounds = RectF(0f, 0f, 100f, 100f)
        when (shape) {
            LauncherIconShape.SQUARE -> path.addRoundRect(bounds, 3.5f, 3.5f, Path.Direction.CW)
            LauncherIconShape.ROUNDED -> path.addRoundRect(bounds, 22f, 22f, Path.Direction.CW)
            LauncherIconShape.CIRCLE -> path.addOval(bounds, Path.Direction.CW)
        }
        return LauncherIconMask(shape, path)
    }

    fun Path.scaledTo(target: RectF): Path {
        val bounds = RectF()
        computeBounds(bounds, true)
        if (bounds.width() < 0.5f || bounds.height() < 0.5f) return Path(this)
        val matrix = Matrix()
        matrix.setRectToRect(bounds, target, Matrix.ScaleToFit.FILL)
        return Path(this).also { it.transform(matrix) }
    }
}
