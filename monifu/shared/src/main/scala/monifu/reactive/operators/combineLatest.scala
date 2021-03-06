/*
 * Copyright (c) 2015 Alexandru Nedelcu
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

package monifu.reactive.operators

import monifu.concurrent.atomic.Atomic
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.internals._
import monifu.reactive.{Ack, Observable, Observer}
import scala.concurrent.Future


object combineLatest {
  /**
   * Implements [[monifu.reactive.Observable!.combineLatest]].
   */
  def apply[T, U](first: Observable[T], second: Observable[U]): Observable[(T, U)] = {
    Observable.create { subscriber =>
      implicit val s = subscriber.scheduler
      val observer = subscriber.observer

      val isDone = Atomic(false)
      // lock used for synchronization
      val lock = new AnyRef
      // MUST BE synchronized by `lock`
      var lastAck = Continue : Future[Ack]
      // MUST BE synchronized by `lock`
      var elemT: T = null.asInstanceOf[T]
      // MUST BE synchronized by `lock`
      var isElemTInitialized = false
      // MUST BE synchronized by `lock`
      var elemU: U = null.asInstanceOf[U]
      // MUST BE synchronized by `lock`
      var isElemUInitialized = false
      // MUST BE synchronized by `lock`
      var completedCount = 0

      // MUST BE synchronized by `lock`
      def signalOnNext(t: T, u: U) = {
        lastAck = lastAck.onContinueStreamOnNext(observer, t -> u)
        lastAck
      }

      def signalOnError(ex: Throwable): Unit = lock.synchronized {
        if (isDone.compareAndSet(expect = false, update = true)) {
          lastAck.onContinueSignalError(observer, ex)
          lastAck = Cancel
        }
      }

      def signalOnComplete(): Unit = lock.synchronized  {
        completedCount += 1

        if (completedCount == 2)
          if (isDone.compareAndSet(expect = false, update = true)) {
            lastAck.onContinueSignalComplete(observer)
            lastAck = Cancel
          }
      }

      first.unsafeSubscribe(new Observer[T] {
        def onNext(elem: T): Future[Ack] = lock.synchronized {
          if (isDone()) Cancel else {
            elemT = elem
            if (!isElemTInitialized)
              isElemTInitialized = true

            if (isElemUInitialized)
              signalOnNext(elemT, elemU)
            else
              Continue
          }
        }

        def onError(ex: Throwable): Unit =
          signalOnError(ex)

        def onComplete(): Unit =
          signalOnComplete()
      })

      second.unsafeSubscribe(new Observer[U] {
        def onNext(elem: U): Future[Ack] = lock.synchronized {
          if (isDone()) Cancel else {
            elemU = elem
            if (!isElemUInitialized)
              isElemUInitialized = true

            if (isElemTInitialized)
              signalOnNext(elemT, elemU)
            else
              Continue
          }
        }

        def onError(ex: Throwable): Unit =
          signalOnError(ex)

        def onComplete(): Unit =
          signalOnComplete()
      })
    }
  }
}
