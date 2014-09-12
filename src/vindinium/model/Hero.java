package vindinium.model;

import jasonlib.Json;

public class Hero {

  public final int id;
  public final int x, y, spawnX, spawnY;
  public final int elo, life, gold, mineCount;
  public final String name, userId;
  public final boolean crashed;

  public Tile tile, spawn;

  public Hero(Json json) {
    id = json.getInt("id");
    x = json.getJson("pos").getInt("y");
    y = json.getJson("pos").getInt("x");
    spawnX = json.getJson("spawnPos").getInt("y");
    spawnY = json.getJson("spawnPos").getInt("x");
    if (json.has("elo")) {
      elo = json.getInt("elo");
    } else {
      elo = 1200;
    }
    life = json.getInt("life");
    gold = json.getInt("gold");
    mineCount = json.getInt("mineCount");
    name = json.get("name");
    if (json.has("userId")) {
      userId = json.get("userId");
    } else{
      userId = "none";
    }
    crashed = json.getBoolean("crashed");
  }

  @Override
  public String toString() {
    return name;
  }

}
