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

import app.cash.lninvoice.Bech32Data.Companion.Encoding.BECH32
import app.cash.lninvoice.Bech32Data.Companion.Encoding.BECH32M
import app.cash.quiver.extensions.ErrorOr
import app.cash.quiver.extensions.toEither
import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.Locale
import kotlin.streams.toList

/**
 * Represents bech32 encoded data.
 */
data class Bech32Data(
  val encoding: Encoding,
  val hrp: String,
  val payload: ByteString
) {

  init {
    require(hrp.isNotEmpty()) { "hrp cannot be empty" }
    require(hrp.length <= 83) { "hrp is too long: ${hrp.length} > 83" }
  }

  private val checksumCanvas = byteArrayOf(0, 0, 0, 0, 0, 0)
  private val checksum: ByteString by lazy {
    val encoded = Buffer().also { buffer ->
      buffer.write(expand(hrp))
      buffer.write(payload)
      buffer.write(checksumCanvas)
    }.readByteString()

    val mod = polymod(encoded) xor encoding.const

    (0 until 6)
      .map { i -> ((mod shr (5 * (5 - i))) and 31).toByte() }
      .toByteArray()
      .toByteString()
  }

  val encoded: String by lazy {
    StringBuffer(hrp.length + payload.size + checksum.size + 1)
      .append(hrp.lowercase(Locale.ROOT))
      .append("1")
      .also { sb ->
        Buffer().apply {
          write(payload)
          write(checksum)
        }.readByteArray().forEach { b ->
          sb.append(encodingCharset[b.toInt()])
        }
      }.toString()
  }

  companion object {
    private const val encodingCharset = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private val decodingCharset = mapOf(
      48 to 15, 50 to 10, 51 to 17, 52 to 21, 53 to 20, 54 to 26, 55 to 30, 56 to 7, 57 to 5, 65 to 29, 67 to 24,
      68 to 13, 69 to 25, 70 to 9, 71 to 8, 72 to 23, 74 to 18, 75 to 22, 76 to 31, 77 to 27, 78 to 19, 80 to 1,
      81 to 0, 82 to 3, 83 to 16, 84 to 11, 85 to 28, 86 to 12, 87 to 14, 88 to 6, 89 to 4, 90 to 2, 97 to 29,
      99 to 24, 100 to 13, 101 to 25, 102 to 9, 103 to 8, 104 to 23, 106 to 18, 107 to 22, 108 to 31, 109 to 27,
      110 to 19, 112 to 1, 113 to 0, 114 to 3, 115 to 16, 116 to 11, 117 to 28, 118 to 12, 119 to 14, 120 to 6,
      121 to 4, 122 to 2
    )

    private val parseRegex =
      Regex("^([!-@\\[-~]{1,83})1([ac-hj-np-z02-9]{6,})|([!-`{-~]{1,83})1([AC-HJ-NP-Z02-9]{6,})\$")

    private fun parse(encoded: String): ErrorOr<Pair<String, List<Int>>> =
      parseRegex.find(encoded)?.groupValues
        .toEither { IllegalArgumentException("Unparseable format") }
        .flatMap { matchResult ->
          val (hrp, data) =
            if (matchResult[1].isEmpty()) {
              matchResult[3].lowercase(Locale.ROOT) to matchResult[4]
            } else {
              matchResult[1] to matchResult[2]
            }
          Either.catch { data.chars().map(decodingCharset::getValue).toList() }
            .mapLeft { IllegalArgumentException("Invalid character") }
            .map { hrp to it }
        }

    fun decode(encoded: String): ErrorOr<Bech32Data> = either {
      val (hrp, data) = parse(encoded).bind()
      val values = data.map { it.toByte() }.toByteArray()
      val encoding = verifyChecksum(hrp, values).bind()
      Bech32Data(encoding, hrp, values.sliceArray(0 until values.size - 6).toByteString())
    }

    private fun verifyChecksum(
      hrp: String,
      values: ByteArray
    ): ErrorOr<Encoding> = when (
      val encoding = polymod(
        Buffer().also { buffer ->
          buffer.write(expand(hrp))
          buffer.write(values)
        }.readByteString()
      )
    ) {
      BECH32.const -> BECH32.right()
      BECH32M.const -> BECH32M.right()
      else -> InvalidChecksum(encoding).left()
    }

    private fun expand(hrp: String): ByteString =
      Buffer().also { buffer ->
        val ascii = hrp.chars().map { c -> c and 0x7f }.toList()
        ascii.forEach { buffer.writeByte((it shr 5) and 0x7f) }
        buffer.writeByte(0)
        ascii.forEach { buffer.writeByte(it and 0x1f) }
      }.readByteString()

    /** Find the polynomial with value coefficients mod the generator as 30-bit.  */
    private fun polymod(values: ByteString): Int {
      var c = 1
      values.toByteArray().forEach { vi ->
        val c0 = c ushr 25 and 0xff
        c = c and 0x1ffffff shl 5 xor (vi.toInt() and 0xff)
        if (c0 and 1 != 0) c = c xor 0x3b6a57b2
        if (c0 and 2 != 0) c = c xor 0x26508e6d
        if (c0 and 4 != 0) c = c xor 0x1ea119fa
        if (c0 and 8 != 0) c = c xor 0x3d4233dd
        if (c0 and 16 != 0) c = c xor 0x2a1462b3
      }
      return c
    }

    enum class Encoding(val const: Int) {
      BECH32(1),
      BECH32M(0x2bc830a3)
    }
  }
}

fun String.toBech32Data(): ErrorOr<Bech32Data> = Bech32Data.decode(this)

data class InvalidChecksum(val sum: Int) : Exception("Invalid checksum: $sum")
