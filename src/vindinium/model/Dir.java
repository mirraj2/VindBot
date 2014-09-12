package vindinium.model;

public enum Dir {
  STAY(0, 0), NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

  public final int vx, vy;

  private Dir(int vx, int vy) {
    this.vx = vx;
    this.vy = vy;
  }

  @Override
  public String toString() {
    String s = name();
    return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
  };
}
