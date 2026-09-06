/*
 * Copyright (C) 2022 - 2025 Elytrium
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package kr.dklight.limboreconnectplus.queue;

public class QueueStats {

  private long totalEnqueued = 0;
  private long totalDispatched = 0;
  private long totalLeft = 0;
  private int peakQueueSize = 0;
  private long totalWaitTimeMs = 0;

  public QueueStats() {
  }

  public synchronized void recordEnqueue(int currentSize) {
    this.totalEnqueued++;
    if (currentSize > this.peakQueueSize) {
      this.peakQueueSize = currentSize;
    }
  }

  public synchronized void recordDispatch(long waitTimeMs) {
    this.totalDispatched++;
    this.totalWaitTimeMs += Math.max(0, waitTimeMs);
  }

  public synchronized void recordLeft() {
    this.totalLeft++;
  }

  public synchronized long getTotalEnqueued() {
    return this.totalEnqueued;
  }

  public synchronized long getTotalDispatched() {
    return this.totalDispatched;
  }

  public synchronized long getTotalLeft() {
    return this.totalLeft;
  }

  public synchronized int getPeakQueueSize() {
    return this.peakQueueSize;
  }

  public synchronized double getAverageWaitTimeSeconds() {
    if (this.totalDispatched == 0) {
      return 0.0;
    }
    return (double) this.totalWaitTimeMs / (double) this.totalDispatched / 1000.0;
  }
}
