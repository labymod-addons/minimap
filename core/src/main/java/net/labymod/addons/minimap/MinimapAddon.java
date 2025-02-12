package net.labymod.addons.minimap;

import javax.inject.Singleton;
import net.labymod.addons.minimap.activity.MapActivity;
import net.labymod.addons.minimap.api.MinimapConfigProvider;
import net.labymod.addons.minimap.api.MinimapHudWidgetConfig;
import net.labymod.addons.minimap.api.generated.ReferenceStorage;
import net.labymod.addons.minimap.api.util.Util;
import net.labymod.addons.minimap.config.MinimapConfiguration;
import net.labymod.addons.minimap.debug.ImGuiMinimapDebug;
import net.labymod.addons.minimap.hudwidget.MinimapHudWidget;
import net.labymod.addons.minimap.integration.waypoints.WaypointsIntegration;
import net.labymod.addons.minimap.map.v2.MinimapRenderer;
import net.labymod.addons.minimap.map.v2.listener.MinimapListener;
import net.labymod.addons.minimap.server.MinimapServers;
import net.labymod.api.Laby;
import net.labymod.api.addon.LabyAddon;
import net.labymod.api.client.gui.screen.key.HotkeyService.Type;
import net.labymod.api.client.gui.screen.key.Key;
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
    MinimapAddon.references = this.referenceStorageAccessor();

    var references = Laby.references();
    this.minimapRenderer = new MinimapRenderer(this::configuration);
    references.hudWidgetRegistry().register(this.hudWidget = new MinimapHudWidget(this, this.minimapRenderer));

    this.servers.init();

    references.addonIntegrationService()
        .registerIntegration("labyswaypoints", WaypointsIntegration.class);

    MinimapConfigProvider configProvider = getReferences().minimapConfigProvider();
    this.registerListener(new MinimapListener(configProvider));
    this.registerListener(getReferences().tileRendererDispatcher());

    references.hotkeyService()
        .register(
            Util.NAMESPACE + "-open-full-map",
            () -> Key.U,
            () -> Type.TOGGLE, pressed -> {
              Laby.labyAPI().minecraft().minecraftWindow().displayScreen(new MapActivity(this.minimapRenderer));
            }
        );

    references.controlEntryRegistry().registerEntry(false, ImGuiMinimapDebug::new);
  }

  @Override
  protected Class<? extends MinimapConfiguration> configurationClass() {
    return MinimapConfiguration.class;
  }

  public boolean isMinimapAllowed() {
    return this.servers.isCurrentlyAllowed();
  }

  @Override
  public MinimapHudWidgetConfig widgetConfig() {
    return this.hudWidget.getConfig();
  }

  public static ReferenceStorage getReferences() {
    return MinimapAddon.references;
  }
}
