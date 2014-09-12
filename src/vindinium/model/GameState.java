package vindinium.model;

import jasonlib.Json;
import java.util.Collection;
import java.util.Map;
import vindinium.model.Tile.Type;
import com.google.common.collect.Maps;

public class GameState {

  public final Json json;
  public final String viewURL, playURL;
  public final int turn, maxTurns;
  public final boolean finished;
  public final Tile[][] board;
  public final Map<Integer, Hero> heroes;
  public final Hero me;

  public GameState(Json json) {
    this.json = json;
    viewURL = json.get("viewUrl");
    playURL = json.get("playUrl");

    int myHeroId = json.getJson("hero").getInt("id");

    json = json.getJson("game");
    turn = json.getInt("turn");
    maxTurns = json.getInt("maxTurns");
    finished = json.getBoolean("finished");

    heroes = parseHeroes(json.getJson("heroes"));
    board = parseBoard(json.getJson("board"));
    me = heroes.get(myHeroId);

    for (Hero hero : heroes.values()) {
      hero.tile = getTile(hero.x, hero.y);
      hero.spawn = getTile(hero.spawnX, hero.spawnY);
    }
  }

  public Tile getTile(int i, int j) {
    if (i < 0 || j < 0 || i >= board.length || j >= board[i].length) {
      return null;
    }
    return board[i][j];
  }

  private Tile[][] parseBoard(Json json) {
    int boardSize = json.getInt("size");
    String boardData = json.get("tiles");
    Tile[][] ret = new Tile[boardSize][boardSize];
    int c = 0;
    for (int j = 0; j < boardSize; j++) {
      for (int i = 0; i < boardSize; i++) {
        char a = boardData.charAt(c++);
        char b = boardData.charAt(c++);
        Tile.Type type;
        Hero hero = null;
        if (a == ' ') {
          type = Type.EMPTY;
        } else if (a == '#') {
          type = Type.WOOD;
        } else if (a == '@') {
          type = Type.HERO;
          hero = heroes.get(b - '0');
        } else if (a == '[') {
          type = Type.TAVERN;
        } else if (a == '$') {
          type = Type.GOLD;
          if (b != '-') {
            hero = heroes.get(b - '0');
          }
        } else {
          throw new IllegalStateException("Unknown tile: " + a + b);
        }
        ret[i][j] = new Tile(i, j, type, hero);
        // System.out.print(a + "" + b);
      }
      // System.out.println();
    }
    // System.out.println();
    return ret;
  }

  private final Map<Integer, Hero> parseHeroes(Json json) {
    Map<Integer, Hero> ret = Maps.newLinkedHashMap();
    for (Json heroJson : json.asJsonArray()) {
      Hero hero = new Hero(heroJson);
      ret.put(hero.id, hero);
    }
    return ret;
  }

  public Collection<Hero> getHeroes() {
    return heroes.values();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (int j = 0; j < board.length; j++) {
      for (int i = 0; i < board.length; i++) {
        Tile t = board[i][j];
        sb.append(t.type);
      }
      sb.append('\n');
    }
    return sb.toString();
  }

}
