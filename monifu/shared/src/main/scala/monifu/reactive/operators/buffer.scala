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

import monifu.reactive.Ack.Continue
import monifu.reactive.internals._
import monifu.reactive.{Ack, Observable, Observer}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.concurrent.duration.{Duration, FiniteDuration}


object buffer {
  /**
   * Implementation for [[Observable.buffer]].
   */
  def sized[T](source: Observable[T], count: Int): Observable[Seq[T]] =
    Observable.create { subscriber =>
      implicit val s = subscriber.scheduler
      val observer = subscriber.observer

      source.unsafeSubscribe(new Observer[T] {
        private[this] var buffer = ArrayBuffer.empty[T]
        private[this] var size = 0

        def onNext(elem: T): Future[Ack] = {
          size += 1
          buffer.append(elem)
          if (size >= count) {
            val oldBuffer = buffer
            buffer = ArrayBuffer.empty[T]
            size = 0

            observer.onNext(oldBuffer)
          }
          else
            Continue
        }

        def onError(ex: Throwable): Unit = {
          if (size > 0) {
            observer.onNext(buffer).onContinueSignalError(observer, ex)
            buffer = null
          }
          else
            observer.onError(ex)
        }

        def onComplete(): Unit = {
          if (size > 0) {
            observer.onNext(buffer).onContinueSignalComplete(observer)
            buffer = null
          }
          else
            observer.onComplete()
        }
      })
    }

  /**
   * Implementation for [[Observable.bufferTimed]].
   */
  def timed[T](source: Observable[T], timespan: FiniteDuration): Observable[Seq[T]] = {
    require(timespan >= Duration.Zero, "timespan must be positive")

    Observable.create[Seq[T]] { subscriber =>
      implicit val s = subscriber.scheduler
      val observer = subscriber.observer

      source.unsafeSubscribe(new Observer[T] {
        private[this] val timespanNanos = timespan.toNanos
        private[this] var buffer = ArrayBuffer.empty[T]
        private[this] var expiresAt = s.nanoTime() + timespanNanos

        def onNext(elem: T) = {
          val rightNow = s.nanoTime()
          buffer.append(elem)

          if (expiresAt <= rightNow) {
            val oldBuffer = buffer
            buffer = ArrayBuffer.empty[T]
            expiresAt = rightNow + timespanNanos
            observer.onNext(oldBuffer)
          }
          else
            Continue
        }

        def onError(ex: Throwable): Unit = {
          if (buffer.nonEmpty) {
            observer.onNext(buffer)
              .onContinueSignalError(observer, ex)
            buffer = null
          }
          else
            observer.onError(ex)
        }

        def onComplete(): Unit = {
          if (buffer.nonEmpty) {
            observer.onNext(buffer)
              .onContinueSignalComplete(observer)
            buffer = null
          }
          else
            observer.onComplete()
        }
      })
    }
  }

  /**
   * Implementation for [[Observable.bufferSizedAndTimed]].
   */
  def sizedAndTimed[T](source: Observable[T], count: Int, timespan: FiniteDuration): Observable[Seq[T]] = {
    require(timespan >= Duration.Zero, "timespan must be positive")

    Observable.create[Seq[T]] { subscriber =>
      implicit val s = subscriber.scheduler
      val observer = subscriber.observer

      source.unsafeSubscribe(new Observer[T] {
        private[this] val timespanNanos = timespan.toNanos
        private[this] var buffer = ArrayBuffer.empty[T]
        private[this] var expiresAt = s.nanoTime() + timespanNanos

        def onNext(elem: T) = {
          val rightNow = s.nanoTime()
          buffer.append(elem)

          if (expiresAt <= rightNow || buffer.length >= count) {
            val oldBuffer = buffer
            buffer = ArrayBuffer.empty[T]
            expiresAt = rightNow + timespanNanos
            observer.onNext(oldBuffer)
          }
          else
            Continue
        }

        def onError(ex: Throwable): Unit = {
          if (buffer.nonEmpty) {
            observer.onNext(buffer).onContinueSignalError(observer, ex)
            buffer = null
          }
          else
            observer.onError(ex)
        }

        def onComplete(): Unit = {
          if (buffer.nonEmpty) {
            observer.onNext(buffer).onContinueSignalComplete(observer)
            buffer = null
          }
          else
            observer.onComplete()
        }
      })
    }
  }

}
