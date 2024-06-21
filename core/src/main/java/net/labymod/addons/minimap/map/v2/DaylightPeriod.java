package net.labymod.addons.minimap.map.v2;

public final class DaylightPeriod {

  public static final DaylightPeriod DAYTIME = new DaylightPeriod("Daytime", 0, 12000);
  public static final DaylightPeriod SUNSET = new DaylightPeriod("Sunset", 12000, 13000);
  public static final DaylightPeriod NIGHTTIME = new DaylightPeriod("Nighttime", 13000, 23000);
  public static final DaylightPeriod SUNRISE = new DaylightPeriod("Sunrise", 23000, 0);

  private final String name;
  private final int start;
  private final int end;

  private DaylightPeriod(String name, int start, int end) {
    this.name = name;
    this.start = start;
    this.end = end;
  }

  public static DaylightPeriod findByTime(long time) {
    if (time >= 0 && time <= 12000) {
      return DAYTIME;
    } else if (time >= 12000 && time <= 13000) {
      return SUNSET;
    } else if (time >= 13000 && time <= 23000) {
      return NIGHTTIME;
    } else {
      return SUNRISE;
    }
  }

  public String getName() {
    return this.name;
  }

  public int getStart() {
    return this.start;
  }

  public int getEnd() {
    return this.end;
  }

  @Override
  public String toString() {
    return "DaylightPeriod[name=" + this.getName() + ", start=" + this.getStart() + ", end=" + this.getEnd() + "]";
  }
}
