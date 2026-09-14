package net.labymod.addons.minimap.worldmap;

import net.labymod.addons.minimap.MinimapAddon;
import net.labymod.addons.minimap.laby3d.MinimapUniformBlocks;
import net.labymod.addons.minimap.map.v2.MinimapRenderer;
import net.labymod.addons.minimap.world.WorldMapService;
import net.labymod.api.Laby;
import net.labymod.api.client.Minecraft;
import net.labymod.api.client.gui.screen.key.Key;
import net.labymod.api.configuration.loader.property.ConfigProperty;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.input.KeyEvent;
import net.labymod.api.event.client.input.KeyEvent.State;

public class WorldMapOpener {

  private static final long SUPPRESS_NANOS = 250_000_000L;

  private final MinimapAddon addon;
  private final ConfigProperty<Key> openKey;
  private final ConfigProperty<Boolean> atlasOpen;
  private final ConfigProperty<Boolean> chunkGrid;
  private final WorldMapService service;
  private final MinimapRenderer minimapRenderer;
  private final MinimapUniformBlocks uniformBlocks;
  private long suppressedUntil;

  public WorldMapOpener(
      MinimapAddon addon,
      ConfigProperty<Key> openKey,
      ConfigProperty<Boolean> atlasOpen,
      ConfigProperty<Boolean> chunkGrid,
      WorldMapService service,
      MinimapRenderer minimapRenderer,
      MinimapUniformBlocks uniformBlocks
  ) {
    this.addon = addon;
    this.openKey = openKey;
    this.atlasOpen = atlasOpen;
    this.chunkGrid = chunkGrid;
    this.service = service;
    this.minimapRenderer = minimapRenderer;
    this.uniformBlocks = uniformBlocks;
  }

  @Subscribe
  public void onKey(KeyEvent event) {
    Key key = this.openKey.get();
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
        this.atlasOpen,
        this.chunkGrid,
        this.minimapRenderer,
        this.uniformBlocks
    ));
  }

  Key openKey() {
    return this.openKey.get();
  }

  /**
   * The key that closes the map must not open it again right away.
   */
  void suppressOpen() {
    this.suppressedUntil = System.nanoTime() + SUPPRESS_NANOS;
  }
}
