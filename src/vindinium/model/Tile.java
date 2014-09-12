package vindinium.model;

public class Tile {

  public final int i, j;
  public final Type type;
  public final Hero hero;

  public Tile(int i, int j, Type type, Hero hero) {
    this.i = i;
    this.j = j;
    this.type = type;
    this.hero = hero;
  }

  public boolean isWalkable() {
    if (type == Type.EMPTY || type == Type.HERO) {
      return true;
    }
    return false;
  }

  public static enum Type {
    EMPTY, WOOD, HERO, TAVERN, GOLD;

    @Override
    public String toString() {
      if (this == EMPTY) {
        return " ";
      } else if (this == WOOD) {
        return "#";
      } else if (this == HERO) {
        return "@";
      } else if (this == TAVERN) {
        return "T";
      } else if (this == GOLD) {
        return "$";
      } else {
        throw new IllegalStateException();
      }
    };
  }

  @Override
  public String toString() {
    return type + " (" + i + "," + j + ")";
  }

}
