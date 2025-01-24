package net.labymod.addons.minimap;

import net.labymod.addons.minimap.api.MinimapConfigProvider;
import net.labymod.addons.minimap.api.MinimapHudWidgetConfig;
import net.labymod.addons.minimap.api.generated.ReferenceStorage;
import net.labymod.addons.minimap.config.MinimapConfiguration;
import net.labymod.addons.minimap.debug.ImGuiMinimapDebug;
import net.labymod.addons.minimap.hudwidget.MinimapHudWidget;
import net.labymod.addons.minimap.integration.waypoints.WaypointsIntegration;
import net.labymod.addons.minimap.map.v2.listener.MinimapListener;
import net.labymod.addons.minimap.server.MinimapServers;
import net.labymod.api.Laby;
import net.labymod.api.addon.LabyAddon;
import net.labymod.api.models.Implements;
import net.labymod.api.models.addon.annotation.AddonMain;
import javax.inject.Singleton;

@AddonMain
@Singleton
@Implements(MinimapConfigProvider.class)
public class MinimapAddon extends LabyAddon<MinimapConfiguration> implements MinimapConfigProvider {

  private final MinimapServers servers = new MinimapServers();
  private static ReferenceStorage references;

  private MinimapHudWidget hudWidget;

  @Override
  protected void enable() {
    MinimapAddon.references = this.referenceStorageAccessor();

    Laby.references().hudWidgetRegistry().register(this.hudWidget = new MinimapHudWidget(this));

    this.servers.init();

    Laby.references().addonIntegrationService()
        .registerIntegration("labyswaypoints", WaypointsIntegration.class);

    MinimapConfigProvider configProvider = getReferences().minimapConfigProvider();
    this.registerListener(new MinimapListener(configProvider));
    this.registerListener(getReferences().tileRendererDispatcher());

    Laby.references().controlEntryRegistry().registerEntry(false, ImGuiMinimapDebug::new);
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
