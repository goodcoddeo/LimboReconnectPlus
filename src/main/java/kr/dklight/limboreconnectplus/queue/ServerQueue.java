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

import com.velocitypowered.api.proxy.server.PingOptions;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.api.scheduler.ScheduledTask;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import kr.dklight.limboreconnectplus.Config;
import kr.dklight.limboreconnectplus.LimboReconnect;
import kr.dklight.limboreconnectplus.handler.ReconnectHandler;
import kr.dklight.limboreconnectplus.webhook.DiscordWebhookNotifier;
import net.kyori.adventure.text.Component;

public class ServerQueue {

  public enum ServerStatus {
    OFFLINE,
    FULL,
    ONLINE
  }

  private static final ExecutorService ASYNC_PING_EXECUTOR = Executors.newFixedThreadPool(
      Math.max(4, Runtime.getRuntime().availableProcessors()),
      r -> {
        Thread thread = new Thread(r, "LimboReconnect-Heartbeat");
        thread.setDaemon(true);
        return thread;
      }
  );

  private final LimboReconnect plugin;
  private final RegisteredServer server;
  private final LinkedList<ReconnectHandler> queue = new LinkedList<>();
  private final QueueStats stats = new QueueStats();
  private final AtomicBoolean dispatching = new AtomicBoolean(false);
  private final AtomicBoolean pinging = new AtomicBoolean(false);
  private final List<Long> recentDispatchTimes = new ArrayList<>();
  private volatile ServerStatus serverStatus = ServerStatus.OFFLINE;
  private volatile boolean paused = false;
  private volatile int onlinePlayers = 0;
  private volatile int maxPlayers = 0;
  private volatile PingOptions pingOptions;
  private ScheduledTask pingTask;
  private long lastPingTime = 0;
  private long offlineSince = -1;

  public ServerQueue(LimboReconnect plugin, RegisteredServer server) {
    this.plugin = plugin;
    this.server = server;
    this.pingOptions = PingOptions.builder()
        .timeout(Duration.ofMillis(LimboReconnect.CONFIG.pingTimeout))
        .build();
  }

  public synchronized boolean enqueue(ReconnectHandler handler) {
    if (this.queue.contains(handler)) {
      return true;
    }

    boolean hasBypass = false;
    if (handler.getPlayer() != null
        && LimboReconnect.CONFIG.bypassPermission != null
        && !LimboReconnect.CONFIG.bypassPermission.isEmpty()) {
      hasBypass = handler.getPlayer().getProxyPlayer().hasPermission(LimboReconnect.CONFIG.bypassPermission);
    }

    int maxQueue = this.resolveMaxQueueSize();
    if (!hasBypass && maxQueue > 0 && this.queue.size() >= maxQueue) {
      return false;
    }

    if (hasBypass) {
      this.queue.addFirst(handler);
    } else if (LimboReconnect.CONFIG.priorityGroups != null && LimboReconnect.CONFIG.priorityGroups.enabled) {
      int insertIndex = 0;
      while (insertIndex < this.queue.size()) {
        ReconnectHandler existing = this.queue.get(insertIndex);
        if (handler.getPriority() > existing.getPriority()) {
          break;
        }
        insertIndex++;
      }
      this.queue.add(insertIndex, handler);
    } else {
      this.queue.add(handler);
    }

    this.stats.recordEnqueue(this.queue.size());
    this.ensurePingTask();
    return true;
  }

  public synchronized void dequeue(ReconnectHandler handler) {
    if (this.queue.remove(handler)) {
      this.stats.recordLeft();
    }
    if (this.queue.isEmpty()) {
      this.cancelPingTask();
    }
  }

  public synchronized int getPosition(ReconnectHandler handler) {
    int index = this.queue.indexOf(handler);
    return index >= 0 ? index + 1 : -1;
  }

  public synchronized int getTotal() {
    return this.queue.size();
  }

  public synchronized boolean enqueueWithGrace(ReconnectHandler handler, int targetPosition) {
    if (this.queue.contains(handler)) {
      return true;
    }

    boolean hasBypass = false;
    if (handler.getPlayer() != null
        && LimboReconnect.CONFIG.bypassPermission != null
        && !LimboReconnect.CONFIG.bypassPermission.isEmpty()) {
      hasBypass = handler.getPlayer().getProxyPlayer().hasPermission(LimboReconnect.CONFIG.bypassPermission);
    }

    int maxQueue = this.resolveMaxQueueSize();
    if (!hasBypass && maxQueue > 0 && this.queue.size() >= maxQueue) {
      return false;
    }

    if (hasBypass) {
      this.queue.addFirst(handler);
    } else {
      int targetIndex = Math.max(0, Math.min(this.queue.size(), targetPosition - 1));
      this.queue.add(targetIndex, handler);
    }

    this.stats.recordEnqueue(this.queue.size());
    this.ensurePingTask();
    return true;
  }

  public boolean isPaused() {
    return this.paused;
  }

  public void setPaused(boolean paused) {
    this.paused = paused;
  }

  public QueueStats getStats() {
    return this.stats;
  }

  public synchronized String getEstimatedWaitTime(int position) {
    Config.Messages.Eta etaConfig = LimboReconnect.CONFIG.messages.eta;
    String offlineText = (etaConfig != null && etaConfig.offline != null) ? etaConfig.offline : "Server Offline";
    String restartingText = (etaConfig != null && etaConfig.restarting != null) ? etaConfig.restarting : "Restarting...";
    String fullText = (etaConfig != null && etaConfig.full != null) ? etaConfig.full : "Waiting for slot";
    String instantText = (etaConfig != null && etaConfig.instant != null) ? etaConfig.instant : "< 5s";

    if (this.serverStatus == ServerStatus.OFFLINE) {
      return this.isRestarting() ? restartingText : offlineText;
    }

    if (this.serverStatus == ServerStatus.FULL) {
      return fullText;
    }

    int batchSize = this.resolveBatchSize();
    if (position <= 1 || position <= batchSize) {
      return instantText;
    }

    long batchInterval = this.resolveBatchInterval();
    if (batchInterval > 0) {
      int batchesAhead = (position - 1) / batchSize;
      long estimatedSec = (batchesAhead * batchInterval) / 1000;
      if (estimatedSec < 60) {
        return "~" + Math.max(5, estimatedSec) + "s";
      }
      long mins = estimatedSec / 60;
      long secs = estimatedSec % 60;
      return "~" + mins + "m" + (secs > 0 ? " " + secs + "s" : "");
    }

    return instantText;
  }

  public synchronized boolean skip(String playerName) {
    for (int i = 0; i < this.queue.size(); i++) {
      ReconnectHandler handler = this.queue.get(i);
      if (handler.getPlayer() != null
          && handler.getPlayer().getProxyPlayer().getUsername().equalsIgnoreCase(playerName)) {
        if (i > 0) {
          this.queue.remove(i);
          this.queue.addFirst(handler);
        }
        return true;
      }
    }
    return false;
  }

  public synchronized int kickAll(String fallbackServer, Component reason) {
    final int count = this.queue.size();
    List<ReconnectHandler> handlers = new ArrayList<>(this.queue);
    this.queue.clear();
    this.cancelPingTask();

    for (ReconnectHandler handler : handlers) {
      if (handler.isConnected() && handler.getPlayer() != null) {
        if (fallbackServer != null && !fallbackServer.isEmpty()) {
          Optional<RegisteredServer> targetFallback = this.plugin.getServer().getServer(fallbackServer);
          if (targetFallback.isPresent()) {
            handler.getPlayer().disconnect(targetFallback.get());
            continue;
          }
        }
        handler.getPlayer().getProxyPlayer().disconnect(reason);
      }
    }
    return count;
  }

  public synchronized List<ReconnectHandler> getQueue() {
    return new ArrayList<>(this.queue);
  }

  public ServerStatus getServerStatus() {
    return this.serverStatus;
  }

  public int getOnlinePlayers() {
    return this.onlinePlayers;
  }

  public int getMaxPlayers() {
    return this.maxPlayers;
  }

  public synchronized void reload() {
    this.pingOptions = PingOptions.builder()
        .timeout(Duration.ofMillis(LimboReconnect.CONFIG.pingTimeout))
        .build();
    if (!this.queue.isEmpty()) {
      this.ensurePingTask();
    }
  }

  private synchronized void ensurePingTask() {
    if (this.pingTask == null) {
      long interval = Math.max(50, Math.min(LimboReconnect.CONFIG.checkInterval, LimboReconnect.CONFIG.heartbeatInterval));
      this.pingTask = this.plugin.getServer().getScheduler()
          .buildTask(this.plugin, this::tick)
          .repeat(Duration.ofMillis(interval))
          .schedule();
      this.plugin.getServer().getScheduler()
          .buildTask(this.plugin, this::tick)
          .schedule();
    }
  }

  private synchronized void cancelPingTask() {
    if (this.pingTask != null) {
      this.pingTask.cancel();
      this.pingTask = null;
    }
    this.dispatching.set(false);
    this.pinging.set(false);
    this.lastPingTime = 0;
  }

  private void updateServerStatus(ServerPing ping) {
    ServerStatus previousStatus = this.serverStatus;
    if (ping == null) {
      this.serverStatus = ServerStatus.OFFLINE;
      if (this.offlineSince == -1) {
        this.offlineSince = System.currentTimeMillis();
      }
      if (previousStatus != ServerStatus.OFFLINE) {
        DiscordWebhookNotifier.notifyServerDown(this.server.getServerInfo().getName(), this.queue.size());
      }
      return;
    }

    this.offlineSince = -1;
    ping.getPlayers().ifPresentOrElse(players -> {
      this.onlinePlayers = players.getOnline();
      this.maxPlayers = players.getMax();
      if (this.maxPlayers > 0 && this.onlinePlayers >= this.maxPlayers) {
        this.serverStatus = ServerStatus.FULL;
      } else {
        this.serverStatus = ServerStatus.ONLINE;
      }
    }, () -> {
      this.serverStatus = ServerStatus.ONLINE;
    });

    if (previousStatus == ServerStatus.OFFLINE && this.serverStatus != ServerStatus.OFFLINE) {
      DiscordWebhookNotifier.notifyServerUp(this.server.getServerInfo().getName(), this.queue.size());
    }
  }

  public boolean isRestarting() {
    if (this.serverStatus != ServerStatus.OFFLINE) {
      return false;
    }
    if (this.offlineSince == -1) {
      return true;
    }
    long elapsedSec = (System.currentTimeMillis() - this.offlineSince) / 1000;
    return elapsedSec < LimboReconnect.CONFIG.restartingTimeout;
  }

  private void pingAsync(Consumer<ServerPing> onSuccess, Runnable onFailure) {
    this.server.ping(this.pingOptions)
        .orTimeout(LimboReconnect.CONFIG.pingTimeout, TimeUnit.MILLISECONDS)
        .whenCompleteAsync((ping, exception) -> {
          if (exception != null) {
            this.updateServerStatus(null);
            if (onFailure != null) {
              onFailure.run();
            }
            return;
          }
          this.updateServerStatus(ping);
          if (onSuccess != null) {
            onSuccess.accept(ping);
          }
        }, ASYNC_PING_EXECUTOR);
  }

  private void tick() {
    synchronized (this) {
      if (this.queue.isEmpty() || this.dispatching.get() || this.paused) {
        return;
      }
    }

    long now = System.currentTimeMillis();
    long configuredHeartbeat = LimboReconnect.CONFIG.heartbeatInterval;
    long requiredInterval = this.serverStatus == ServerStatus.OFFLINE
        ? Math.max(50, configuredHeartbeat)
        : Math.max(50, LimboReconnect.CONFIG.checkInterval);

    if (now - this.lastPingTime < requiredInterval) {
      return;
    }

    if (!this.pinging.compareAndSet(false, true)) {
      return;
    }
    this.lastPingTime = now;

    this.pingAsync(ping -> {
      try {
        if (this.serverStatus == ServerStatus.FULL && this.resolveWaitForSlots()) {
          if (LimboReconnect.CONFIG.debug) {
            LimboReconnect.getLogger().info("Server {} is full ({}/{}), waiting for slots...",
                this.server.getServerInfo().getName(), this.onlinePlayers, this.maxPlayers);
          }
          return;
        }

        synchronized (this) {
          if (this.queue.isEmpty() || this.paused || !this.dispatching.compareAndSet(false, true)) {
            return;
          }
        }

        if (LimboReconnect.CONFIG.debug) {
          LimboReconnect.getLogger().info("Server {} is ready ({}/{}), starting queue dispatch for {} players",
              this.server.getServerInfo().getName(), this.onlinePlayers, this.maxPlayers, this.getTotal());
        }

        long joinDelay = Math.max(0, LimboReconnect.CONFIG.joinDelay);
        this.plugin.getServer().getScheduler()
            .buildTask(this.plugin, this::dispatchNext)
            .delay(Duration.ofMillis(joinDelay))
            .schedule();
      } finally {
        this.pinging.set(false);
      }
    }, () -> {
      this.pinging.set(false);
      if (LimboReconnect.CONFIG.debug) {
        LimboReconnect.getLogger().info("Can't ping server {}", this.server.getServerInfo().getName());
      }
    });
  }

  private void recordDispatchTime() {
    long now = System.currentTimeMillis();
    this.recentDispatchTimes.add(now);
    if (this.recentDispatchTimes.size() > 8) {
      this.recentDispatchTimes.remove(0);
    }
  }

  private void dispatchNext() {
    List<ReconnectHandler> batch = new ArrayList<>();

    synchronized (this) {
      if (this.paused) {
        this.dispatching.set(false);
        return;
      }

      int targetBatchSize = this.resolveBatchSize();
      if (this.resolveWaitForSlots() && this.maxPlayers > 0) {
        int availableSlots = Math.max(0, this.maxPlayers - this.onlinePlayers);
        targetBatchSize = Math.min(targetBatchSize, availableSlots);
        if (targetBatchSize <= 0) {
          this.dispatching.set(false);
          return;
        }
      }

      while (!this.queue.isEmpty() && batch.size() < targetBatchSize) {
        ReconnectHandler candidate = this.queue.removeFirst();
        if (candidate.isConnected()) {
          batch.add(candidate);
        }
      }

      if (batch.isEmpty()) {
        this.dispatching.set(false);
        if (this.queue.isEmpty()) {
          this.cancelPingTask();
        }
        return;
      }

      long now = System.currentTimeMillis();
      for (ReconnectHandler handler : batch) {
        this.stats.recordDispatch(now - handler.getEntryTime());
        this.recordDispatchTime();
      }
    }

    for (ReconnectHandler handler : batch) {
      handler.connect();
    }

    synchronized (this) {
      if (this.queue.isEmpty()) {
        this.dispatching.set(false);
        this.cancelPingTask();
        return;
      }
    }

    long interval = this.resolveBatchInterval();
    this.plugin.getServer().getScheduler()
        .buildTask(this.plugin, () -> {
          this.pingAsync(ping -> {
            if ((this.serverStatus == ServerStatus.FULL && this.resolveWaitForSlots()) || this.paused) {
              this.dispatching.set(false);
              return;
            }

            this.dispatchNext();
          }, () -> {
            this.dispatching.set(false);
          });
        })
        .delay(Duration.ofMillis(interval))
        .schedule();
  }

  private boolean resolveWaitForSlots() {
    String serverName = this.server.getServerInfo().getName();
    if (LimboReconnect.CONFIG.servers != null && LimboReconnect.CONFIG.servers.containsKey(serverName)) {
      Config.ServerOverride override = LimboReconnect.CONFIG.servers.get(serverName);
      if (override != null) {
        return override.waitForSlots;
      }
    }
    return LimboReconnect.CONFIG.waitForSlots;
  }

  public int resolveMaxQueueSize() {
    String serverName = this.server.getServerInfo().getName();
    if (LimboReconnect.CONFIG.servers != null && LimboReconnect.CONFIG.servers.containsKey(serverName)) {
      Config.ServerOverride override = LimboReconnect.CONFIG.servers.get(serverName);
      if (override != null && override.maxQueueSize >= 0) {
        return override.maxQueueSize;
      }
    }
    return LimboReconnect.CONFIG.maxQueueSize;
  }

  public int resolveBatchSize() {
    String serverName = this.server.getServerInfo().getName();
    if (LimboReconnect.CONFIG.servers != null && LimboReconnect.CONFIG.servers.containsKey(serverName)) {
      Config.ServerOverride override = LimboReconnect.CONFIG.servers.get(serverName);
      if (override != null && override.batchSize >= 1) {
        return override.batchSize;
      }
    }
    return Math.max(1, LimboReconnect.CONFIG.batchSize);
  }

  public long resolveBatchInterval() {
    String serverName = this.server.getServerInfo().getName();
    if (LimboReconnect.CONFIG.servers != null && LimboReconnect.CONFIG.servers.containsKey(serverName)) {
      Config.ServerOverride override = LimboReconnect.CONFIG.servers.get(serverName);
      if (override != null && override.batchInterval >= 0) {
        return override.batchInterval;
      }
    }
    if (LimboReconnect.CONFIG.batchInterval > 0) {
      return LimboReconnect.CONFIG.batchInterval;
    }
    return Math.max(0, LimboReconnect.CONFIG.queueDelay);
  }
}
