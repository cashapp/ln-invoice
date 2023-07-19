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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bigDecimal
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.absoluteValue

class BitcoinAmountTest : StringSpec({

  "can create bitcoin amount from BigDecimal" {
    checkAll(
      Arb.bigDecimal()
        .filter { it.toLong().absoluteValue < 100_000_000_000L }
    ) {
      val amount = it.toBitcoinAmount()
      amount.bitcoin() shouldBe it.setScale(12, RoundingMode.DOWN).stripTrailingZeros()
    }
  }

  "can create bitcoin amount from millisats" {
    checkAll(Arb.long()) {
      val amount = MilliSatBitcoinAmount(it)
      amount.picoRemainder % 10 shouldBe 0
      amount.millisat() shouldBe it
    }
  }

  "can create bitcoin amount from picobtc" {
    checkAll(Arb.long()) {
      val amount = PicoBitcoinAmount(it)
      amount.pico() shouldBe it
    }
  }

  "can obtain millisat value" {
    BitcoinAmount(1).millisat() shouldBe 1_000
    BitcoinAmount(1, 1).millisat() shouldBe 1_000
    BitcoinAmount(1, 9).millisat() shouldBe 1_000
    BitcoinAmount(1, 10).millisat() shouldBe 1_001

    checkAll(arbBitcoinAmount) {
      it.millisat() shouldBe BigDecimal(it.satoshi * 1000)
        .plus(BigDecimal(it.picoRemainder).divide(BigDecimal.TEN, RoundingMode.DOWN))
        .longValueExact()
    }
  }

  "can obtain pico value" {
    BitcoinAmount(1).pico() shouldBe 10_000
    BitcoinAmount(1, 1).pico() shouldBe 10_001
    BitcoinAmount(1, 9).pico() shouldBe 10_009
    BitcoinAmount(1, 10).pico() shouldBe 10_010

    checkAll(arbBitcoinAmount) {
      it.pico() shouldBe BigDecimal(it.satoshi * 10_000)
        .plus(BigDecimal(it.picoRemainder))
        .longValueExact()
    }
  }
}) {
  companion object {
    val arbBitcoinAmount: Arb<BitcoinAmount> =
      Arb.bigDecimal(
        min = BigDecimal.ONE.divide(BitcoinAmount.PICO_PER_BTC, RoundingMode.DOWN),
        max = BigDecimal(100)
      ).map { it.toBitcoinAmount() }
  }
}
