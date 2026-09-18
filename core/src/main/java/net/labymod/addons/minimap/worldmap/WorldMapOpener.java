package net.labymod.addons.minimap.worldmap;

import net.labymod.addons.minimap.MinimapAddon;
import net.labymod.addons.minimap.laby3d.MinimapUniformBlocks;
import net.labymod.addons.minimap.map.v2.MinimapRenderer;
import net.labymod.addons.minimap.world.WorldMapService;
import net.labymod.api.Laby;
import net.labymod.api.client.Minecraft;
import net.labymod.api.client.gui.screen.key.Key;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.input.KeyEvent;
import net.labymod.api.event.client.input.KeyEvent.State;

public class WorldMapOpener {

  private static final long SUPPRESS_NANOS = 250_000_000L;

  private final MinimapAddon addon;
  private final WorldMapService service;
  private final MinimapRenderer minimapRenderer;
  private final MinimapUniformBlocks uniformBlocks;
  private long suppressedUntil;

  public WorldMapOpener(
      MinimapAddon addon,
      WorldMapService service,
      MinimapRenderer minimapRenderer,
      MinimapUniformBlocks uniformBlocks
  ) {
    this.addon = addon;
    this.service = service;
    this.minimapRenderer = minimapRenderer;
    this.uniformBlocks = uniformBlocks;
  }

  @Subscribe
  public void onKey(KeyEvent event) {
    Key key = this.openKey();
    if (event.state() != State.PRESS || key == Key.NONE || event.key() != key) {
      return;
    }

    Minecraft minecraft = Laby.labyAPI().minecraft();
    if (System.nanoTime() < this.suppressedUntil
        || !minecraft.isIngame()
        || minecraft.minecraftWindow().isScreenOpened()
        || !this.addon.isMinimapAllowed()) {
      return;
    }

    minecraft.minecraftWindow().displayScreen(new WorldMapActivity(
        this.addon,
        this.service,
        this,
        this.addon.configuration(),
        this.addon.remotePlayers(),
        this.minimapRenderer,
        this.uniformBlocks
    ));
  }

  Key openKey() {
    return this.addon.configuration().worldMapKey().get();
  }

  /**
   * The key that closes the map must not open it again right away.
   */
  void suppressOpen() {
    this.suppressedUntil = System.nanoTime() + SUPPRESS_NANOS;
  }
}
