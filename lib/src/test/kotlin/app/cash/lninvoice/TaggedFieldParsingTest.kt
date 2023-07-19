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

import app.cash.lninvoice.Invoices.sampleWithDescriptionHash
import arrow.core.Either
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeNone
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.arrow.core.shouldBeSome
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.decodeHex
import java.time.Duration

class TaggedFieldParsingTest : StringSpec({

  val parsedSample: Either<InvalidInvoice, PaymentRequest> by lazy { PaymentRequest.parse(Invoices.sample) }

  "an invoice with a description tagged field should provide the description" {
    parsedSample.shouldBeRight().description shouldBeSome "coffee beans"
  }

  "an invoice without a description tagged field should not provide a description" {
    parsedSample.shouldBeRight().copy(taggedFields = emptyList()).description.shouldBeNone()
  }

  "an invoice with an expiry tagged field should provide the expiry as a duration" {
    parsedSample.shouldBeRight().copy(
      taggedFields = listOf(TaggedField(6, 3, "020e18".decodeHex()))
    ).expiry shouldBe Duration.ofMinutes(42)
  }

  "an invoice without an expiry tagged field should default to 1 hour" {
    parsedSample.shouldBeRight().expiry shouldBe Duration.ofHours(1)
  }

  "an invoice with description hash" {
    PaymentRequest.parse(sampleWithDescriptionHash)
      .shouldBeRight().descriptionHash shouldBeSome "3925b6f67e2c340036ed12093dd44e0368df1b6ea26c53dbe4811f58fd5db8c1"
  }

  "an invoice with unknown tags should parse when not using strict mode" {
    PaymentRequest.parse(encoded = Invoices.sampleWithUnknownTags, strict = false)
      .shouldBeRight().description shouldBeSome "coffee beans"
  }

  "an invoice with unknown tags should not parse when using strict mode" {
    PaymentRequest.parse(encoded = Invoices.sampleWithUnknownTags, strict = true)
      .shouldBeLeft().message shouldBe "Tagged field has unknown tag(s) [10]"
  }
})
