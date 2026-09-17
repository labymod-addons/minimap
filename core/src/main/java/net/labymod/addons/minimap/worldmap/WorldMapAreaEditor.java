package net.labymod.addons.minimap.worldmap;

import java.util.ArrayList;
import java.util.List;
import net.labymod.addons.minimap.data.ChunkData;
import net.labymod.addons.minimap.world.MapArea;
import net.labymod.addons.minimap.world.MapArea.Shape;
import net.labymod.addons.minimap.world.MapAreaStore;
import net.labymod.api.client.gfx.pipeline.renderer.text.TextRenderingOptions;
import net.labymod.api.client.gui.screen.key.KeyHandler;
import net.labymod.api.client.gui.screen.state.ScreenCanvas;
import net.labymod.api.client.render.font.FontSize.PredefinedFontSize;
import net.labymod.api.util.Color;
import net.labymod.api.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Draws marked areas on the world map and edits them. After picking a shape, drag or click twice
 * to draw the area. Clicking an area selects it. Drag its corner or edge handles to resize it, drag
 * its inside to move it, and double click it to rename it. Hold shift to snap edges to the nearest
 * chunk line.
 */
final class WorldMapAreaEditor {

  private static final int FILL_ALPHA = 0x30;
  private static final int ACTIVE_FILL_ALPHA = 0x50;
  private static final int OUTLINE_ALPHA = 0xE0;
  private static final int HANDLE_COLOR = 0xFFFFFFFF;
  private static final int HANDLE_OUTLINE_COLOR = 0xFF000000;
  private static final int NAME_COLOR = 0xFFFFFFFF;
  private static final float NAME_SCALE = 0.75F;
  private static final float HANDLE_SIZE = 5.0F;
  private static final float HANDLE_HIT_RADIUS = 5.0F;
  private static final float SCREEN_MARGIN = 4.0F;
  private static final int NO_HANDLE = -1;
  private static final float BLOCK_CIRCLE_MIN_SCALE = 1.0F;
  private static final int CIRCLE_HANDLE = 5;
  private static final int HANDLE_COUNT = 8;
  private static final long DOUBLE_CLICK_NANOS = 400_000_000L;
  private static final float RENAME_PADDING = 3.0F;
  private static final float RENAME_MIN_WIDTH = 24.0F;
  private static final long CARET_BLINK_MILLIS = 500L;

  private static final int[] PALETTE = {
      0x4FC3F7, 0xFFB74D, 0x81C784, 0xE57373, 0xBA68C8, 0xFFF176, 0x4DB6AC, 0xF06292
  };

  @Nullable
  private MapAreaStore store;
  @Nullable
  private MapArea hovered;
  private int hoveredHandle = NO_HANDLE;
  @Nullable
  private String selectedId;
  @Nullable
  private String lastClickedId;
  private long lastClickNanos;

  @Nullable
  private String renamingId;
  private final StringBuilder renameText = new StringBuilder();
  private boolean renameSelected;

  @Nullable
  private Shape placingShape;
  private String placingName;
  @Nullable
  private MapArea drawing;
  private double anchorX;
  private double anchorZ;

  @Nullable
  private MapArea operated;
  private int operatedHandle = NO_HANDLE;
  private int startBlockX;
  private int startBlockZ;
  private int startMinX;
  private int startMinZ;
  private int startMaxX;
  private int startMaxZ;

  static int paletteColor(int index) {
    return PALETTE[Math.floorMod(index, PALETTE.length)];
  }

  static List<Color> paletteColors() {
    List<Color> colors = new ArrayList<>(PALETTE.length);
    for (int color : PALETTE) {
      colors.add(Color.of(color, 255));
    }

    return colors;
  }

  /**
   * Waits for a press on the map to start drawing the area.
   */
  void startPlacing(MapAreaStore store, Shape shape, String name) {
    this.cancel();
    this.placingShape = shape;
    this.placingName = name;
    this.store = store;
  }

  /**
   * @return whether a shape was picked. Drawing may not have started yet.
   */
  boolean isPlacing() {
    return this.placingShape != null;
  }

  boolean isDrawing() {
    return this.drawing != null;
  }

  void startDrawing(double worldX, double worldZ) {
    if (this.placingShape == null || this.store == null) {
      return;
    }

    this.anchorX = worldX;
    this.anchorZ = worldZ;
    int blockX = MathHelper.floor(worldX);
    int blockZ = MathHelper.floor(worldZ);
    this.drawing = new MapArea(
        this.placingShape,
        this.placingName,
        paletteColor(this.store.areas().size()),
        blockX, blockZ,
        blockX + 1, blockZ + 1
    );
    this.updateDrawing(worldX, worldZ);
  }

  /**
   * Adds the drawn area and selects it.
   */
  @Nullable
  MapArea finishDrawing(double worldX, double worldZ) {
    MapArea area = this.drawing;
    if (area == null || this.store == null) {
      return null;
    }

    this.updateDrawing(worldX, worldZ);
    this.drawing = null;
    this.placingShape = null;
    this.store.add(area);
    this.selectedId = area.id();
    return area;
  }

  /**
   * Stops placing and resets an area that is being moved or resized.
   */
  void cancel() {
    this.placingShape = null;
    this.drawing = null;
    if (this.operated != null) {
      this.operated.setBounds(this.startMinX, this.startMinZ, this.startMaxX, this.startMaxZ);
      this.operated = null;
    }
  }

  @Nullable
  MapArea hovered() {
    return this.hovered;
  }

  @Nullable
  MapArea selected() {
    return this.store == null || this.selectedId == null ? null : this.store.get(this.selectedId);
  }

  void select(@Nullable MapArea area) {
    this.selectedId = area == null ? null : area.id();
  }

  boolean isOperating() {
    return this.operated != null;
  }

  /**
   * @return the area being drawn, moved or resized
   */
  @Nullable
  MapArea editing() {
    return this.drawing != null ? this.drawing : this.operated;
  }

  /**
   * Starts resizing when a handle of the selected area is under the cursor, or moving when the
   * cursor is inside it.
   *
   * @return whether an operation started
   */
  boolean press(double worldX, double worldZ) {
    MapArea selected = this.selected();
    if (selected == null || this.placingShape != null) {
      return false;
    }

    if (this.hoveredHandle == NO_HANDLE && this.hovered != selected) {
      return false;
    }

    this.operated = selected;
    this.operatedHandle = this.hoveredHandle;
    this.startBlockX = MathHelper.floor(worldX);
    this.startBlockZ = MathHelper.floor(worldZ);
    this.startMinX = selected.minX();
    this.startMinZ = selected.minZ();
    this.startMaxX = selected.maxX();
    this.startMaxZ = selected.maxZ();
    return true;
  }

  void drag(double worldX, double worldZ) {
    if (this.drawing != null) {
      this.updateDrawing(worldX, worldZ);
      return;
    }

    MapArea area = this.operated;
    if (area == null) {
      return;
    }

    boolean snap = KeyHandler.isShiftDown();
    if (this.operatedHandle == NO_HANDLE) {
      int offsetX = MathHelper.floor(worldX) - this.startBlockX;
      int offsetZ = MathHelper.floor(worldZ) - this.startBlockZ;
      if (snap) {
        offsetX += snapOffset(this.startMinX + offsetX, this.startMaxX + offsetX);
        offsetZ += snapOffset(this.startMinZ + offsetZ, this.startMaxZ + offsetZ);
      }

      area.setBounds(
          this.startMinX + offsetX, this.startMinZ + offsetZ,
          this.startMaxX + offsetX, this.startMaxZ + offsetZ
      );
      return;
    }

    if (area.shape() == Shape.CIRCLE) {
      setCircle(
          area,
          (this.startMinX + this.startMaxX) / 2.0D,
          (this.startMinZ + this.startMaxZ) / 2.0D,
          worldX, worldZ,
          snap
      );
      return;
    }

    // The edges across from the dragged handle stay in place
    int sideX = handleSideX(this.operatedHandle);
    int sideZ = handleSideZ(this.operatedHandle);
    int fixedX = sideX < 0 ? this.startMaxX : this.startMinX;
    int fixedZ = sideZ < 0 ? this.startMaxZ : this.startMinZ;
    area.setBounds(
        sideX == 0 ? this.startMinX : fixedX,
        sideZ == 0 ? this.startMinZ : fixedZ,
        sideX == 0 ? this.startMaxX : movingEdge(fixedX, worldX, snap),
        sideZ == 0 ? this.startMaxZ : movingEdge(fixedZ, worldZ, snap)
    );
  }

  /**
   * @return whether an operation ended
   */
  boolean release() {
    if (this.operated == null) {
      return false;
    }

    MapArea area = this.operated;
    this.operated = null;
    boolean changed = area.minX() != this.startMinX
        || area.minZ() != this.startMinZ
        || area.maxX() != this.startMaxX
        || area.maxZ() != this.startMaxZ;
    if (changed && this.store != null) {
      this.store.save();
    }

    return true;
  }

  /**
   * Selects the area at the clicked point, or clears the selection when there is none. A second
   * click on the same area starts renaming it.
   */
  void click(double worldX, double worldZ) {
    // render() stops tracking hover while the button is held, so look the area up again
    if (this.hoveredHandle != NO_HANDLE || this.store == null) {
      return;
    }

    MapArea area = areaAt(this.store, worldX, worldZ);
    long now = System.nanoTime();
    boolean doubleClick = area != null
        && area.id().equals(this.lastClickedId)
        && now - this.lastClickNanos <= DOUBLE_CLICK_NANOS;
    this.select(area);
    this.lastClickedId = doubleClick || area == null ? null : area.id();
    this.lastClickNanos = now;
    if (doubleClick) {
      this.startRenaming(area);
    }
  }

  /**
   * Starts with the whole name selected, so typing replaces it.
   */
  void startRenaming(MapArea area) {
    this.renamingId = area.id();
    this.renameText.setLength(0);
    this.renameText.append(area.name());
    this.renameSelected = true;
  }

  boolean isRenaming() {
    return this.renaming() != null;
  }

  void type(char character) {
    if (Character.isISOControl(character)) {
      return;
    }

    if (this.renameSelected) {
      this.renameText.setLength(0);
      this.renameSelected = false;
    }

    if (this.renameText.length() < WorldMapAreaPopup.MAX_NAME_LENGTH) {
      this.renameText.append(character);
    }
  }

  void erase() {
    if (this.renameSelected) {
      this.renameText.setLength(0);
      this.renameSelected = false;
    } else if (this.renameText.length() > 0) {
      this.renameText.setLength(this.renameText.length() - 1);
    }
  }

  void finishRenaming() {
    MapArea area = this.renaming();
    this.renamingId = null;
    if (area == null || this.store == null) {
      return;
    }

    String name = this.renameText.toString().trim();
    if (!name.equals(area.name())) {
      area.setName(name);
      this.store.save();
    }
  }

  void cancelRenaming() {
    this.renamingId = null;
  }

  @Nullable
  private MapArea renaming() {
    return this.store == null || this.renamingId == null ? null : this.store.get(this.renamingId);
  }

  void render(
      ScreenCanvas canvas,
      WorldMapCamera camera,
      MapAreaStore store,
      float width, float height,
      float mouseX, float mouseY,
      boolean hoverable
  ) {
    if (this.store != store) {
      this.cancel();
      this.selectedId = null;
      this.renamingId = null;
      this.store = store;
    }

    double mouseWorldX = camera.screenToWorldX(mouseX, width);
    double mouseWorldZ = camera.screenToWorldZ(mouseY, height);
    if (this.drawing != null) {
      this.updateDrawing(mouseWorldX, mouseWorldZ);
    }

    MapArea selected = this.selected();
    if (this.operated == null) {
      this.hovered = null;
      this.hoveredHandle = hoverable && selected != null && selected.isVisible()
          ? this.handleAt(camera, selected, width, height, mouseX, mouseY)
          : NO_HANDLE;
      if (hoverable && this.placingShape == null) {
        this.hovered = areaAt(store, mouseWorldX, mouseWorldZ);
      }
    }

    boolean layered = false;
    for (MapArea area : store.areas()) {
      if (!area.isVisible() || !this.isOnScreen(camera, area, width, height)) {
        continue;
      }

      if (!layered) {
        // Circles and rectangles of the terrain share layers, a new one keeps areas above it
        canvas.nextLayer();
        layered = true;
      }

      boolean active = area == selected || area == this.hovered;
      this.renderArea(canvas, camera, area, width, height, active);
    }

    if (this.drawing != null) {
      canvas.nextLayer();
      this.renderArea(canvas, camera, this.drawing, width, height, true);
    }

    if (selected != null && selected.isVisible()) {
      this.renderHandles(canvas, camera, selected, width, height);
    }

    MapArea renaming = this.renaming();
    if (renaming != null && renaming.isVisible()) {
      canvas.nextLayer();
      this.renderRenameField(
          canvas,
          camera.worldToScreenX(renaming.centerX(), width),
          camera.worldToScreenY(renaming.centerZ(), height)
      );
    }
  }

  private void renderRenameField(ScreenCanvas canvas, float centerX, float centerY) {
    WorldMapTheme theme = WorldMapTheme.get();
    float nameScale = theme.textScale(NAME_SCALE, PredefinedFontSize.MEDIUM);
    String text = this.renameText.toString();
    float textWidth = canvas.getTextWidth(text) * nameScale;
    float lineHeight = canvas.getLineHeight() * nameScale;
    float fieldWidth = Math.max(textWidth, RENAME_MIN_WIDTH) + RENAME_PADDING * 2.0F;
    theme.field(
        canvas,
        centerX - fieldWidth / 2.0F, centerY - lineHeight / 2.0F - RENAME_PADDING,
        fieldWidth, lineHeight + RENAME_PADDING * 2.0F
    );

    float textX = centerX - textWidth / 2.0F;
    float textY = centerY - lineHeight / 2.0F;
    if (this.renameSelected && textWidth > 0.0F) {
      canvas.submitRelativeRect(textX, textY - 1.0F, textWidth, lineHeight + 1.0F, theme.selectionColor());
    }

    canvas.submitText(
        text,
        centerX, textY,
        theme.textColor(),
        nameScale,
        theme.textOptions() | TextRenderingOptions.CENTERED
    );
    if (!this.renameSelected && System.currentTimeMillis() / CARET_BLINK_MILLIS % 2L == 0L) {
      canvas.submitRelativeRect(textX + textWidth, textY - 1.0F, 1.0F, lineHeight + 1.0F, theme.textColor());
    }
  }

  /**
   * @return the topmost visible area at the point
   */
  @Nullable
  private static MapArea areaAt(MapAreaStore store, double worldX, double worldZ) {
    MapArea found = null;
    for (MapArea area : store.areas()) {
      if (area.isVisible() && area.contains(worldX, worldZ)) {
        found = area;
      }
    }

    return found;
  }

  private void updateDrawing(double worldX, double worldZ) {
    MapArea area = this.drawing;
    boolean snap = KeyHandler.isShiftDown();
    if (area.shape() == Shape.CIRCLE) {
      // Snapped circles center on a chunk corner, others on the middle of a block
      double centerX = snap ? roundToChunk(this.anchorX) : MathHelper.floor(this.anchorX) + 0.5D;
      double centerZ = snap ? roundToChunk(this.anchorZ) : MathHelper.floor(this.anchorZ) + 0.5D;
      setCircle(area, centerX, centerZ, worldX, worldZ, snap);
      return;
    }

    int fixedX = anchorEdge(this.anchorX, worldX, snap);
    int fixedZ = anchorEdge(this.anchorZ, worldZ, snap);
    area.setBounds(
        fixedX, fixedZ,
        movingEdge(fixedX, worldX, snap), movingEdge(fixedZ, worldZ, snap)
    );
  }

  /**
   * @return the anchor block's edge on the side away from the cursor. When snapping, the chunk line
   * nearest to the anchor.
   */
  private static int anchorEdge(double anchor, double world, boolean snap) {
    if (snap) {
      return roundToChunk(anchor);
    }

    int block = MathHelper.floor(anchor);
    return MathHelper.floor(world) >= block ? block : block + 1;
  }

  /**
   * @return the edge that includes the block under the cursor. When snapping, the chunk line nearest
   * to the cursor. Never returns the fixed edge.
   */
  private static int movingEdge(int fixedEdge, double world, boolean snap) {
    if (!snap) {
      int block = MathHelper.floor(world);
      return block >= fixedEdge ? block + 1 : block;
    }

    int edge = roundToChunk(world);
    if (edge != fixedEdge) {
      return edge;
    }

    return world < fixedEdge ? fixedEdge - ChunkData.CHUNK_SIZE : fixedEdge + ChunkData.CHUNK_SIZE;
  }

  /**
   * @return the shift that puts the closer of both edges onto a chunk line
   */
  private static int snapOffset(int min, int max) {
    int toMin = roundToChunk(min) - min;
    int toMax = roundToChunk(max) - max;
    return Math.abs(toMin) <= Math.abs(toMax) ? toMin : toMax;
  }

  private static int roundToChunk(double value) {
    return MathHelper.floor(value / ChunkData.CHUNK_SIZE + 0.5D) * ChunkData.CHUNK_SIZE;
  }

  /**
   * Grows the radius to the cursor in whole blocks, or whole chunks when snapping. The center stays.
   * A center on the middle of a block gives an odd diameter.
   */
  private static void setCircle(
      MapArea area,
      double centerX, double centerZ,
      double worldX, double worldZ,
      boolean snap
  ) {
    double deltaX = worldX - centerX;
    double deltaZ = worldZ - centerZ;
    double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
    double fraction = centerX - Math.floor(centerX);
    int step = snap ? ChunkData.CHUNK_SIZE : 1;
    long steps = Math.round((distance - fraction) / step);
    long minSteps = snap || fraction == 0.0D ? 1 : 0;
    double radius = Math.max(steps, minSteps) * step + fraction;
    area.setBounds(
        MathHelper.floor(centerX - radius), MathHelper.floor(centerZ - radius),
        MathHelper.floor(centerX + radius), MathHelper.floor(centerZ + radius)
    );
  }

  private void renderArea(
      ScreenCanvas canvas,
      WorldMapCamera camera,
      MapArea area,
      float width, float height,
      boolean active
  ) {
    float left = camera.worldToScreenX(area.minX(), width);
    float top = camera.worldToScreenY(area.minZ(), height);
    float right = camera.worldToScreenX(area.maxX(), width);
    float bottom = camera.worldToScreenY(area.maxZ(), height);
    int fill = (active ? ACTIVE_FILL_ALPHA : FILL_ALPHA) << 24 | area.color();
    int outline = OUTLINE_ALPHA << 24 | area.color();
    float thickness = active ? 2.0F : 1.0F;
    if (area.shape() == Shape.CIRCLE && camera.scale() < BLOCK_CIRCLE_MIN_SCALE) {
      // A block is smaller than a pixel here, so the steps would not show
      float centerX = (left + right) / 2.0F;
      float centerY = (top + bottom) / 2.0F;
      float radius = (right - left) / 2.0F;
      canvas.submitCircle(centerX, centerY, radius, fill);
      canvas.submitCircle(centerX, centerY, Math.max(0.0F, radius - thickness), radius, outline);
    } else if (area.shape() == Shape.CIRCLE) {
      this.renderBlockCircle(canvas, camera, area, width, height, fill, outline, thickness);
    } else {
      // Far zoomed in areas reach way past the screen, clamping keeps float precision
      float clampedLeft = Math.max(left, -SCREEN_MARGIN);
      float clampedTop = Math.max(top, -SCREEN_MARGIN);
      float clampedRight = Math.min(right, width + SCREEN_MARGIN);
      float clampedBottom = Math.min(bottom, height + SCREEN_MARGIN);
      canvas.submitAbsoluteRect(clampedLeft, clampedTop, clampedRight, clampedBottom, fill);
      canvas.submitAbsoluteRect(clampedLeft, top, clampedRight, top + thickness, outline);
      canvas.submitAbsoluteRect(clampedLeft, bottom - thickness, clampedRight, bottom, outline);
      canvas.submitAbsoluteRect(left, clampedTop, left + thickness, clampedBottom, outline);
      canvas.submitAbsoluteRect(right - thickness, clampedTop, right, clampedBottom, outline);
    }

    float nameScale = WorldMapTheme.get().textScale(NAME_SCALE, PredefinedFontSize.MEDIUM);
    float nameWidth = canvas.getTextWidth(area.name()) * nameScale;
    boolean renaming = area.id().equals(this.renamingId);
    if (!renaming && !area.name().isEmpty() && nameWidth + SCREEN_MARGIN * 2.0F < right - left) {
      canvas.submitText(
          area.name(),
          (left + right) / 2.0F,
          (top + bottom) / 2.0F - canvas.getLineHeight() * nameScale / 2.0F,
          NAME_COLOR,
          nameScale,
          TextRenderingOptions.SHADOW | TextRenderingOptions.CENTERED
      );
    }
  }

  /**
   * Fills each run of equal-width rows with one rectangle. The outline sits inside the circle.
   */
  private void renderBlockCircle(
      ScreenCanvas canvas,
      WorldMapCamera camera,
      MapArea area,
      float width, float height,
      int fill, int outline,
      float thickness
  ) {
    int firstZ = Math.max(area.minZ(), MathHelper.floor(camera.screenToWorldZ(-SCREEN_MARGIN, height)));
    int endZ = Math.min(area.maxZ(), MathHelper.floor(camera.screenToWorldZ(height + SCREEN_MARGIN, height)) + 1);
    if (firstZ >= endZ) {
      return;
    }

    float middle = camera.worldToScreenX(area.centerX(), width);
    int previousInset = area.circleInset(firstZ - 1);
    int runStart = firstZ;
    int runInset = area.circleInset(firstZ);
    for (int z = firstZ + 1; z <= endZ; z++) {
      int inset = area.circleInset(z);
      if (z < endZ && inset == runInset) {
        continue;
      }

      float left = camera.worldToScreenX(area.minX() + runInset, width);
      float right = camera.worldToScreenX(area.maxX() - runInset, width);
      float top = camera.worldToScreenY(runStart, height);
      float bottom = camera.worldToScreenY(z, height);
      canvas.submitAbsoluteRect(Math.max(left, -SCREEN_MARGIN), top, Math.min(right, width + SCREEN_MARGIN), bottom, fill);
      canvas.submitAbsoluteRect(left, top, left + thickness, bottom, outline);
      canvas.submitAbsoluteRect(right - thickness, top, right, bottom, outline);
      this.renderStep(canvas, camera, area, width, middle, top, previousInset, runInset, thickness, outline);

      previousInset = runInset;
      runStart = z;
      runInset = inset;
    }

    float bottom = camera.worldToScreenY(endZ, height);
    this.renderStep(canvas, camera, area, width, middle, bottom, previousInset, runInset, thickness, outline);
  }

  /**
   * Draws the horizontal outline where two rows differ in width. The line sits inside the wider row.
   */
  private void renderStep(
      ScreenCanvas canvas,
      WorldMapCamera camera,
      MapArea area,
      float width,
      float middle,
      float y,
      int aboveInset, int belowInset,
      float thickness,
      int outline
  ) {
    if (aboveInset == belowInset) {
      return;
    }

    int outer = Math.min(aboveInset, belowInset);
    int inner = Math.max(aboveInset, belowInset);
    float left = Math.max(camera.worldToScreenX(area.minX() + outer, width), -SCREEN_MARGIN);
    // Extend under the narrower row's side lines to close the inner corners
    float leftEnd = Math.min(camera.worldToScreenX(area.minX() + inner, width) + thickness, middle);
    float rightStart = Math.max(camera.worldToScreenX(area.maxX() - inner, width) - thickness, middle);
    float right = Math.min(camera.worldToScreenX(area.maxX() - outer, width), width + SCREEN_MARGIN);
    float stepTop = belowInset < aboveInset ? y : y - thickness;
    canvas.submitAbsoluteRect(left, stepTop, leftEnd, stepTop + thickness, outline);
    canvas.submitAbsoluteRect(rightStart, stepTop, right, stepTop + thickness, outline);
  }

  private void renderHandles(
      ScreenCanvas canvas,
      WorldMapCamera camera,
      MapArea area,
      float width, float height
  ) {
    for (int handle = 0; handle < HANDLE_COUNT; handle++) {
      if (!hasHandle(area, handle)) {
        continue;
      }

      float x = this.handleX(camera, area, handle, width);
      float y = this.handleY(camera, area, handle, height);
      float half = HANDLE_SIZE / 2.0F;
      canvas.submitRelativeRect(x - half - 1.0F, y - half - 1.0F, HANDLE_SIZE + 2.0F, HANDLE_SIZE + 2.0F, HANDLE_OUTLINE_COLOR);
      canvas.submitRelativeRect(x - half, y - half, HANDLE_SIZE, HANDLE_SIZE, HANDLE_COLOR);
    }
  }

  private int handleAt(
      WorldMapCamera camera,
      MapArea area,
      float width, float height,
      float mouseX, float mouseY
  ) {
    // Check corners first so they win over edge handles on small areas
    for (int handle = 0; handle < HANDLE_COUNT; handle++) {
      if (!hasHandle(area, handle)) {
        continue;
      }

      float deltaX = mouseX - this.handleX(camera, area, handle, width);
      float deltaY = mouseY - this.handleY(camera, area, handle, height);
      if (deltaX * deltaX + deltaY * deltaY <= HANDLE_HIT_RADIUS * HANDLE_HIT_RADIUS) {
        return handle;
      }
    }

    return NO_HANDLE;
  }

  /**
   * Rectangles have handles 0 to 3 on their corners clockwise from the top left, and 4 to 7 on their
   * edges clockwise from the top. A circle only has the one on its right edge.
   */
  private static boolean hasHandle(MapArea area, int handle) {
    return area.shape() == Shape.RECTANGLE || handle == CIRCLE_HANDLE;
  }

  /**
   * @return -1 when the handle sits on the minimum X edge, 1 on the maximum, 0 in between
   */
  private static int handleSideX(int handle) {
    if (handle == 0 || handle == 3 || handle == 7) {
      return -1;
    }

    return handle == 1 || handle == 2 || handle == 5 ? 1 : 0;
  }

  private static int handleSideZ(int handle) {
    if (handle == 0 || handle == 1 || handle == 4) {
      return -1;
    }

    return handle == 2 || handle == 3 || handle == 6 ? 1 : 0;
  }

  private float handleX(WorldMapCamera camera, MapArea area, int handle, float width) {
    int side = handleSideX(handle);
    double x = side == 0 ? area.centerX() : side < 0 ? area.minX() : area.maxX();
    return camera.worldToScreenX(x, width);
  }

  private float handleY(WorldMapCamera camera, MapArea area, int handle, float height) {
    int side = handleSideZ(handle);
    double z = side == 0 ? area.centerZ() : side < 0 ? area.minZ() : area.maxZ();
    return camera.worldToScreenY(z, height);
  }

  private boolean isOnScreen(WorldMapCamera camera, MapArea area, float width, float height) {
    return camera.worldToScreenX(area.maxX(), width) >= -SCREEN_MARGIN
        && camera.worldToScreenX(area.minX(), width) <= width + SCREEN_MARGIN
        && camera.worldToScreenY(area.maxZ(), height) >= -SCREEN_MARGIN
        && camera.worldToScreenY(area.minZ(), height) <= height + SCREEN_MARGIN;
  }
}
