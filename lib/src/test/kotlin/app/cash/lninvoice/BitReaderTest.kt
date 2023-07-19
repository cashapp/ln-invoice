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
import app.cash.quiver.extensions.orThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class BitReaderTest : StringSpec({

  val bolt11Sample =
    "lnbc1pvjluezsp5zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zygspp5qqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqqqsyqc" +
      "yq5rqwzqfqypqdpl2pkx2ctnv5sxxmmwwd5kgetjypeh2ursdae8g6twvus8g6rfwvs8qun0dfjkxaq9qrsgq357wnc5r2ueh7ck6q93d" +
      "j32dlqnls087fxdwk8qakdyafkq3yap9us6v52vjjsrvywa6rt52cm9r9zqt8r2t7mlcwspyetp5h2tztugp9lfyql"

  "testing bolt-11 example" {
    val data = bolt11Sample.toBech32Data().orThrow().payload
    val reader = BitReader(data.substring(0, data.size - 104))
    reader.long(7) shouldBe 1496314658L // timestamp
    reader.int(1) shouldBe 16 // 's' field
    reader.int(2) shouldBe 52 // data length
    reader.byteString(256).hex() shouldBe "1111111111111111111111111111111111111111111111111111111111111111"
    reader.int(1) shouldBe 1
    reader.int(2) shouldBe 52
    reader.byteString(256).hex() shouldBe "0001020304050607080900010203040506070809000102030405060708090102"
    reader.int(1) shouldBe 13
    reader.int(2) shouldBe 63
    reader.text(63) shouldBe "Please consider supporting this project"
    reader.int(1) shouldBe 5
    reader.int(2) shouldBe 3
    reader.bits(3) shouldBe setOf(8, 14)
  }

  "testing bolt-11 example with description hash" {
    val data = sampleWithDescriptionHash.toBech32Data().orThrow().payload
    val reader = BitReader(data.substring(0, data.size - 104))
    reader.long(7) shouldBe 1496314658 // timestamp
    reader.int(1) shouldBe 16 // 's' field
    reader.int(2) shouldBe 52 // data length
    reader.byteString(256).hex() shouldBe "1111111111111111111111111111111111111111111111111111111111111111"
    reader.int(1) shouldBe 1
    reader.int(2) shouldBe 52
    reader.byteString(256).hex() shouldBe "0001020304050607080900010203040506070809000102030405060708090102"
    reader.int(1) shouldBe 23 // 'h' field
    reader.int(2) shouldBe 52 // data length
    reader.byteString(256).hex() shouldBe "3925b6f67e2c340036ed12093dd44e0368df1b6ea26c53dbe4811f58fd5db8c1"
    reader.int(1) shouldBe 5
    reader.int(2) shouldBe 3
    reader.bits(3) shouldBe setOf(8, 14)
  }

  "testing uneven bytes with padding" {
    val data = "lnurl1xyerxdu27jy".toBech32Data().orThrow().payload
    val reader = BitReader(data)
    reader.byteString(24).string(Charsets.UTF_8) shouldBe "123"
  }
})
