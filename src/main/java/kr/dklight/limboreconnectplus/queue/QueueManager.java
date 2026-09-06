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

import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import kr.dklight.limboreconnectplus.LimboReconnect;
import kr.dklight.limboreconnectplus.handler.ReconnectHandler;

public class QueueManager {

  private final LimboReconnect plugin;
  private final ConcurrentMap<RegisteredServer, ServerQueue> queues = new ConcurrentHashMap<>();

  public QueueManager(LimboReconnect plugin) {
    this.plugin = plugin;
    this.plugin.getServer().getScheduler()
        .buildTask(this.plugin, () -> this.plugin.getGraceQueueManager().cleanExpired())
        .repeat(Duration.ofMinutes(5))
        .delay(Duration.ofMinutes(5))
        .schedule();
  }

  public ServerQueue getQueue(RegisteredServer server) {
    return this.queues.computeIfAbsent(server, s -> new ServerQueue(this.plugin, s));
  }

  public boolean enqueue(RegisteredServer server, ReconnectHandler handler) {
    return this.getQueue(server).enqueue(handler);
  }

  public void dequeue(RegisteredServer server, ReconnectHandler handler) {
    ServerQueue queue = this.queues.get(server);
    if (queue != null) {
      queue.dequeue(handler);
    }
  }

  public int getPosition(RegisteredServer server, ReconnectHandler handler) {
    ServerQueue queue = this.queues.get(server);
    if (queue != null) {
      return queue.getPosition(handler);
    }
    return -1;
  }

  public int getTotal(RegisteredServer server) {
    ServerQueue queue = this.queues.get(server);
    if (queue != null) {
      return queue.getTotal();
    }
    return 0;
  }

  public ServerQueue.ServerStatus getServerStatus(RegisteredServer server) {
    ServerQueue queue = this.queues.get(server);
    if (queue != null) {
      return queue.getServerStatus();
    }
    return ServerQueue.ServerStatus.OFFLINE;
  }

  public int getOnlinePlayers(RegisteredServer server) {
    ServerQueue queue = this.queues.get(server);
    if (queue != null) {
      return queue.getOnlinePlayers();
    }
    return 0;
  }

  public int getMaxPlayers(RegisteredServer server) {
    ServerQueue queue = this.queues.get(server);
    if (queue != null) {
      return queue.getMaxPlayers();
    }
    return 0;
  }

  public boolean enqueueWithGrace(RegisteredServer server, ReconnectHandler handler, int targetPosition) {
    return this.getQueue(server).enqueueWithGrace(handler, targetPosition);
  }

  public String getEstimatedWaitTime(RegisteredServer server, int position) {
    ServerQueue queue = this.queues.get(server);
    if (queue != null) {
      return queue.getEstimatedWaitTime(position);
    }
    return "~";
  }

  public ConcurrentMap<RegisteredServer, ServerQueue> getQueues() {
    return this.queues;
  }

  public ServerQueue getQueueByName(String serverName) {
    for (Map.Entry<RegisteredServer, ServerQueue> entry : this.queues.entrySet()) {
      if (entry.getKey().getServerInfo().getName().equalsIgnoreCase(serverName)) {
        return entry.getValue();
      }
    }
    return null;
  }

  public ReconnectHandler getHandlerByPlayer(com.velocitypowered.api.proxy.Player player) {
    for (ServerQueue queue : this.queues.values()) {
      for (ReconnectHandler handler : queue.getQueue()) {
        if (handler.getPlayer() != null
            && handler.getPlayer().getProxyPlayer().getUniqueId().equals(player.getUniqueId())) {
          return handler;
        }
      }
    }
    return null;
  }

  public void reload() {
    this.queues.values().forEach(ServerQueue::reload);
  }
}
