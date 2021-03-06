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

package monifu.reactive.builders

import monifu.reactive.Observable
import monifu.reactive.internals._
import scala.concurrent.duration._


object interval {
  /**
   * Creates an Observable that emits auto-incremented natural numbers (longs) spaced equally by
   * a given time interval. Starts from 0 with no delay, after which it emits incremented
   * numbers spaced by the `period` of time.
   *
   * <img src="https://raw.githubusercontent.com/wiki/monifu/monifu/assets/rx-operators/interval.png"" />
   *
   * @param delay the delay between two subsequent events
   */
  def withFixedDelay(delay: FiniteDuration): Observable[Long] = {
    Observable.create { subscriber =>
      implicit val s = subscriber.scheduler
      val o = subscriber.observer

      s.execute(new Runnable { self =>
        import monifu.reactive.internals.ObserverState.{ON_CONTINUE, ON_NEXT}
        var state = ON_NEXT
        var counter = 0L

        def run() = state match {
          case ON_NEXT =>
            state = ON_CONTINUE
            o.onNext(counter).onContinue(self)

          case ON_CONTINUE =>
            state = ON_NEXT
            counter += 1
            s.scheduleOnce(delay, self)
        }
      })
    }
  }

  /**
   * Creates an Observable that emits auto-incremented natural numbers (longs) emitted
   * at a fixed rate, as specified by `period`.
   *
   * @param period the delay between two subsequent events
   */
  def atFixedRate(period: FiniteDuration): Observable[Long] = {
    Observable.create { subscriber =>
      implicit val s = subscriber.scheduler
      val o = subscriber.observer

      s.execute(new Runnable { self =>
        import monifu.reactive.internals.ObserverState.{ON_CONTINUE, ON_NEXT}
        var state = ON_NEXT
        var counter = 0L
        var startedAt = 0L

        def run() = state match {
          case ON_NEXT =>
            state = ON_CONTINUE
            startedAt = s.nanoTime()
            o.onNext(counter).onContinue(self)

          case ON_CONTINUE =>
            state = ON_NEXT
            counter += 1

            val delay = {
              val duration = (s.nanoTime() - startedAt).nanos
              val d = period - duration
              if (d >= Duration.Zero) d else Duration.Zero
            }

            s.scheduleOnce(delay, self)
        }
      })
    }
  }
}
