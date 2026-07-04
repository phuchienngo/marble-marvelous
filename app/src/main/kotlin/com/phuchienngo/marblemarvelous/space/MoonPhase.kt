package com.phuchienngo.marblemarvelous.space

import org.shredzone.commons.suncalc.MoonIllumination
import java.util.Date
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/**
 * Turns the real lunar phase (from commons-suncalc) into a billboard-space light
 * direction, so lighting a moon disc with it reproduces the current phase.
 */
internal object MoonPhase {
  fun billboardLight(date: Date): FloatArray {
    val illumination: MoonIllumination =
      MoonIllumination
        .compute()
        .on(date)
        .execute()
    val fraction: Double = illumination.fraction.coerceIn(0.0, 1.0)
    // fraction = (1 + cos(beta)) / 2, where beta is the sun-moon angle seen from
    // the moon's disc: beta = 0 -> full, beta = PI -> new.
    val beta: Double = acos((2.0 * fraction - 1.0).coerceIn(-1.0, 1.0))
    // Northern-hemisphere convention: waxing moons are lit on the right, waning
    // on the left. suncalc reports a positive phase while waning, so the lit
    // side flips to the left there.
    val waxingSign: Double = if (illumination.phase >= 0.0) -1.0 else 1.0
    return floatArrayOf(
      (sin(beta) * waxingSign).toFloat(),
      0.0f,
      cos(beta).toFloat()
    )
  }
}
