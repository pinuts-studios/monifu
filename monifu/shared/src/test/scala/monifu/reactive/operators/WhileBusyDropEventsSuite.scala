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

import minitest.TestSuite
import monifu.concurrent.schedulers.TestScheduler
import monifu.reactive.Ack.Continue
import monifu.reactive.{Observer, Observable}
import monifu.reactive.subjects.PublishSubject

import scala.concurrent.Promise
import scala.util.Success

object WhileBusyDropEventsSuite extends TestSuite[TestScheduler] {
  def setup() = TestScheduler()
  def tearDown(s: TestScheduler) = {
    assert(s.state.get.tasks.isEmpty,
      "TestScheduler should have no pending tasks")
  }

  test("should not drop events for synchronous observers") { implicit s =>
    val f = Observable.range(0, 1000).whileBusyDropEvents.sum.asFuture
    s.tick()

    assertEquals(f.value, Some(Success(Some(999 * 500))))
  }

  test("should drop events for busy observers") { implicit s =>
    val source = PublishSubject[Long]()
    val p = Promise[Continue]()
    var received = 0L
    var wasCompleted = false

    source.whileBusyDropEvents.unsafeSubscribe(new Observer[Long] {
      def onNext(elem: Long) = {
        received += elem
        p.future
      }

      def onError(ex: Throwable) = ()
      def onComplete() = {
        wasCompleted = true
      }
    })

    source.onNext(1)
    s.tick()
    assertEquals(received, 1)

    for (i <- 0 until 100) source.onNext(i)
    assertEquals(received, 1)

    p.success(Continue)
    s.tick()
    assertEquals(received, 1)

    for (i <- 100 until 200) source.onNext(i)
    assertEquals(received, (100 until 200).sum + 1)
  }
}
