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
import app.cash.lninvoice.Invoices.pubkeyRecoveryTest
import app.cash.lninvoice.Invoices.sample
import app.cash.lninvoice.Invoices.sampleDecoded
import app.cash.lninvoice.Invoices.sampleWithDescriptionAndDescriptionHash
import app.cash.lninvoice.Invoices.sampleWithPaymentHash
import app.cash.lninvoice.Invoices.signatureOverflowTest
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeNone
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.arrow.core.shouldBeSome
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.startWith
import io.kotest.matchers.throwable.haveCauseOfType
import io.kotest.matchers.throwable.haveMessage
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.toByteString
import java.time.Instant

class PaymentRequestTest : StringSpec({

  "a valid invoice should be parsed" {
    PaymentRequest.parse(sample).shouldBeRight().should { invoice ->
      invoice.network shouldBe Network.MAIN
      invoice.amount shouldBeSome BitcoinAmount(2_500_000)
      invoice.timestamp shouldBe Instant.ofEpochSecond(1496314658L)
      invoice.description shouldBeSome "coffee beans"
      invoice.taggedFields shouldContainExactly listOf(
        TaggedField(
          1,
          52,
          "0000001004001804001403000e0200090000001004001804001403000e0200090000001004001804001403000e02000900040100"
            .decodeHex()
        ),
        TaggedField(13, 20, "0c0d17160c1913050c14100604190b010d191910".decodeHex()),
        TaggedField(
          16,
          52,
          "02040811020408110204081102040811020408110204081102040811020408110204081102040811020408110204081102040810"
            .decodeHex()
        ),
        TaggedField(5, 20, "1000000000000000000000000000000001001000".decodeHex())
      )
      invoice.signature shouldBe (
        "1a1e080419110517090811020d07030818070f16110a0b0c040506160a061c130e1b141f021906130c01061" +
          "a090d1e1e191307001d00150a150d06080b141a181d080a0d010b111405161110100417190b160b160100170a1512001312040e1a1" +
          "c000d0f07100801"
        ).decodeHex()
    }
  }

  "parsing succeeds for testnet" {
    val encoded = Bech32Data(BECH32, "lntb25m", sampleDecoded.payload).encoded

    PaymentRequest.parse(encoded).shouldBeRight().network shouldBe Network.TEST
  }

  "a valid invoice should have a payment hash" {
    PaymentRequest.parse(sampleWithPaymentHash).shouldBeRight().should {
      it.description shouldBeSome "Blockstream Store: 88.85 USD for Blockstream Ledger Nano S x 1" +
        ", \"Back In My Day\" Sticker x 2, \"I Got Lightning Working\" Sticker x 2 and 1 more items"
      it.paymentHash shouldBe "462264ede7e14047e9b249da94fefc47f41f7d02ee9b091815a5506bc8abf75f"
      it.payeeNodePublicKey shouldBe "03e7156ae33b0a208d0744199163177e909e80176e55d97a2f221ede0f934dd9ad".decodeHex()
    }
  }

  "a valid invoice should have a payee node public key" {
    PaymentRequest.parse(pubkeyRecoveryTest).shouldBeRight()
      .payeeNodePublicKey shouldBe "02e98e5929c25f16f7c15b9026b9986e32dc36ba62c6497f436984b40fc3f0d7ac".decodeHex()
  }

  "a valid invoice should have a payee node public key with large ECDSA signature values" {
    PaymentRequest.parse(signatureOverflowTest).shouldBeRight()
      .payeeNodePublicKey shouldBe "037cc5f9f1da20ac0d60e83989729a204a33cc2d8e80438969fadf35c1c5f1233b".decodeHex()
  }

  "if the invoice does not have a valid checksum, then it must fail to parse" {
    val badChecksumInvoice = sample.replaceFirst("44qu", "44qz")
    PaymentRequest.parse(badChecksumInvoice).shouldBeLeft().should {
      it.printStackTrace()
      it should haveMessage("Failed to bech32 decode [invoice=$badChecksumInvoice]")
      it should haveCauseOfType<InvalidChecksum>()
    }
  }

  "if the parser does NOT understand the hrp prefix it must fail to parse" {
    val encoded = Bech32Data(BECH32, "lzbc25m", sampleDecoded.payload).encoded

    PaymentRequest.parse(encoded) shouldBeLeft InvalidInvoice("Cannot parse invoice. Bad HRP. [hrp=lzbc25m]")
  }

  "if the amount is not specified in the hrp prefix, then the parsed amount should be None" {
    val noAmountInvoice = Bech32Data(BECH32, "lnbc", sampleDecoded.payload).encoded
    PaymentRequest.parse(noAmountInvoice).shouldBeRight().amount.shouldBeNone()
  }

  "if the amount contains a non-digit, then the invoice must fail to parse" {
    val encoded = Bech32Data(BECH32, "lnbc1f1p", sampleDecoded.payload).encoded
    PaymentRequest.parse(encoded) shouldBeLeft
      InvalidInvoice("Unexpected suffix in HRP. [hrp=lnbc1f1p][suffix=f1p]")
  }

  "if the amount is followed by anything other than a multiplier, then the invoice must fail to parse" {
    val encoded = Bech32Data(BECH32, "lnbc100a", sampleDecoded.payload).encoded
    PaymentRequest.parse(encoded) shouldBeLeft
      InvalidInvoice("Unexpected suffix in HRP. [hrp=lnbc100a][suffix=a]")
  }

  "if the amount is not followed by a multiplier, then it should be parsed as whole bitcoin" {
    val encoded = Bech32Data(BECH32, "lnbc100", sampleDecoded.payload).encoded
    PaymentRequest.parse(encoded).shouldBeRight().amount shouldBeSome BitcoinAmount(10_000_000_000)
  }

  "if the amount is followed by the multiplier `m`, then it should be parsed as milli-bitcoin" {
    val encoded = Bech32Data(BECH32, "lnbc100m", sampleDecoded.payload).encoded
    PaymentRequest.parse(encoded).shouldBeRight().amount shouldBeSome BitcoinAmount(10_000_000)
  }

  "if the amount is followed by the multiplier `u`, then it should be parsed as micro-bitcoin" {
    val encoded = Bech32Data(BECH32, "lnbc100u", sampleDecoded.payload).encoded
    PaymentRequest.parse(encoded).shouldBeRight().amount shouldBeSome BitcoinAmount(10_000)
  }

  "if the amount is followed by the multiplier `n`, then it should be parsed as nano-bitcoin" {
    val encoded = Bech32Data(BECH32, "lnbc100n", sampleDecoded.payload).encoded
    PaymentRequest.parse(encoded).shouldBeRight().amount shouldBeSome BitcoinAmount(satoshi = 10)
  }

  "if the amount is followed by the multiplier `p`, then it should be parsed as pico-bitcoin" {
    val encoded = Bech32Data(BECH32, "lnbc100p", sampleDecoded.payload).encoded
    PaymentRequest.parse(encoded).shouldBeRight().amount shouldBeSome PicoBitcoinAmount(100)
  }

  "parsing succeeds for invoices with both a description and a description hash" {
    PaymentRequest.parse(sampleWithDescriptionAndDescriptionHash).shouldBeRight() should {
      it.descriptionHash shouldBeSome "3925b6f67e2c340036ed12093dd44e0368df1b6ea26c53dbe4811f58fd5db8c1"
      it.description shouldBeSome "Test"
    }
  }

  "if the amount does not ends with 0 and the multiplier is `p`, then the invoice must fail to parse" {
    val encoded = Bech32Data(BECH32, "lnbc105p", sampleDecoded.payload).encoded
    PaymentRequest.parse(encoded) shouldBeLeft
      InvalidInvoice("Invalid amount. Pico amounts must be a multiple of 10. [hrp=lnbc105p]")
  }

  "if the timestamp is omitted, then it must fail to parse" {
    val data = sampleDecoded.payload.toByteArray().drop(7).toByteArray().toByteString()
    val encoded = Bech32Data(BECH32, "lnbc25m", data).encoded
    PaymentRequest.parse(encoded).shouldBeLeft().message should startWith("Cannot parse tagged fields from data.")
  }

  "if the trailing data doesn't cleanly fulfil a whole tag, then it must fail to parse" {
    val data = sampleDecoded.payload.toByteArray().dropLast(1).toByteArray().toByteString()
    val encoded = Bech32Data(BECH32, "lnbc25m", data).encoded
    PaymentRequest.parse(encoded).shouldBeLeft().message should startWith("Cannot parse tagged fields from data.")
  }

  "parsing unsafely should fail correctly" {
    val encoded = Bech32Data(BECH32, "lnbc105p", sampleDecoded.payload).encoded
    shouldThrow<InvalidInvoice> { PaymentRequest.parseUnsafe(encoded) }
  }

  "if the invoice is too short (without enough bytes for a signature), it should fail to parse" {
    val invoice = "lnbc1pvjluezpp5qqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqypqdpl2pkx2ctnv5sxxmmwwd5k" +
      "getjypeh2ursdae8g6na6hlh"

    PaymentRequest.parse(invoice).shouldBeLeft().message shouldBe "Invoice too short [invoice=$invoice]"
  }

  "parsing fails if network is unknown" {
    val encoded = Bech32Data(BECH32, "lnbz25m", sampleDecoded.payload).encoded

    PaymentRequest.parse(encoded).shouldBeLeft().message shouldBe "Invalid network. [invoice=$encoded]"
  }
})
