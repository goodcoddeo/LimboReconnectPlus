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

package kr.dklight.limboreconnectplus;

import java.util.List;
import java.util.Map;
import net.elytrium.commons.kyori.serialization.Serializers;
import net.elytrium.limboapi.api.file.BuiltInWorldFileType;
import net.elytrium.limboapi.api.player.GameMode;
import net.elytrium.serializer.SerializerConfig;
import net.elytrium.serializer.annotations.Comment;
import net.elytrium.serializer.annotations.CommentValue;
import net.elytrium.serializer.language.object.YamlSerializable;

public class Config extends YamlSerializable {

  private static final SerializerConfig CONFIG = new SerializerConfig.Builder().setCommentValueIndent(1).build();


  @Comment(value = {
      @CommentValue("Available serializers:"),
      @CommentValue("LEGACY_AMPERSAND - \"&c&lExample &c&9Text\"."),
      @CommentValue("LEGACY_SECTION - \"§c§lExample §c§9Text\"."),
      @CommentValue("MINIMESSAGE - \"<bold><red>Example</red> <blue>Text</blue></bold>\". (https://webui.adventure.kyori.net/)"),
      @CommentValue("GSON - \"[{\"text\":\"Example\",\"bold\":true,\"color\":\"red\"},{\"text\":\" \",\"bold\":true},{\"text\":\"Text\",\"bold\":true,\"color\":\"blue\"}]\". (https://minecraft.tools/en/json_text.php/)"),
      @CommentValue("GSON_COLOR_DOWNSAMPLING - Same as GSON, but uses downsampling.")
  })
  public Serializers serializer = Serializers.MINIMESSAGE;

  @Comment(value = @CommentValue("Send player to the limbo, if disconnect reason contains this text (using regex)"))
  public String triggerMessage = "(?s).*";

  @Comment(value = @CommentValue("Server status check interval in milliseconds"))
  public long checkInterval = 1000;
  @Comment(value = @CommentValue("Heartbeat check interval in milliseconds when server is offline"))
  public long heartbeatInterval = 5000;
  @Comment(value = @CommentValue("Server status check timeout in milliseconds"))
  public long pingTimeout = 5000;
  @Comment(value = @CommentValue("Reconnect delay after server startup"))
  public long joinDelay = 2000;
  @Comment(value = @CommentValue("Delay between reconnecting each player (ms, 0 for instant)"))
  public long queueDelay = 2000;
  @Comment(value = @CommentValue("Number of players to reconnect per batch"))
  public int batchSize = 5;
  @Comment(value = @CommentValue("Delay between reconnect batches in milliseconds"))
  public long batchInterval = 2000;
  @Comment(value = @CommentValue("Maximum queue size (0 to disable)"))
  public int maxQueueSize = 0;
  public String maxQueueMessage = "<red>Queue is currently full. Please try again later.";
  public String bypassPermission = "limboreconnectplus.bypass";
  @Comment(value = @CommentValue("Send to limbo or use current server's world"))
  public boolean useLimbo = false;
  @Comment(value = @CommentValue("Require permission to reconnect (limboreconnectplus.reconnect)"))
  public boolean requirePermission = false;
  @Comment(value = @CommentValue("Wait for free slots before reconnecting if server is full"))
  public boolean waitForSlots = true;
  @Comment(value = @CommentValue("Timeout in seconds before assuming server is offline rather than restarting"))
  public long restartingTimeout = 30;
  @Comment(value = @CommentValue("Timeout in seconds in offline state before kicking player (0 to disable)"))
  public long offlineKickTimeout = 60;
  @Comment(value = @CommentValue("Maximum time to wait in limbo in seconds before fallback/disconnect (0 to disable)"))
  public long maxWaitTime = 0;
  @Comment(value = @CommentValue("Fallback server name if timeout is reached (empty to disconnect)"))
  public String fallbackServer = "";

  public World world;
  public Messages messages;
  public Sounds sounds;
  public PriorityGroups priorityGroups;
  public EscapeCommands escapeCommands;
  public Parkour parkour;
  public DiscordWebhook discordWebhook;
  public Map<String, ServerOverride> servers = Map.of();
  public boolean debug = false;


  public Config() {
    super(Config.CONFIG);
    this.world = new World();
    this.messages = new Messages();
    this.sounds = new Sounds();
    this.priorityGroups = new PriorityGroups();
    this.escapeCommands = new EscapeCommands();
    this.parkour = new Parkour();
    this.discordWebhook = new DiscordWebhook();
  }


  public static class World {

    @Comment(value = @CommentValue("Dimensions: OVERWORLD, NETHER, THE_END"))
    public String dimension = "OVERWORLD";
    @Comment(value = @CommentValue("Load world from file"))
    public boolean loadWorld = false;
    @Comment(value = @CommentValue("Schematic file name"))
    public String worldFilePath = "world.schem";
    public BuiltInWorldFileType worldFileType = BuiltInWorldFileType.WORLDEDIT_SCHEM;
    public int worldLightLevel = 15;
    public GameMode gamemode = GameMode.ADVENTURE;


    public WorldCoords worldCoords;
    public PlayerCoords playerCoords;
    @Comment(value = @CommentValue("Teleport back to spawn coordinates when falling below voidY"))
    public boolean voidRespawn = true;
    public double voidY = 0.0;

    public World() {
      this.worldCoords = new WorldCoords();
      this.playerCoords = new PlayerCoords();
    }

    public static class WorldCoords {

      public int x = 0;
      public int y = 100;
      public int z = 0;

      public WorldCoords() {
      }
    }

    public static class PlayerCoords {

      public int x = 0;
      public int y = 100;
      public int z = 0;
      public float pitch = 0;
      public float yaw = 90;

      public PlayerCoords() {
      }
    }
  }

  public static class Messages {

    public String reload = "<green>LimboReconnectPlus reloaded";
    public String timeoutDisconnect = "<red>Server took too long to respond. Please try again later.";
    public String offlineKick = "<red>Server is offline. You have been disconnected.";
    public String graceRestored = "<green>Your queue position has been restored (<gold>#%position%</gold>)!";
    public String pausedQueue = "<yellow>Queue is currently paused for this server.";
    public Titles titles;
    public ActionBar actionBar;
    public BossBar bossBar;
    public TabList tabList;
    public Chat chat;
    public StatusNames statusNames;
    public Eta eta;

    public Messages() {
      this.titles = new Titles();
      this.actionBar = new ActionBar();
      this.bossBar = new BossBar();
      this.tabList = new TabList();
      this.chat = new Chat();
      this.statusNames = new StatusNames();
      this.eta = new Eta();
    }

    public static class Titles {

      @Comment(value = @CommentValue("time in ticks"), at = Comment.At.SAME_LINE)
      public long showDelay = 20;

      public List<Title> titles = List.of(
          new Title("Server is restarting.", "Queue position: <gold>%position%</gold>/<yellow>%total%</yellow>"),
          new Title("Server is restarting..", "Queue position: <gold>%position%</gold>/<yellow>%total%</yellow>"),
          new Title("Server is restarting...", "Queue position: <gold>%position%</gold>/<yellow>%total%</yellow>")
      );
      public List<Title> offlineTitles = List.of(
          new Title("Server is offline.", "Queue position: <gold>%position%</gold>/<yellow>%total%</yellow>"),
          new Title("Server is offline..", "Queue position: <gold>%position%</gold>/<yellow>%total%</yellow>"),
          new Title("Server is offline...", "Queue position: <gold>%position%</gold>/<yellow>%total%</yellow>")
      );
      public List<Title> fullTitles = List.of(
          new Title("Server is full.", "Waiting for slot: <gold>%online%</gold>/<yellow>%max%</yellow> (<gold>#%position%</gold>)"),
          new Title("Server is full..", "Waiting for slot: <gold>%online%</gold>/<yellow>%max%</yellow> (<gold>#%position%</gold>)"),
          new Title("Server is full...", "Waiting for slot: <gold>%online%</gold>/<yellow>%max%</yellow> (<gold>#%position%</gold>)")
      );
      public List<Title> connectingTitles = List.of(
          new Title("Connecting.", "Connecting to the server..."),
          new Title("Connecting..", "Connecting to the server..."),
          new Title("Connecting...", "Connecting to the server...")
      );
    }

    public static class Title {

      public String title;
      public String subtitle = "Queue position: <gold>%position%</gold>/<yellow>%total%</yellow>";

      public Title() {
      }

      public Title(String title) {
        this.title = title;
      }

      public Title(String title, String subtitle) {
        this.title = title;
        this.subtitle = subtitle;
      }
    }

    public static class ActionBar {

      public boolean enabled = true;
      public String waiting = "<gray>Queue position: <gold>%position%</gold> <dark_gray>| <gray>Total: <yellow>%total%</yellow>";
      public String fullWaiting = "<red>Server is full (<gold>%online%</gold>/<yellow>%max%</yellow>) <dark_gray>| <gray>Queue: <gold>%position%</gold>";
      public String connecting = "<green>Connecting to the server...";

      public ActionBar() {
      }
    }

    public static class BossBar {

      public boolean enabled = true;
      public String title = "<gray>Queue: <gold>%position%<gray>/<gold>%total% <dark_gray>(<yellow>%eta%<dark_gray>)";
      public String color = "YELLOW";
      public String overlay = "PROGRESS";

      public BossBar() {
      }
    }

    public static class TabList {

      public boolean enabled = true;
      public String header = "<gold><bold>LimboReconnectPlus</bold></gold>\n<gray>Server: <yellow>%server%</yellow> <dark_gray>| <gray>Status: <green>%status%</green>";
      public String footer = "<gray>Queue Position: <gold>%position%</gold>/<yellow>%total%</yellow> <dark_gray>| <gray>Estimated: <yellow>%eta%</yellow>";

      public TabList() {
      }
    }

    public static class Chat {

      public boolean enabled = true;
      @Comment(value = @CommentValue("Chat message interval in seconds (0 = only on join/position change)"))
      public long interval = 5;
      public String message = "<yellow>Server currently full. <gray>Position: <gold>%position%</gold>/<yellow>%total%</yellow>";
      public String restarting = "<yellow>Server is restarting. <gray>Position: <gold>%position%</gold>/<yellow>%total%</yellow>";
      public String offline = "<red>Server is currently offline. <gray>Position: <gold>%position%</gold>/<yellow>%total%</yellow>";
      public String full = "<yellow>Server currently full (<gold>%online%</gold>/<yellow>%max%</yellow>). <gray>Position: <gold>%position%</gold>/<yellow>%total%</yellow>";

      public Chat() {
      }
    }

    public static class StatusNames {

      public String restarting = "Restarting";
      public String offline = "Offline";
      public String full = "Full";
      public String online = "Online";

      public StatusNames() {
      }
    }

    public static class Eta {

      public String offline = "Server Offline";
      public String restarting = "Restarting...";
      public String full = "Waiting for slot";
      public String instant = "< 5s";

      public Eta() {
      }
    }
  }

  public static class Sounds {

    public Sound waiting = new Sound("entity.experience_orb.pickup");
    public Sound connecting = new Sound("entity.player.levelup");

    public static class Sound {

      public String name;
      public float volume = 1.0f;
      public float pitch = 1.0f;

      public Sound() {
      }

      public Sound(String name) {
        this.name = name;
      }
    }
  }

  public static class PriorityGroups {

    public boolean enabled = false;

    public Map<String, Group> groups = Map.of(
        "admin", new Group(100, "limboreconnectplus.priority.admin", 300, 1.0),
        "vip", new Group(10, "limboreconnectplus.priority.vip", 120, 1.0),
        "default", new Group(0, "none", 30, 0.5)
    );

    public PriorityGroups() {
    }

    public static class Group {

      public int priority = 0;
      public String permission = "none";
      @Comment(value = @CommentValue("Queue retention grace period in seconds (0 to disable)"))
      public long gracePeriod = 30;
      @Comment(value = @CommentValue("Ratio of queue position retained (0.0 to 1.0)"))
      public double retentionRatio = 1.0;

      public Group() {
      }

      public Group(int priority, String permission) {
        this.priority = priority;
        this.permission = permission;
      }

      public Group(int priority, String permission, long gracePeriod, double retentionRatio) {
        this.priority = priority;
        this.permission = permission;
        this.gracePeriod = gracePeriod;
        this.retentionRatio = retentionRatio;
      }
    }
  }

  public static class EscapeCommands {

    public boolean enabled = true;
    public List<String> commands = List.of("hub", "leave", "lobby", "queue leave");
    public String leftQueueMessage = "<green>You have left the queue.";
    public String noFallbackMessage = "<red>No fallback server available to send you to.";

    public EscapeCommands() {
    }
  }


  public static class Parkour {

    public boolean enabled = false;
    public Cuboid startBox = new Cuboid(-5, 95, -5, 5, 105, 5);
    public Cuboid endBox = new Cuboid(20, 100, 20, 25, 105, 25);
    public String startMessage = "<green>Parkour challenge started! Reach the finish line.";
    public String finishMessage = "<gold><bold>Parkour finished in <yellow>%time%s</yellow>!</bold></gold>";
    public String resetMessage = "<red>Parkour timer reset.";

    public Parkour() {
    }

    public static class Cuboid {

      public double minX = 0;
      public double minY = 0;
      public double minZ = 0;
      public double maxX = 0;
      public double maxY = 0;
      public double maxZ = 0;

      public Cuboid() {
      }

      public Cuboid(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
      }

      public boolean contains(double x, double y, double z) {
        return x >= this.minX && x <= this.maxX
            && y >= this.minY && y <= this.maxY
            && z >= this.minZ && z <= this.maxZ;
      }
    }
  }

  public static class DiscordWebhook {

    public boolean enabled = false;
    public String url = "";
    public String username = "LimboReconnectPlus";
    public String avatarUrl = "";
    public String serverDownMessage = ":warning: **Server %server% is OFFLINE!** Queue size: %queue%.";
    public String serverUpMessage = ":white_check_mark: **Server %server% is ONLINE!** Reconnecting %queue% waiting players.";

    public DiscordWebhook() {
    }
  }

  public static class ServerOverride {

    public String fallbackServer = "";
    public long maxWaitTime = -1;
    public long offlineKickTimeout = -1;
    public boolean waitForSlots = true;
    public int maxQueueSize = -1;
    public int batchSize = -1;
    public long batchInterval = -1;

    public ServerOverride() {
    }
  }
}
