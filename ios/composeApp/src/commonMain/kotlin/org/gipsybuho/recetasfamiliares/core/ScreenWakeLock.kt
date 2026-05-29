package org.gipsybuho.recetasfamiliares.core

expect class ScreenWakeLock() {
    fun acquire()
    fun release()
}
