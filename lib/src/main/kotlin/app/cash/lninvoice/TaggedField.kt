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

import okio.ByteString

data class TaggedField(val tag: Int, val size: Int, val data: ByteString)

enum class FieldTags(val tag: Int) {
  PAYMENT_HASH(1),
  EXTRA_ROUTING_INFO(3),
  FEATURE_BITS(5),
  EXPIRY(6),
  FALLBACK_ON_CHAIN_ADDRESS(9),
  DESCRIPTION(13),
  SECRET(16),
  PAYEE_NODE(19),
  DESCRIPTION_HASH(23),
  MIN_FINAL_CLTV_EXPIRY_DELTA(24),
  METADATA(27);

  companion object {
    fun validTags() = FieldTags.values().map { it.tag }
  }
}
