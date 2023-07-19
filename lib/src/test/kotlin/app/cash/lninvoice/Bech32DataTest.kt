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

import app.cash.lninvoice.Bech32Data.Companion.Encoding
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import java.util.Locale

class Bech32DataTest : StringSpec({

  "valid bech32 can serde" {
    checkAll(
      Exhaustive.collection(
        listOf(
          "A12UEL5L",
          "a12uel5l",
          "an83characterlonghumanreadablepartthatcontainsthenumber1andtheexcludedcharactersbio1tt5tgs",
          "abcdef1qpzry9x8gf2tvdw0s3jn54khce6mua7lmqqqxw",
          "11qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqc8247j",
          "split1checkupstagehandshakeupstreamerranterredcaperred2y9e3w",
          "?1ezyfcl"
        )
      )
    ) { encoded ->
      encoded.toBech32Data().shouldBeRight() should { data ->
        data.encoding shouldBe Encoding.BECH32
        data.encoded shouldBe encoded.lowercase(Locale.ROOT)
      }
    }
  }

  "valid bech32m can serde" {
    checkAll(
      Exhaustive.collection(
        listOf(
          "A1LQFN3A",
          "a1lqfn3a",
          "an83characterlonghumanreadablepartthatcontainsthetheexcludedcharactersbioandnumber11sg7hg6",
          "abcdef1l7aum6echk45nj3s0wdvt2fg8x9yrzpqzd3ryx",
          "11llllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllludsr8",
          "split1checkupstagehandshakeupstreamerranterredcaperredlc445v",
          "?1v759aa"
        )
      )
    ) { encoded ->
      encoded.toBech32Data().shouldBeRight() should { data ->
        data.encoding shouldBe Encoding.BECH32M
        data.encoded shouldBe encoded.lowercase(Locale.ROOT)
      }
    }
  }

  "invalid bech32(m) cannot parse" {
    checkAll(
      Exhaustive.collection(
        listOf(
          " 1nwldj5", // HRP character out of range
          "${0x7f}1axkwrx", // HRP character out of range
          "${0x80}1eym55h", // HRP character out of range
          "an84characterslonghumanreadablepartthatcontainsthenumber1andtheexcludedcharactersbio1569pvx",
          "pzry9x0s0muk", // No separator character
          "1pzry9x0s0muk", // Empty HRP
          "x1b4n0q5v", // Invalid data character
          "li1dgmt3", // Too short checksum
          "de1lg7wt${0xff}", // Invalid character in checksum
          "A1G7SGD8", // checksum calculated with uppercase form of HRP
          "10a06t8", // empty HRP
          "1qzzfhee" // empty HRP
        )
      )
    ) { encoded ->
      encoded.toBech32Data().shouldBeLeft()
    }
  }
})
