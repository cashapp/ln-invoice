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

import app.cash.lninvoice.FieldTags.DESCRIPTION
import app.cash.lninvoice.FieldTags.DESCRIPTION_HASH
import app.cash.lninvoice.FieldTags.EXPIRY
import app.cash.lninvoice.FieldTags.PAYMENT_HASH
import app.cash.quiver.extensions.orThrow
import app.cash.quiver.extensions.toEither
import arrow.core.Either
import arrow.core.None
import arrow.core.Option
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import arrow.core.some
import arrow.core.toOption
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.Sha256Hash
import java.math.BigInteger
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant

/**
 * A request for payment, as per [BOLT-11](https://github.com/lightning/bolts/blob/master/11-payment-encoding.md#bolt-11-invoice-protocol-for-lightning-payments)
 */
data class PaymentRequest(
  /** In which Bitcoin network this request is valid */
  val network: Network,
  /** When the request was created */
  val timestamp: Instant,
  /** The optional amount that this request is asking for */
  val amount: Option<BitcoinAmount> = None,
  /** Hash of the request pre-image */
  val paymentHash: String,
  /** List of all the raw tagged fields in the request */
  val taggedFields: List<TaggedField> = emptyList(),
  /** Signature provided by the requesting node */
  val signature: ByteString,
  /** Checksum for this request */
  val hash: ByteString
) {

  private val taggedFieldMap: Map<Int, TaggedField> = taggedFields.associateBy { it.tag }

  /** Short description of purpose of payment (UTF-8), e.g. '1 cup of coffee' or 'ナンセンス 1杯' */
  val description: Option<String> by lazy {
    taggedFieldMap[DESCRIPTION.tag].toOption().map { BitReader(it.data).text(it.size) }
  }

  /**
   * SHA2 256-bit hash of the original description.
   * Usually set when the description is too long to store in the invoice itself.
   */
  val descriptionHash: Option<String> by lazy {
    taggedFieldMap[DESCRIPTION_HASH.tag].toOption().map { BitReader(it.data).byteString(256).hex() }
  }

  /** Expiry time as defined in tagged fields. Default is 1 hour if no duration tag is present */
  val expiry: Duration by lazy {
    taggedFieldMap[EXPIRY.tag].toOption().map {
      Duration.ofSeconds(BitReader(it.data).long(it.size))
    }.getOrElse { Duration.ofHours(1) }
  }

  /** Recovers the payee node public key from the signature */
  val payeeNodePublicKey: ByteString by lazy {
    val sigBytes = BitReader(signature).byteString(520)
    val recoveryKey = signature[signature.size - 1].toInt()
    val ellipticCurveSignature = ECKey.ECDSASignature(
      BigInteger(sigBytes.substring(0, 32).hex(), 16),
      BigInteger(sigBytes.substring(32, 64).hex(), 16)
    )

    ECKey.recoverFromSignature(
      recoveryKey,
      ellipticCurveSignature,
      Sha256Hash.wrap(hash.toByteArray()),
      true
    )!!.pubKey.toByteString()
  }

  companion object {

    private const val SIGNATURE_BYTE_SIZE = 104
    private val HrpRegex = Regex("ln([a-z]+)(([0-9]*)([munp])?)?(.*)")

    /** Parse an encoded invoice into a PaymentRequest. */
    fun parse(encoded: String, strict: Boolean = false): Either<InvalidInvoice, PaymentRequest> = either {
      val decoded = decodeBech32String(encoded)
        .bind()

      val hrp = HrpRegex.find(decoded.hrp).toOption().toEither {
        InvalidInvoice("Cannot parse invoice. Bad HRP. [hrp=${decoded.hrp}]")
      }.flatMap {
        if (it.groupValues.size > 5 && it.groupValues[5].isNotEmpty()) {
          InvalidInvoice("Unexpected suffix in HRP. [hrp=${decoded.hrp}][suffix=${it.groupValues[5]}]").left()
        } else {
          it.right()
        }
      }.bind()

      val network = parseNetwork(hrp, encoded)
        .bind()

      val amount: Option<BitcoinAmount> = when (hrp.groupValues[2]) {
        "" -> None
        else -> when (hrp.groupValues[4]) {
          "p" -> PicoBitcoinAmount(hrp.groupValues[3].toLong()).some()
          "n" -> PicoBitcoinAmount(hrp.groupValues[3].toLong() * 1_000).some()
          "u" -> BitcoinAmount(hrp.groupValues[3].toLong() * 100).some()
          "m" -> BitcoinAmount(hrp.groupValues[3].toLong() * 100_000).some()
          else -> BitcoinAmount(hrp.groupValues[3].toLong() * 100_000_000).some()
        }
      }.right().flatMap {
        if (it.isSome { a -> a.picoRemainder % 10 != 0 }) {
          InvalidInvoice("Invalid amount. Pico amounts must be a multiple of 10. [hrp=${decoded.hrp}]").left()
        } else {
          it.right()
        }
      }.bind()

      val reader = Either.catch {
        BitReader(decoded.payload.substring(0, decoded.payload.size - SIGNATURE_BYTE_SIZE))
      }.mapLeft { InvalidInvoice("Invoice too short [invoice=$encoded]", it) }
        .bind()

      val timestamp: Instant = Either.catch { reader.timestamp(7) }.mapLeft {
        InvalidInvoice(
          "Cannot parse timestamp from data. " +
            "[invoice=$encoded][data=${decoded.payload.hex()}]",
          it
        )
      }.bind()

      val taggedFields = Either.catch { reader.taggedFields() }.mapLeft {
        InvalidInvoice(
          "Cannot parse tagged fields from data. " +
            "[invoice=$encoded][data=${decoded.payload.hex()}]",
          it
        )
      }.bind()

      val paymentHash = taggedFields.find { it.tag == PAYMENT_HASH.tag }.toEither {
        InvalidInvoice("Invoice did not include a payment hash [invoice=$encoded]")
      }.map {
        BitReader(it.data).byteString(256).hex()
      }.bind()

      val validatedTaggedFields = validateTaggedFields(taggedFields, strict).bind()

      PaymentRequest(
        network = network,
        timestamp = timestamp,
        amount = amount,
        paymentHash = paymentHash,
        taggedFields = validatedTaggedFields,
        signature = decoded.payload.toByteArray().takeLast(SIGNATURE_BYTE_SIZE).toByteArray().toByteString(),
        hash = hashData(decoded)
      )
    }

    private fun parseNetwork(hrp: MatchResult, encoded: String): Either<InvalidInvoice, Network> =
      Network.parse(hrp.groupValues[1])
        .mapLeft { InvalidInvoice("Invalid network. [invoice=$encoded]", it) }

    private fun decodeBech32String(encoded: String): Either<InvalidInvoice, Bech32Data> =
      encoded.toBech32Data()
        .mapLeft { InvalidInvoice("Failed to bech32 decode [invoice=$encoded]", it) }

    private fun validateTaggedFields(
      taggedFields: List<TaggedField>,
      strict: Boolean
    ): Either<InvalidInvoice, List<TaggedField>> {
      val unknown: Set<Int> by lazy { taggedFields.map { it.tag }.toSet().minus(FieldTags.validTags().toSet()) }
      return if (strict && unknown.isNotEmpty()) {
        InvalidInvoice("Tagged field has unknown tag(s) [${unknown.joinToString(",")}]").left()
      } else {
        taggedFields.right()
      }
    }

    private fun hashData(decoded: Bech32Data): ByteString {
      val hrp = decoded.hrp.encodeUtf8()
      val words = decoded.payload.toByteArray().dropLast(SIGNATURE_BYTE_SIZE).toByteArray().toByteString()
      val dataWithoutSig = BitReader(words).byteString(words.size * 5).toByteArray()
      val buffer = ByteBuffer.allocate(hrp.size + dataWithoutSig.size)
      buffer.put(hrp.toByteArray())
      buffer.put(dataWithoutSig)
      buffer.flip()
      return buffer.toByteString().sha256()
    }

    /**
     * Parse an encoded invoice into a PaymentRequest.
     * @throws `InvalidInvoice` if it cannot be parsed.
     */
    fun parseUnsafe(encoded: String): PaymentRequest = parse(encoded).orThrow()
  }
}

data class InvalidInvoice(
  override val message: String,
  override val cause: Throwable? = null
) : Throwable(message, cause)
