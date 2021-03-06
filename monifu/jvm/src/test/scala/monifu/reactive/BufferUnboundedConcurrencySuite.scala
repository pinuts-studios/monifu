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

package monifu.reactive

import java.util.concurrent.{TimeUnit, CountDownLatch}
import minitest.TestSuite
import monifu.concurrent.Scheduler
import monifu.reactive.Ack.Continue
import monifu.reactive.BufferPolicy.Unbounded
import monifu.reactive.observers.BufferedSubscriber
import scala.concurrent.{Promise, Future}

object BufferUnboundedConcurrencySuite extends TestSuite[Scheduler] {
  def tearDown(env: Scheduler) = ()
  def setup() = {
    monifu.concurrent.Implicits.globalScheduler
  }

  test("should not lose events, test 1") { implicit s =>
    var number = 0
    val completed = new CountDownLatch(1)

    val underlying = new Observer[Int] {
      def onNext(elem: Int): Future[Ack] = {
        number += 1
        Continue
      }

      def onError(ex: Throwable): Unit = {
        s.reportFailure(ex)
      }

      def onComplete(): Unit = {
        completed.countDown()
      }
    }

    val buffer = BufferedSubscriber(underlying, Unbounded)
    for (i <- 0 until 100000) buffer.observer.onNext(i)
    buffer.observer.onComplete()

    assert(completed.await(20, TimeUnit.SECONDS), "completed.await should have succeeded")
    assertEquals(number, 100000)
  }

  test("should not lose events, test 2") { implicit s =>
    var number = 0
    val completed = new CountDownLatch(1)

    val underlying = new Observer[Int] {
      def onNext(elem: Int): Future[Ack] = {
        number += 1
        Continue
      }

      def onError(ex: Throwable): Unit = {
        s.reportFailure(ex)
      }

      def onComplete(): Unit = {
        completed.countDown()
      }
    }

    val buffer = BufferedSubscriber(underlying, Unbounded)

    def loop(n: Int): Unit =
      if (n > 0) s.execute(new Runnable {
        def run() = { buffer.observer.onNext(n); loop(n-1) }
      })
      else buffer.observer.onComplete()

    loop(10000)
    assert(completed.await(20, TimeUnit.SECONDS), "completed.await should have succeeded")
    assertEquals(number, 10000)
  }

  test("should send onError when empty") { implicit s =>
    val latch = new CountDownLatch(1)
    val buffer = BufferedSubscriber(new Observer[Int] {
      def onError(ex: Throwable) = {
        assertEquals(ex.getMessage, "dummy")
        latch.countDown()
      }
      def onNext(elem: Int) = throw new IllegalStateException()
      def onComplete() = throw new IllegalStateException()
    })

    buffer.observer.onError(new RuntimeException("dummy"))
    assert(latch.await(5, TimeUnit.SECONDS), "latch.await should have succeeded")
  }

  test("should send onError when in flight") { implicit s =>
    val latch = new CountDownLatch(1)
    val buffer = BufferedSubscriber(new Observer[Int] {
      def onError(ex: Throwable) = {
        assertEquals(ex.getMessage, "dummy")
        latch.countDown()
      }
      def onNext(elem: Int) = Continue
      def onComplete() = throw new IllegalStateException()
    })

    buffer.observer.onNext(1)
    buffer.observer.onError(new RuntimeException("dummy"))
    assert(latch.await(5, TimeUnit.SECONDS), "latch.await should have succeeded")
  }

  test("should send onComplete when empty") { implicit s =>
    val latch = new CountDownLatch(1)
    val buffer = BufferedSubscriber(new Observer[Int] {
      def onError(ex: Throwable) = throw new IllegalStateException()
      def onNext(elem: Int) = throw new IllegalStateException()
      def onComplete() = latch.countDown()
    })

    buffer.observer.onComplete()
    assert(latch.await(5, TimeUnit.SECONDS), "latch.await should have succeeded")
  }

  test("should send onComplete when in flight") { implicit s =>
    val latch = new CountDownLatch(1)
    val promise = Promise[Ack]()
    val buffer = BufferedSubscriber(new Observer[Int] {
      def onError(ex: Throwable) = throw new IllegalStateException()
      def onNext(elem: Int) = promise.future
      def onComplete() = latch.countDown()
    })

    buffer.observer.onNext(1)
    buffer.observer.onComplete()
    assert(!latch.await(1, TimeUnit.SECONDS), "latch.await should have failed")

    promise.success(Continue)
    assert(latch.await(5, TimeUnit.SECONDS), "latch.await should have succeeded")
  }

  test("should do onComplete only after all the queue was drained") { implicit s =>
    var sum = 0L
    val complete = new CountDownLatch(1)
    val startConsuming = Promise[Continue]()

    val buffer = BufferedSubscriber(new Observer[Long] {
      def onNext(elem: Long) = {
        sum += elem
        startConsuming.future
      }
      def onError(ex: Throwable) = throw ex
      def onComplete() = complete.countDown()
    })

    (0 until 9999).foreach(x => buffer.observer.onNext(x))
    buffer.observer.onComplete()
    startConsuming.success(Continue)

    assert(complete.await(10, TimeUnit.SECONDS), "complete.await should have succeeded")
    assert(sum == (0 until 9999).sum)
  }

  test("should do onComplete only after all the queue was drained, test2") { implicit s =>
    var sum = 0L
    val complete = new CountDownLatch(1)

    val buffer = BufferedSubscriber(new Observer[Long] {
      def onNext(elem: Long) = {
        sum += elem
        Continue
      }
      def onError(ex: Throwable) = throw ex
      def onComplete() = complete.countDown()
    })

    (0 until 9999).foreach(x => buffer.observer.onNext(x))
    buffer.observer.onComplete()

    assert(complete.await(10, TimeUnit.SECONDS), "complete.await should have succeeded")
    assert(sum == (0 until 9999).sum)
  }

  test("should do onError only after the queue was drained") { implicit s =>
    var sum = 0L
    val complete = new CountDownLatch(1)
    val startConsuming = Promise[Continue]()

    val buffer = BufferedSubscriber(new Observer[Long] {
      def onNext(elem: Long) = {
        sum += elem
        startConsuming.future
      }
      def onError(ex: Throwable) = complete.countDown()
      def onComplete() = throw new IllegalStateException()
    })

    (0 until 9999).foreach(x => buffer.observer.onNext(x))
    buffer.observer.onError(new RuntimeException)
    startConsuming.success(Continue)

    assert(complete.await(10, TimeUnit.SECONDS), "complete.await should have succeeded")
    assertEquals(sum, (0 until 9999).sum)
  }

  test("should do onError only after all the queue was drained, test2") { implicit s =>
    var sum = 0L
    val complete = new CountDownLatch(1)

    val buffer = BufferedSubscriber(new Observer[Long] {
      def onNext(elem: Long) = {
        sum += elem
        Continue
      }
      def onError(ex: Throwable) = complete.countDown()
      def onComplete() = throw new IllegalStateException()
    })

    (0 until 9999).foreach(x => buffer.observer.onNext(x))
    buffer.observer.onError(new RuntimeException)

    assert(complete.await(10, TimeUnit.SECONDS), "complete.await should have succeeded")
    assertEquals(sum, (0 until 9999).sum)
  }
}
