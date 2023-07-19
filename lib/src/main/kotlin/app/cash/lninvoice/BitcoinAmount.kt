/*
 * Copyright (c) 2023 Block, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.lninvoice

import java.math.BigDecimal
import java.math.RoundingMode

/** A quantity of Bitcoin, internally denominated in satoshi, with a pico-btc remainder */
data class BitcoinAmount(
  /** The base unit of account in satoshis (10^-8 Bitcoin, or 100m satoshi per Bitcoin */
  val satoshi: Long,
  /** If necessary, any sub-satoshi remainder in pico btc (10^-12 Bitcoin, or 10k pico per satoshi) */
  val picoRemainder: Int = 0
) {

  /** Provide the amount denominated in BTC */
  fun bitcoin(): BigDecimal =
    BigDecimal(satoshi).divide(SATS_PER_BTC).plus(remainderAsBigDecimal).stripTrailingZeros()

  /** Provide the amount denominated in pico btc (10,000th of a satoshi) */
  fun pico(): Long = BigDecimal(satoshi)
    .multiply(PICO_PER_SAT)
    .plus(BigDecimal(picoRemainder))
    .longValueExact()

  /**
   * Provide the amount denominated in millisats (1,000th of a satoshi, 10 x pico btc).
   * Rounded towards zero if necessary.
   */
  fun millisat(): Long = BigDecimal(satoshi)
    .multiply(PICO_PER_SAT)
    .plus(BigDecimal(picoRemainder))
    .divide(PICO_PER_MSAT, RoundingMode.DOWN)
    .longValueExact()

  private val remainderAsBigDecimal: BigDecimal by lazy {
    BigDecimal(picoRemainder).divide(PICO_PER_BTC)
  }

  companion object {
    internal val MSAT_PER_SAT: BigDecimal = BigDecimal(1_000)
    internal val PICO_PER_MSAT: BigDecimal = BigDecimal(10)
    internal val PICO_PER_SAT: BigDecimal = BigDecimal(10_000)
    internal val SATS_PER_BTC: BigDecimal = BigDecimal(100_000_000)

    internal val PICO_PER_BTC: BigDecimal = SATS_PER_BTC.multiply(PICO_PER_SAT)

    internal const val SCALE_SATS = 8
    internal const val SCALE_PICO = 12

    // Due to Long overflow, this (1 million BTC) is the threshold at which we will never set a the scale above 8
    // when converting a BitcoinAmount to a CryptoAmount, even if a non-zero remainder_pico value is present e.g.
    // BitcoinAmount(sats = 1_000_000 * 100_000_000L, remainder_pico = 5555).toCryptoAmount() will strip the remainder.
    internal val SCALE_ROUNDING_LIMIT_IN_SATS = SATS_PER_BTC.multiply(BigDecimal(1_000_000))
  }
}

/** A BitcoinAmount representing the given millisat value (100,000,000,000 msat per btc) */
@Suppress("FunctionName")
fun MilliSatBitcoinAmount(milliSats: Long): BitcoinAmount {
  val input = BigDecimal(milliSats)
  val sats = input.divide(MSAT_PER_SAT).toLong()
  val pico = input.remainder(MSAT_PER_SAT).multiply(PICO_PER_MSAT).toInt()
  return BitcoinAmount(sats, pico)
}

/** A BitcoinAmount representing the given pico btc value (1,000,000,000,000 pico per btc) */
@Suppress("FunctionName")
fun PicoBitcoinAmount(picoBtc: Long): BitcoinAmount {
  val input = BigDecimal(picoBtc)
  val sats = input.divide(PICO_PER_SAT).toLong()
  val pico = input.remainder(PICO_PER_SAT).toInt()
  return BitcoinAmount(sats, pico)
}

/** Create a BitcoinAmount from the amount in BTC. Amounts below pico are truncated, not rounded. */
fun BigDecimal.toBitcoinAmount(): BitcoinAmount {
  val sats = this.multiply(SATS_PER_BTC).toLong()
  val pico = this.multiply(PICO_PER_BTC).remainder(PICO_PER_SAT).toInt()
  return BitcoinAmount(sats, pico)
}

internal val MSAT_PER_SAT: BigDecimal = BigDecimal(1_000)
internal val PICO_PER_MSAT: BigDecimal = BigDecimal(10)
internal val PICO_PER_SAT: BigDecimal = BigDecimal(10_000)
internal val SATS_PER_BTC: BigDecimal = BigDecimal(100_000_000)

internal val PICO_PER_BTC: BigDecimal = SATS_PER_BTC.multiply(PICO_PER_SAT)
