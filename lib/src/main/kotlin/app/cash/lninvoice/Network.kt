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

import app.cash.quiver.extensions.ErrorOr
import arrow.core.left
import arrow.core.right

/**
 * Represents allowed bitcoin networks as defined by SLIP-0173.
 */
enum class Network {
  MAIN,
  TEST;

  companion object {
    fun parse(value: String): ErrorOr<Network> = when (value) {
      "bc" -> MAIN.right()
      "tb" -> TEST.right()
      else -> UnknownNetworkException(value).left()
    }
  }
}

class UnknownNetworkException(network: String) : Throwable("$network is unknown and cannot be parsed")
