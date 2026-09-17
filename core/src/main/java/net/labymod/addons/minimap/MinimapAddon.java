package net.labymod.addons.minimap;

import javax.inject.Singleton;
import net.labymod.addons.minimap.api.config.MinimapConfig;
import net.labymod.addons.minimap.api.config.MinimapConfigProvider;
import net.labymod.addons.minimap.api.config.MinimapHudWidgetConfig;
import net.labymod.addons.minimap.api.generated.ReferenceStorage;
import net.labymod.addons.minimap.config.MinimapConfiguration;
import net.labymod.addons.minimap.debug.ImGuiMinimapDebug;
import net.labymod.addons.minimap.hudwidget.MinimapHudWidget;
import net.labymod.addons.minimap.integration.waypoints.WaypointsIntegration;
import net.labymod.addons.minimap.map.v2.MinimapRenderer;
import net.labymod.addons.minimap.map.v2.renderer.AreaMinimapRenderer;
import net.labymod.addons.minimap.server.MinimapServers;
import net.labymod.addons.minimap.stream.MinimapPublisher;
import net.labymod.addons.minimap.world.WorldMapService;
import net.labymod.addons.minimap.worldmap.WorldMapOpener;
import net.labymod.api.Laby;
import net.labymod.api.addon.LabyAddon;
import net.labymod.api.client.Minecraft;
import net.labymod.api.models.Implements;
import net.labymod.api.models.addon.annotation.AddonMain;
import net.labymod.laby3d.api.util.Util;

@AddonMain
@Singleton
@Implements(MinimapConfigProvider.class)
public class MinimapAddon extends LabyAddon<MinimapConfiguration> implements MinimapConfigProvider {

  private final MinimapServers servers = new MinimapServers();
  private static ReferenceStorage references;
  private static WorldMapService worldMap;

  private MinimapContext minimapContext;
  private MinimapRenderer minimapRenderer;
  private MinimapHudWidget hudWidget;
  private WorldMapService worldMapService;

  @Override
  protected void enable() {
    Util.NATIVE_BOUNDS_CHECK_ENABLED = true;
    this.registerSettingCategory();
    MinimapAddon.references = this.referenceStorageAccessor();

    this.minimapContext = new MinimapContext();
    this.registerListener(this.minimapContext.storage());
    this.registerListener(this.minimapContext.uniformBlocks());

    var references = Laby.references();
    this.minimapRenderer = new MinimapRenderer(this, this.minimapContext);
    this.registerListener(this.minimapRenderer);
    references.hudWidgetRegistry().register(this.hudWidget = new MinimapHudWidget(this, this.minimapRenderer));

    this.worldMapService = new WorldMapService(this);
    MinimapAddon.worldMap = this.worldMapService;
    this.registerListener(this.worldMapService);
    this.registerListener(new AreaMinimapRenderer(this, this.worldMapService));

    this.servers.init();

    references.addonIntegrationService()
        .registerIntegration("labyswaypoints", WaypointsIntegration.class);

    this.registerListener(getReferences().tileRendererDispatcher());
    this.registerListener(new MinimapPublisher(
        this,
        this.minimapContext,
        this.minimapRenderer,
        this.hudWidget,
        this.worldMapService
    ));
    this.registerListener(new WorldMapOpener(
        this,
        this.configuration().worldMapKey(),
        this.configuration().worldMapAtlasOpen(),
        this.configuration().worldMapChunkGrid(),
        this.worldMapService,
        this.minimapRenderer,
        this.minimapContext.uniformBlocks()
    ));

    references.controlEntryRegistry().registerEntry(false, ImGuiMinimapDebug::new);
  }

  @Override
  protected void onActivated() {
    // Every listener of this addon was muted while it was disabled, so the collected world state is
    // stale.
    this.servers.refreshAllowedState();
    this.worldMapService.close();

    Minecraft minecraft = this.labyAPI().minecraft();
    if (minecraft.isIngame()) {
      this.minimapContext.reload(minecraft.clientWorld());
    }
  }

  @Override
  protected void onDeactivated() {
    if (this.minimapContext == null) {
      return;
    }

    this.minimapContext.storage().clearAll();
    this.worldMapService.close();
  }

  @Override
  protected Class<? extends MinimapConfiguration> configurationClass() {
    return MinimapConfiguration.class;
  }

  public boolean isMinimapAllowed() {
    return this.servers.isCurrentlyAllowed();
  }

  public static ReferenceStorage getReferences() {
    return MinimapAddon.references;
  }

  public static WorldMapService worldMap() {
    return MinimapAddon.worldMap;
  }

  @Override
  public MinimapConfig config() {
    return this.configuration();
  }

  @Override
  public MinimapHudWidgetConfig hudWidgetConfig() {
    return this.hudWidget.getConfig();
  }
}
