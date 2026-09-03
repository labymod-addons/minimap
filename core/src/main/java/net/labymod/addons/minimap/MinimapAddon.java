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
import net.labymod.addons.minimap.server.MinimapServers;
import net.labymod.addons.minimap.stream.MinimapCommands;
import net.labymod.addons.minimap.stream.MinimapPublisher;
import net.labymod.api.Laby;
import net.labymod.api.addon.LabyAddon;
import net.labymod.api.models.Implements;
import net.labymod.api.models.addon.annotation.AddonMain;
import net.labymod.laby3d.api.util.Util;

@AddonMain
@Singleton
@Implements(MinimapConfigProvider.class)
public class MinimapAddon extends LabyAddon<MinimapConfiguration> implements MinimapConfigProvider {

  private final MinimapServers servers = new MinimapServers();
  private static ReferenceStorage references;

  private MinimapRenderer minimapRenderer;
  private MinimapHudWidget hudWidget;
  private boolean externalDevices;

  @Override
  protected void enable() {
    Util.NATIVE_BOUNDS_CHECK_ENABLED = true;
    this.registerSettingCategory();
    MinimapAddon.references = this.referenceStorageAccessor();

    MinimapContext minimapContext = new MinimapContext();
    this.registerListener(minimapContext.storage());
    this.registerListener(minimapContext.uniformBlocks());

    var references = Laby.references();
    this.minimapRenderer = new MinimapRenderer(this, minimapContext);
    references.hudWidgetRegistry().register(this.hudWidget = new MinimapHudWidget(this, this.minimapRenderer));

    this.servers.init();

    references.addonIntegrationService()
        .registerIntegration("labyswaypoints", WaypointsIntegration.class);

    this.registerListener(getReferences().tileRendererDispatcher());

    // External Devices: publish minimap tiles + live state whenever a phone is connected, and let
    // a paired controller set waypoints. Transport and pairing live in the core
    // ExternalDeviceService (ingame.phoneHud setting). Guarded so the addon still works on client
    // builds that don't ship the External Devices API yet; a LinkageError means an older one that
    // has the service but not its control side.
    try {
      Class.forName("net.labymod.api.externaldevice.ExternalDeviceControl");
      this.registerListener(new MinimapPublisher(this, minimapContext));
      MinimapCommands.register();
      this.externalDevices = true;
    } catch (ClassNotFoundException | LinkageError ignored) {
      this.logger().info("External Devices API not available in this client build, minimap publishing disabled");
    }

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

  /**
   * Switched off in the mods menu the publisher simply stops receiving ticks, but the waypoint
   * operation is pulled by the controller, so it has to be taken off the list itself.
   */
  @Override
  protected void onDeactivated() {
    if (this.externalDevices) {
      MinimapCommands.unregister();
    }
  }

  @Override
  protected void onActivated() {
    if (this.externalDevices) {
      MinimapCommands.register();
    }
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
