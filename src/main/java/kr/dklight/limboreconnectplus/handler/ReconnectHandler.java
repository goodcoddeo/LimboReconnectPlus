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

package kr.dklight.limboreconnectplus.handler;

import static kr.dklight.limboreconnectplus.LimboReconnect.CONFIG;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import kr.dklight.limboreconnectplus.Config;
import kr.dklight.limboreconnectplus.LimboReconnect;
import kr.dklight.limboreconnectplus.protocol.packets.PlaySound;
import kr.dklight.limboreconnectplus.queue.GraceQueueManager;
import kr.dklight.limboreconnectplus.queue.ServerQueue;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboSessionHandler;
import net.elytrium.limboapi.api.player.LimboPlayer;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.ComponentSerializer;
import net.kyori.adventure.title.Title;

public class ReconnectHandler implements LimboSessionHandler {

  private final LimboReconnect plugin;
  private final RegisteredServer server;
  private final long spawnTime;
  private LimboPlayer player;
  private BossBar bossBar;
  private boolean connected = true;
  private boolean connecting = false;
  private int titleIndex = -1;
  private int lastChatPosition = -1;
  private long lastChatTime = 0;
  private int priority = 0;
  private String groupName = "default";
  private long entryTime;
  private long offlineSince = -1;
  private double currentX;
  private double currentY;
  private double currentZ;
  private final PlaySound waitSound;
  private final PlaySound connectSound;

  public ReconnectHandler(LimboReconnect plugin, RegisteredServer server) {
    this.plugin = plugin;
    this.server = server;
    this.spawnTime = System.currentTimeMillis();
    this.currentX = CONFIG.world.playerCoords.x;
    this.currentY = CONFIG.world.playerCoords.y;
    this.currentZ = CONFIG.world.playerCoords.z;
    Config.Sounds sounds = CONFIG.sounds;
    float waitVol = Math.max(sounds.waiting.volume, 1000.0f);
    float connVol = Math.max(sounds.connecting.volume, 1000.0f);
    this.waitSound = new PlaySound(sounds.waiting.name, this.currentX, this.currentY, this.currentZ, waitVol, sounds.waiting.pitch);
    this.connectSound = new PlaySound(sounds.connecting.name, this.currentX, this.currentY, this.currentZ, connVol, sounds.connecting.pitch);
  }

  @Override
  public void onSpawn(Limbo server, LimboPlayer player) {
    this.player = player;
    this.entryTime = System.currentTimeMillis();
    this.computePriorityAndGroup();
    this.player.disableFalling();
    this.player.setGameMode(CONFIG.world.gamemode);
    this.player.sendAbilities((byte) 0x06, 0.05f, 0.1f);

    GraceQueueManager.GraceEntry grace = this.plugin.getGraceQueueManager().claimGraceSession(
        this.player.getProxyPlayer().getUniqueId(), this.server.getServerInfo().getName()
    );
    boolean enqueued;
    if (grace != null) {
      int restoredPos = grace.calculateRestoredPosition(this.plugin.getQueueManager().getTotal(this.server));
      enqueued = this.plugin.getQueueManager().enqueueWithGrace(this.server, this, restoredPos);
      if (enqueued && CONFIG.messages.graceRestored != null && !CONFIG.messages.graceRestored.isEmpty()) {
        ComponentSerializer<Component, Component, String> serializer = LimboReconnect.getSerializer();
        String msg = CONFIG.messages.graceRestored.replace("%position%", String.valueOf(restoredPos));
        this.player.getProxyPlayer().sendMessage(serializer.deserialize(msg));
      }
    } else {
      enqueued = this.plugin.getQueueManager().enqueue(this.server, this);
    }

    if (!enqueued) {
      this.handleQueueFull();
      return;
    }

    this.tickMessages();
  }

  @Override
  public void onDisconnect() {
    this.connected = false;
    this.cleanVisuals();

    Config.PriorityGroups pg = CONFIG.priorityGroups;
    long gracePeriod = 0;
    double retentionRatio = 1.0;
    if (pg != null && pg.groups != null && pg.groups.containsKey(this.groupName)) {
      Config.PriorityGroups.Group grp = pg.groups.get(this.groupName);
      if (grp != null) {
        gracePeriod = grp.gracePeriod;
        retentionRatio = grp.retentionRatio;
      }
    }

    int currentPos = this.plugin.getQueueManager().getPosition(this.server, this);
    int total = this.plugin.getQueueManager().getTotal(this.server);
    if (currentPos > 0 && gracePeriod > 0 && this.player != null) {
      this.plugin.getGraceQueueManager().recordDisconnect(
          this.player.getProxyPlayer().getUniqueId(),
          this.server.getServerInfo().getName(),
          currentPos, total, this.groupName, this.priority, gracePeriod, retentionRatio
      );
    }

    this.plugin.getQueueManager().dequeue(this.server, this);
  }

  public void cleanVisuals() {
    if (this.player != null) {
      if (this.bossBar != null) {
        this.player.getProxyPlayer().hideBossBar(this.bossBar);
        this.bossBar = null;
      }
      this.player.getProxyPlayer().sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
    }
  }

  @Override
  public void onMove(double x, double y, double z) {
    this.currentX = x;
    this.currentY = y;
    this.currentZ = z;
    this.waitSound.setPosition(x, y, z);
    this.connectSound.setPosition(x, y, z);

    if (CONFIG.world.voidRespawn && y < CONFIG.world.voidY && this.player != null) {
      this.player.teleport(
          CONFIG.world.playerCoords.x,
          CONFIG.world.playerCoords.y,
          CONFIG.world.playerCoords.z,
          CONFIG.world.playerCoords.yaw,
          CONFIG.world.playerCoords.pitch
      );
      this.currentX = CONFIG.world.playerCoords.x;
      this.currentY = CONFIG.world.playerCoords.y;
      this.currentZ = CONFIG.world.playerCoords.z;
      this.waitSound.setPosition(this.currentX, this.currentY, this.currentZ);
      this.connectSound.setPosition(this.currentX, this.currentY, this.currentZ);
      this.player.sendAbilities((byte) 0x06, 0.05f, 0.1f);
    }
  }

  @Override
  public void onGeneric(Object packet) {
  }

  @Override
  public void onChat(String text) {
    if (text == null || !text.startsWith("/")) {
      return;
    }
    String cmd = text.substring(1).trim().toLowerCase(Locale.ROOT);
    if (cmd.equals("queue status") || cmd.equals("status")) {
      this.sendStatusMessage();
      return;
    }

    this.handleEscapeCommand(cmd);
  }

  public void sendStatusMessage() {
    if (this.player == null) {
      return;
    }
    int position = this.plugin.getQueueManager().getPosition(this.server, this);
    int total = this.plugin.getQueueManager().getTotal(this.server);
    int ahead = Math.max(0, position - 1);
    String serverName = this.server.getServerInfo().getName();
    ServerQueue.ServerStatus status = this.plugin.getQueueManager().getServerStatus(this.server);
    int online = this.plugin.getQueueManager().getOnlinePlayers(this.server);
    int max = this.plugin.getQueueManager().getMaxPlayers(this.server);
    String statusStr = this.resolveStatusString(status);
    String eta = this.plugin.getQueueManager().getEstimatedWaitTime(this.server, position);

    String raw = "<gold><bold>Queue Status:</bold></gold> <yellow>%server%</yellow> "
        + "(<green>%status%</green>) | Position: <gold>%position%</gold>/<yellow>%total%</yellow> | ETA: <gold>%eta%</gold>";
    ComponentSerializer<Component, Component, String> serializer = LimboReconnect.getSerializer();
    String formatted = this.replacePlaceholders(raw, position, total, ahead, serverName, online, max, statusStr, eta);
    this.player.getProxyPlayer().sendMessage(serializer.deserialize(formatted));
  }

  private void handleQueueFull() {
    this.connected = false;
    this.cleanVisuals();

    String fallback = this.resolveFallbackServer();
    if (fallback != null && !fallback.isEmpty()) {
      Optional<RegisteredServer> targetFallback = this.plugin.getServer().getServer(fallback);
      if (targetFallback.isPresent()) {
        if (CONFIG.maxQueueMessage != null && !CONFIG.maxQueueMessage.isEmpty()) {
          ComponentSerializer<Component, Component, String> serializer = LimboReconnect.getSerializer();
          this.player.getProxyPlayer().sendMessage(serializer.deserialize(CONFIG.maxQueueMessage));
        }
        this.player.disconnect(targetFallback.get());
        return;
      }
    }

    ComponentSerializer<Component, Component, String> serializer = LimboReconnect.getSerializer();
    String msg = (CONFIG.maxQueueMessage != null && !CONFIG.maxQueueMessage.isEmpty())
        ? CONFIG.maxQueueMessage
        : "<red>Queue is currently full. Please try again later.";
    this.player.getProxyPlayer().disconnect(serializer.deserialize(msg));
  }

  public boolean handleEscapeCommand(String cmd) {
    Config.EscapeCommands escapeConfig = CONFIG.escapeCommands;
    if (escapeConfig == null || !escapeConfig.enabled || escapeConfig.commands == null) {
      return false;
    }

    boolean matches = false;
    for (String esc : escapeConfig.commands) {
      String lowerEsc = esc.toLowerCase(Locale.ROOT);
      if (cmd.equalsIgnoreCase(lowerEsc) || cmd.startsWith(lowerEsc + " ")) {
        matches = true;
        break;
      }
    }

    if (!matches) {
      return false;
    }

    this.leaveQueueToFallback();
    return true;
  }

  public void leaveQueueToFallback() {
    this.connected = false;
    this.cleanVisuals();
    this.plugin.getQueueManager().dequeue(this.server, this);

    String fallback = this.resolveFallbackServer();
    ComponentSerializer<Component, Component, String> serializer = LimboReconnect.getSerializer();
    if (fallback != null && !fallback.isEmpty()) {
      Optional<RegisteredServer> targetFallback = this.plugin.getServer().getServer(fallback);
      if (targetFallback.isPresent()) {
        if (CONFIG.escapeCommands.leftQueueMessage != null && !CONFIG.escapeCommands.leftQueueMessage.isEmpty()) {
          this.player.getProxyPlayer().sendMessage(serializer.deserialize(CONFIG.escapeCommands.leftQueueMessage));
        }
        this.player.disconnect(targetFallback.get());
        return;
      }
    }

    if (CONFIG.escapeCommands.noFallbackMessage != null && !CONFIG.escapeCommands.noFallbackMessage.isEmpty()) {
      this.player.getProxyPlayer().sendMessage(serializer.deserialize(CONFIG.escapeCommands.noFallbackMessage));
    }
  }

  public LimboPlayer getPlayer() {
    return this.player;
  }

  public RegisteredServer getServer() {
    return this.server;
  }

  public boolean isConnected() {
    return this.connected;
  }

  public int getPriority() {
    return this.priority;
  }

  public long getEntryTime() {
    return this.entryTime;
  }

  public String getGroupName() {
    return this.groupName;
  }

  public void connect() {
    if (!this.connected) {
      return;
    }

    this.connecting = true;
    this.cleanVisuals();
    this.titleIndex = -1;
    this.tickTitleAndActionBar();

    long queueDelay = Math.max(0, CONFIG.queueDelay);
    if (queueDelay <= 0) {
      this.connectSound.setPosition(this.currentX, this.currentY, this.currentZ);
      this.player.writePacket(this.connectSound);
      this.player.getProxyPlayer().resetTitle();
      this.player.disconnect(this.server);
      return;
    }

    this.player.getScheduledExecutor().schedule(() -> {
      if (this.connected) {
        this.connectSound.setPosition(this.currentX, this.currentY, this.currentZ);
        this.player.writePacket(this.connectSound);
        this.player.getProxyPlayer().resetTitle();
        this.player.disconnect(this.server);
      }
    }, queueDelay, TimeUnit.MILLISECONDS);
  }

  private void computePriorityAndGroup() {
    Config.PriorityGroups pg = CONFIG.priorityGroups;
    if (pg == null || !pg.enabled || pg.groups == null || pg.groups.isEmpty()) {
      this.priority = 0;
      this.groupName = "default";
      return;
    }

    int highestPriority = Integer.MIN_VALUE;
    String bestGroup = "default";

    for (Map.Entry<String, Config.PriorityGroups.Group> entry : pg.groups.entrySet()) {
      Config.PriorityGroups.Group group = entry.getValue();
      if (group == null) {
        continue;
      }
      boolean hasPerm = "none".equalsIgnoreCase(group.permission)
          || this.player.getProxyPlayer().hasPermission(group.permission);
      if (hasPerm && group.priority > highestPriority) {
        highestPriority = group.priority;
        bestGroup = entry.getKey();
      }
    }

    if (highestPriority != Integer.MIN_VALUE) {
      this.priority = highestPriority;
      this.groupName = bestGroup;
    } else {
      this.priority = 0;
      this.groupName = "default";
    }
  }

  private void tickMessages() {
    if (!this.connected) {
      return;
    }

    if (this.checkTimeout()) {
      return;
    }

    this.tickTitleAndActionBar();
    this.tickChat();
    this.waitSound.setPosition(this.currentX, this.currentY, this.currentZ);
    this.player.writePacket(this.waitSound);
    this.player.getScheduledExecutor().schedule(this::tickMessages, CONFIG.messages.titles.showDelay * 50, TimeUnit.MILLISECONDS);
  }

  private boolean checkOfflineTimeout() {
    ServerQueue.ServerStatus status = this.plugin.getQueueManager().getServerStatus(this.server);
    if (status == ServerQueue.ServerStatus.OFFLINE && !this.isRestarting()) {
      if (this.offlineSince == -1) {
        this.offlineSince = System.currentTimeMillis();
      }
      long timeout = this.resolveOfflineKickTimeout();
      if (timeout > 0) {
        long offlineSeconds = (System.currentTimeMillis() - this.offlineSince) / 1000;
        if (offlineSeconds >= timeout) {
          this.connected = false;
          this.cleanVisuals();
          this.plugin.getQueueManager().dequeue(this.server, this);

          String fallback = this.resolveFallbackServer();
          if (fallback != null && !fallback.isEmpty()) {
            Optional<RegisteredServer> targetFallback = this.plugin.getServer().getServer(fallback);
            if (targetFallback.isPresent()) {
              this.player.disconnect(targetFallback.get());
              return true;
            }
          }

          ComponentSerializer<Component, Component, String> serializer = LimboReconnect.getSerializer();
          String kickMsg = this.replacePlaceholders(CONFIG.messages.offlineKick, 0, 0, 0,
              this.server.getServerInfo().getName(), 0, 0, "Offline");
          this.player.getProxyPlayer().disconnect(serializer.deserialize(kickMsg));
          return true;
        }
      }
    } else {
      this.offlineSince = -1;
    }
    return false;
  }

  private boolean checkTimeout() {
    if (this.checkOfflineTimeout()) {
      return true;
    }

    long maxWait = this.resolveMaxWaitTime();
    if (maxWait <= 0) {
      return false;
    }

    long elapsedSeconds = (System.currentTimeMillis() - this.spawnTime) / 1000;
    if (elapsedSeconds < maxWait) {
      return false;
    }

    this.connected = false;
    this.cleanVisuals();
    this.plugin.getQueueManager().dequeue(this.server, this);

    String fallback = this.resolveFallbackServer();
    if (fallback != null && !fallback.isEmpty()) {
      Optional<RegisteredServer> targetFallback = this.plugin.getServer().getServer(fallback);
      if (targetFallback.isPresent()) {
        this.player.disconnect(targetFallback.get());
        return true;
      }
    }

    ComponentSerializer<Component, Component, String> serializer = LimboReconnect.getSerializer();
    String timeoutMsg = this.replacePlaceholders(CONFIG.messages.timeoutDisconnect, 0, 0, 0,
        this.server.getServerInfo().getName(), 0, 0, "Offline");
    this.player.getProxyPlayer().disconnect(serializer.deserialize(timeoutMsg));
    return true;
  }

  private String resolveFallbackServer() {
    String serverName = this.server.getServerInfo().getName();
    if (CONFIG.servers != null && CONFIG.servers.containsKey(serverName)) {
      Config.ServerOverride override = CONFIG.servers.get(serverName);
      if (override != null && override.fallbackServer != null && !override.fallbackServer.isEmpty()) {
        return override.fallbackServer;
      }
    }
    return CONFIG.fallbackServer;
  }

  private long resolveMaxWaitTime() {
    String serverName = this.server.getServerInfo().getName();
    if (CONFIG.servers != null && CONFIG.servers.containsKey(serverName)) {
      Config.ServerOverride override = CONFIG.servers.get(serverName);
      if (override != null && override.maxWaitTime >= 0) {
        return override.maxWaitTime;
      }
    }
    return CONFIG.maxWaitTime;
  }

  private long resolveOfflineKickTimeout() {
    String serverName = this.server.getServerInfo().getName();
    if (CONFIG.servers != null && CONFIG.servers.containsKey(serverName)) {
      Config.ServerOverride override = CONFIG.servers.get(serverName);
      if (override != null && override.offlineKickTimeout >= 0) {
        return override.offlineKickTimeout;
      }
    }
    return CONFIG.offlineKickTimeout;
  }

  private boolean isRestarting() {
    ServerQueue queue = this.plugin.getQueueManager().getQueue(this.server);
    if (queue != null) {
      return queue.isRestarting();
    }
    long elapsed = (System.currentTimeMillis() - this.spawnTime) / 1000;
    return elapsed < CONFIG.restartingTimeout;
  }

  private void tickTitleAndActionBar() {
    if (!this.connected || this.player == null) {
      return;
    }

    int position = this.plugin.getQueueManager().getPosition(this.server, this);
    int total = this.plugin.getQueueManager().getTotal(this.server);
    int ahead = Math.max(0, position - 1);
    String serverName = this.server.getServerInfo().getName();
    String eta = this.plugin.getQueueManager().getEstimatedWaitTime(this.server, position);

    ServerQueue.ServerStatus status = this.plugin.getQueueManager().getServerStatus(this.server);
    int online = this.plugin.getQueueManager().getOnlinePlayers(this.server);
    int max = this.plugin.getQueueManager().getMaxPlayers(this.server);
    String statusStr = this.resolveStatusString(status);

    ComponentSerializer<Component, Component, String> serializer = LimboReconnect.getSerializer();

    List<Config.Messages.Title> titleList;
    if (this.connecting) {
      titleList = CONFIG.messages.titles.connectingTitles;
    } else if (status == ServerQueue.ServerStatus.FULL
        && CONFIG.messages.titles.fullTitles != null && !CONFIG.messages.titles.fullTitles.isEmpty()) {
      titleList = CONFIG.messages.titles.fullTitles;
    } else if (status == ServerQueue.ServerStatus.OFFLINE && !this.isRestarting()
        && CONFIG.messages.titles.offlineTitles != null && !CONFIG.messages.titles.offlineTitles.isEmpty()) {
      titleList = CONFIG.messages.titles.offlineTitles;
    } else {
      titleList = CONFIG.messages.titles.titles;
    }

    if (titleList != null && !titleList.isEmpty()) {
      this.titleIndex = (this.titleIndex + 1) % titleList.size();
      Config.Messages.Title confTitle = titleList.get(this.titleIndex);

      String rawTitle = confTitle.title != null ? confTitle.title : "";
      String rawSubtitle = confTitle.subtitle != null ? confTitle.subtitle : "";

      String titleStr = this.replacePlaceholders(rawTitle, position, total, ahead, serverName, online, max, statusStr, eta);
      String subtitleStr = this.replacePlaceholders(rawSubtitle, position, total, ahead, serverName, online, max, statusStr, eta);

      Title title = Title.title(
          serializer.deserialize(titleStr),
          serializer.deserialize(subtitleStr),
          Title.Times.times(
              Duration.ZERO,
              Duration.ofMillis(1000 * 30),
              Duration.ZERO
          )
      );
      this.player.getProxyPlayer().showTitle(title);
    }

    Config.Messages.ActionBar actionBarConfig = CONFIG.messages.actionBar;
    if (actionBarConfig != null && actionBarConfig.enabled) {
      String rawBar;
      if (this.connecting) {
        rawBar = actionBarConfig.connecting;
      } else if (status == ServerQueue.ServerStatus.FULL
          && actionBarConfig.fullWaiting != null && !actionBarConfig.fullWaiting.isEmpty()) {
        rawBar = actionBarConfig.fullWaiting;
      } else {
        rawBar = actionBarConfig.waiting;
      }

      if (rawBar != null && !rawBar.isEmpty()) {
        String barStr = this.replacePlaceholders(rawBar, position, total, ahead, serverName, online, max, statusStr, eta);
        this.player.getProxyPlayer().sendActionBar(serializer.deserialize(barStr));
      }
    }

    Config.Messages.BossBar bossBarConfig = CONFIG.messages.bossBar;
    if (bossBarConfig != null && bossBarConfig.enabled && !this.connecting) {
      float progress = (total > 0) ? Math.max(0f, Math.min(1f, 1f - ((float) ahead / (float) Math.max(1, total)))) : 0f;
      BossBar.Color color;
      try {
        color = BossBar.Color.valueOf(bossBarConfig.color.toUpperCase(Locale.ROOT));
      } catch (Exception e) {
        color = BossBar.Color.YELLOW;
      }
      BossBar.Overlay overlay;
      try {
        overlay = BossBar.Overlay.valueOf(bossBarConfig.overlay.toUpperCase(Locale.ROOT));
      } catch (Exception e) {
        overlay = BossBar.Overlay.PROGRESS;
      }
      String rawBossTitle = bossBarConfig.title != null ? bossBarConfig.title : "";
      Component bossTitleComp = serializer.deserialize(
          this.replacePlaceholders(rawBossTitle, position, total, ahead, serverName, online, max, statusStr, eta)
      );

      if (this.bossBar == null) {
        this.bossBar = BossBar.bossBar(bossTitleComp, progress, color, overlay);
        this.player.getProxyPlayer().showBossBar(this.bossBar);
      } else {
        this.bossBar.name(bossTitleComp);
        this.bossBar.progress(progress);
        this.bossBar.color(color);
        this.bossBar.overlay(overlay);
      }
    }

    Config.Messages.TabList tabListConfig = CONFIG.messages.tabList;
    if (tabListConfig != null && tabListConfig.enabled) {
      String rawHeader = tabListConfig.header != null ? tabListConfig.header : "";
      String rawFooter = tabListConfig.footer != null ? tabListConfig.footer : "";
      Component headerComp = serializer.deserialize(
          this.replacePlaceholders(rawHeader, position, total, ahead, serverName, online, max, statusStr, eta)
      );
      Component footerComp = serializer.deserialize(
          this.replacePlaceholders(rawFooter, position, total, ahead, serverName, online, max, statusStr, eta)
      );
      this.player.getProxyPlayer().sendPlayerListHeaderAndFooter(headerComp, footerComp);
    }
  }

  private void tickChat() {
    Config.Messages.Chat chatConfig = CONFIG.messages.chat;
    if (chatConfig == null || !chatConfig.enabled || this.connecting) {
      return;
    }

    int position = this.plugin.getQueueManager().getPosition(this.server, this);
    int total = this.plugin.getQueueManager().getTotal(this.server);
    int ahead = Math.max(0, position - 1);
    String serverName = this.server.getServerInfo().getName();
    String eta = this.plugin.getQueueManager().getEstimatedWaitTime(this.server, position);

    ServerQueue.ServerStatus status = this.plugin.getQueueManager().getServerStatus(this.server);
    int online = this.plugin.getQueueManager().getOnlinePlayers(this.server);
    int max = this.plugin.getQueueManager().getMaxPlayers(this.server);
    String statusStr = this.resolveStatusString(status);

    long now = System.currentTimeMillis();
    boolean positionChanged = this.lastChatPosition != position;
    long requiredInterval = (status == ServerQueue.ServerStatus.OFFLINE && !this.isRestarting())
        ? Math.max(chatConfig.interval * 1000, LimboReconnect.CONFIG.heartbeatInterval)
        : chatConfig.interval * 1000;
    boolean intervalElapsed = chatConfig.interval > 0 && (now - this.lastChatTime >= requiredInterval);

    if (positionChanged || intervalElapsed) {
      this.lastChatPosition = position;
      this.lastChatTime = now;

      String rawMessage;
      if (this.plugin.getQueueManager().getQueue(this.server).isPaused()
          && CONFIG.messages.pausedQueue != null && !CONFIG.messages.pausedQueue.isEmpty()) {
        rawMessage = CONFIG.messages.pausedQueue;
      } else if (status == ServerQueue.ServerStatus.FULL && chatConfig.full != null && !chatConfig.full.isEmpty()) {
        rawMessage = chatConfig.full;
      } else if (status == ServerQueue.ServerStatus.OFFLINE && this.isRestarting()
          && chatConfig.restarting != null && !chatConfig.restarting.isEmpty()) {
        rawMessage = chatConfig.restarting;
      } else if (status == ServerQueue.ServerStatus.OFFLINE && chatConfig.offline != null && !chatConfig.offline.isEmpty()) {
        rawMessage = chatConfig.offline;
      } else {
        rawMessage = chatConfig.message;
      }

      if (rawMessage != null && !rawMessage.isEmpty()) {
        ComponentSerializer<Component, Component, String> serializer = LimboReconnect.getSerializer();
        String msgStr = this.replacePlaceholders(rawMessage, position, total, ahead, serverName, online, max, statusStr, eta);
        this.player.getProxyPlayer().sendMessage(serializer.deserialize(msgStr));
      }
    }
  }

  private String resolveStatusString(ServerQueue.ServerStatus status) {
    Config.Messages.StatusNames names = CONFIG.messages.statusNames;
    if (status == ServerQueue.ServerStatus.OFFLINE) {
      if (this.isRestarting()) {
        return names != null && names.restarting != null ? names.restarting : "Restarting";
      }
      return names != null && names.offline != null ? names.offline : "Offline";
    } else if (status == ServerQueue.ServerStatus.FULL) {
      return names != null && names.full != null ? names.full : "Full";
    } else {
      return names != null && names.online != null ? names.online : "Online";
    }
  }

  private String replacePlaceholders(String text, int position, int total, int ahead, String serverName,
      int online, int max, String statusStr, String eta) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    String pingStr = (this.player != null) ? String.valueOf(this.player.getPing()) : "0";
    return text.replace("%position%", String.valueOf(position > 0 ? position : 1))
        .replace("%total%", String.valueOf(total > 0 ? total : 1))
        .replace("%ahead%", String.valueOf(ahead))
        .replace("%server%", serverName)
        .replace("%online%", String.valueOf(online))
        .replace("%max%", String.valueOf(max))
        .replace("%status%", statusStr)
        .replace("%group%", this.groupName)
        .replace("%priority%", String.valueOf(this.priority))
        .replace("%eta%", eta)
        .replace("%ping%", pingStr);
  }

  private String replacePlaceholders(String text, int position, int total, int ahead, String serverName,
      int online, int max, String statusStr) {
    String eta = this.plugin.getQueueManager().getEstimatedWaitTime(this.server, position);
    return this.replacePlaceholders(text, position, total, ahead, serverName, online, max, statusStr, eta);
  }
}
