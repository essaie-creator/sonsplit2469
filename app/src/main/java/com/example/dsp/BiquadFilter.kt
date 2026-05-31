package com.example.dsp

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

class BiquadFilter(
    val type: Type,
    var fc: Double,
    var fs: Double,
    var q: Double = 0.707
) {
    enum class Type { BANDPASS, NOTCH, LOWPASS, HIGHPASS }

    private var b0 = 1.0; private var b1 = 0.0; private var b2 = 0.0
    private var a0 = 1.0; private var a1 = 0.0; private var a2 = 0.0

    private var x1 = 0.0; private var x2 = 0.0
    private var y1 = 0.0; private var y2 = 0.0

    init {
        updateCoefficients()
    }

    private fun updateCoefficients() {
        val omega = 2.0 * PI * fc / fs
        val sn = sin(omega)
        val cs = cos(omega)
        val alpha = sn / (2.0 * q)

        when (type) {
            Type.BANDPASS -> {
                b0 = alpha
                b1 = 0.0
                b2 = -alpha
                a0 = 1.0 + alpha
                a1 = -2.0 * cs
                a2 = 1.0 - alpha
            }
            Type.NOTCH -> {
                b0 = 1.0
                b1 = -2.0 * cs
                b2 = 1.0
                a0 = 1.0 + alpha
                a1 = -2.0 * cs
                a2 = 1.0 - alpha
            }
            Type.LOWPASS -> {
                b0 = (1.0 - cs) / 2.0
                b1 = 1.0 - cs
                b2 = (1.0 - cs) / 2.0
                a0 = 1.0 + alpha
                a1 = -2.0 * cs
                a2 = 1.0 - alpha
            }
            Type.HIGHPASS -> {
                b0 = (1.0 + cs) / 2.0
                b1 = -(1.0 + cs)
                b2 = (1.0 + cs) / 2.0
                a0 = 1.0 + alpha
                a1 = -2.0 * cs
                a2 = 1.0 - alpha
            }
        }

        // Normalize coefficients by a0
        b0 /= a0
        b1 /= a0
        b2 /= a0
        a1 /= a0
        a2 /= a0
    }

    /**
     * Reconfigure filter frequency and sampling rate on the fly if needed
     */
    fun configure(frequency: Double, sampleRate: Double, qFactor: Double = 0.707) {
        this.fc = frequency
        this.fs = sampleRate
        this.q = qFactor
        updateCoefficients()
    }

    /**
     * Process a single audio sample (stored as a float between -1.0 and 1.0)
     */
    fun process(sample: Float): Float {
        val inVal = sample.toDouble()
        val outVal = b0 * inVal + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        
        // Delay register shift
        x2 = x1
        x1 = inVal
        y2 = y1
        y1 = outVal
        
        return outVal.toFloat()
    }

    /**
     * Clear delay filter state
     */
    fun reset() {
        x1 = 0.0
        x2 = 0.0
        y1 = 0.0
        y2 = 0.0
    }
}
