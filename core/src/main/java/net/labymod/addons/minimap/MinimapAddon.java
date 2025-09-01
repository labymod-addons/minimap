package net.labymod.addons.minimap;

import javax.inject.Singleton;
import net.labymod.addons.minimap.api.config.MinimapConfig;
import net.labymod.addons.minimap.api.config.MinimapConfigProvider;
import net.labymod.addons.minimap.api.config.MinimapHudWidgetConfig;
import net.labymod.addons.minimap.api.generated.ReferenceStorage;
import net.labymod.addons.minimap.config.MinimapConfiguration;
import net.labymod.addons.minimap.data.ChunkDataStorage;
import net.labymod.addons.minimap.debug.ImGuiMinimapDebug;
import net.labymod.addons.minimap.hudwidget.MinimapHudWidget;
import net.labymod.addons.minimap.integration.waypoints.WaypointsIntegration;
import net.labymod.addons.minimap.map.v2.MinimapRenderer;
import net.labymod.addons.minimap.server.MinimapServers;
import net.labymod.api.Laby;
import net.labymod.api.addon.LabyAddon;
import net.labymod.api.models.Implements;
import net.labymod.api.models.addon.annotation.AddonMain;

@AddonMain
@Singleton
@Implements(MinimapConfigProvider.class)
public class MinimapAddon extends LabyAddon<MinimapConfiguration> implements MinimapConfigProvider {

  private final MinimapServers servers = new MinimapServers();
  private static ReferenceStorage references;

  private MinimapRenderer minimapRenderer;
  private MinimapHudWidget hudWidget;

  @Override
  protected void enable() {
    this.registerSettingCategory();
    MinimapAddon.references = this.referenceStorageAccessor();

    ChunkDataStorage storage = new ChunkDataStorage();
    this.registerListener(storage);

    var references = Laby.references();
    this.minimapRenderer = new MinimapRenderer(this, "hud", storage);
    references.hudWidgetRegistry().register(this.hudWidget = new MinimapHudWidget(this, this.minimapRenderer));

    this.servers.init();

    references.addonIntegrationService()
        .registerIntegration("labyswaypoints", WaypointsIntegration.class);

    this.registerListener(getReferences().tileRendererDispatcher());

    /*
    references.hotkeyService()
        .register(
            Util.NAMESPACE + "-open-full-map",
            () -> Key.U,
            () -> Type.TOGGLE, pressed -> {
              Laby.labyAPI().minecraft().minecraftWindow().displayScreen(new MapActivity(new MinimapRenderer(this::configuration, "activity", storage)));
            }
        );*/

    references.controlEntryRegistry().registerEntry(false, ImGuiMinimapDebug::new);
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

  @Override
  public MinimapConfig config() {
    return this.configuration();
  }

  @Override
  public MinimapHudWidgetConfig hudWidgetConfig() {
    return this.hudWidget.getConfig();
  }
}
