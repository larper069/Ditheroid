package com.example.dithercam.gl

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLSurfaceView

class CameraGLSurfaceView(
    context: Context
) : GLSurfaceView(context) {
    
    val renderer = CameraRenderer()
    
    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
    
    fun setPixelSize(value: Float) {
        renderer.setPixelSize(value)
    }
    
    fun setDitherStrength(value: Float) {
        renderer.setDitherStrength(value)
    }
    
    fun setDitherMatrix(value: Int) {
        renderer.setDitherMatrix(value)
    }
    
    fun setDitherShape(value: Int) {
        renderer.setDitherShape(value)
    }
    
    fun setPalette(colors: List<String>): Boolean {
        return renderer.setPalette(colors)
    }
    
    fun capture(callback: (Bitmap) -> Unit) {
        renderer.requestCapture(callback)
    }
    
    override fun onDetachedFromWindow() {
        queueEvent {
            renderer.release()
        }
        super.onDetachedFromWindow()
    }
}
