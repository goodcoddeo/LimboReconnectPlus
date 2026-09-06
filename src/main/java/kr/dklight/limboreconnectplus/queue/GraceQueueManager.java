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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GraceQueueManager {

  public static class GraceEntry {

    private final UUID playerUuid;
    private final String serverName;
    private final int originalPosition;
    private final int originalTotal;
    private final long disconnectTime;
    private final String groupName;
    private final int priority;
    private final long gracePeriodSeconds;
    private final double retentionRatio;

    public GraceEntry(UUID playerUuid, String serverName, int originalPosition, int originalTotal,
                      String groupName, int priority, long gracePeriodSeconds, double retentionRatio) {
      this.playerUuid = playerUuid;
      this.serverName = serverName;
      this.originalPosition = originalPosition;
      this.originalTotal = originalTotal;
      this.disconnectTime = System.currentTimeMillis();
      this.groupName = groupName;
      this.priority = priority;
      this.gracePeriodSeconds = gracePeriodSeconds;
      this.retentionRatio = retentionRatio;
    }

    public UUID getPlayerUuid() {
      return this.playerUuid;
    }

    public String getServerName() {
      return this.serverName;
    }

    public int getOriginalPosition() {
      return this.originalPosition;
    }

    public int getOriginalTotal() {
      return this.originalTotal;
    }

    public String getGroupName() {
      return this.groupName;
    }

    public int getPriority() {
      return this.priority;
    }

    public boolean isExpired() {
      if (this.gracePeriodSeconds <= 0) {
        return true;
      }
      return (System.currentTimeMillis() - this.disconnectTime) > (this.gracePeriodSeconds * 1000);
    }

    public int calculateRestoredPosition(int currentQueueSize) {
      if (this.retentionRatio >= 1.0) {
        return Math.max(1, this.originalPosition);
      }
      if (this.retentionRatio <= 0.0) {
        return currentQueueSize + 1;
      }
      int penalty = (int) Math.round((currentQueueSize + 1 - this.originalPosition) * (1.0 - this.retentionRatio));
      return Math.max(1, this.originalPosition + penalty);
    }
  }

  private final Map<UUID, GraceEntry> graceSessions = new ConcurrentHashMap<>();

  public void recordDisconnect(UUID playerUuid, String serverName, int position, int total,
                               String groupName, int priority, long gracePeriod, double retentionRatio) {
    if (gracePeriod <= 0 || position <= 0) {
      return;
    }
    this.graceSessions.put(playerUuid, new GraceEntry(
        playerUuid, serverName, position, total, groupName, priority, gracePeriod, retentionRatio
    ));
  }

  public GraceEntry claimGraceSession(UUID playerUuid, String serverName) {
    GraceEntry entry = this.graceSessions.get(playerUuid);
    if (entry == null) {
      return null;
    }
    if (!entry.getServerName().equalsIgnoreCase(serverName) || entry.isExpired()) {
      this.graceSessions.remove(playerUuid);
      return null;
    }
    this.graceSessions.remove(playerUuid);
    return entry;
  }

  public void cleanExpired() {
    this.graceSessions.values().removeIf(GraceEntry::isExpired);
  }
}
