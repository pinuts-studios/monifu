/*
 * Copyright (c) 2014-2015 Alexandru Nedelcu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monifu.reactive.operators

import monifu.reactive.Observable
import scala.concurrent.duration.Duration

object ConcatOneSuite extends BaseOperatorSuite {
  def observable(sourceCount: Int) = Some {
    Observable.range(0, sourceCount)
      .flatMap(i => Observable.unit(i))
  }

  def count(sourceCount: Int) =
    sourceCount

  def waitForFirst = Duration.Zero
  def waitForNext = Duration.Zero

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    createObservableEndingInError(Observable.range(0, sourceCount), ex)
      .flatMap(i => Observable.unit(i))
  }

  def sum(sourceCount: Int) = {
    sourceCount * (sourceCount - 1) / 2
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    Observable.range(0, sourceCount).flatMap { i =>
      if (i == sourceCount-1)
        throw ex
      else
        Observable.unit(i)
    }
  }
}
