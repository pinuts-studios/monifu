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

import java.io.{OutputStream, PrintStream}
import monifu.concurrent.atomic.{AtomicInt, Atomic}
import monifu.reactive.{DummyException, Observable}
import scala.concurrent.duration.Duration


object DumpSuite extends BaseOperatorSuite {
  def dummyOut(count: AtomicInt = null) = {
    val out = new OutputStream { def write(b: Int) = () }
    new PrintStream(out) {
      override def println(x: String) = {
        super.println(x)
        if (count != null) {
          val c = count.decrementAndGet()
          if (c == 0) throw new DummyException("dummy")
        }
      }
    }
  }

  def observable(sourceCount: Int) = Some {
    Observable.range(0, sourceCount)
      .dump("o", dummyOut())
  }

  def observableInError(sourceCount: Int, ex: Throwable) = Some {
    createObservableEndingInError(Observable.range(0, sourceCount), ex)
      .dump("o", dummyOut())
  }

  def brokenUserCodeObservable(sourceCount: Int, ex: Throwable) = Some {
    Observable.range(0, sourceCount * 2)
      .dump("o", dummyOut(Atomic(sourceCount)))
  }

  def count(sourceCount: Int) = sourceCount
  def sum(sourceCount: Int) =
    sourceCount * (sourceCount - 1) / 2

  def waitForNext = Duration.Zero
  def waitForFirst = Duration.Zero
}
