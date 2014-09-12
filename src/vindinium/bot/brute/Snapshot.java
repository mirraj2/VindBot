package vindinium.bot.brute;

import java.util.List;
import java.util.Map;
import vindinium.model.Dir;
import vindinium.model.GameState;
import vindinium.model.Hero;
import vindinium.model.Tile;
import vindinium.model.Tile.Type;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class Snapshot {

  private static final Dir[] dirs = Dir.values();
  private static final boolean[][] possibleDirs = new boolean[4][5];
  private static int mapSize;

  private final char[] map;
  private final RHero[] heroes;
  private final RHero me;
  private final boolean[] validHeroes;

  public double computeScore() {
    double ret = 0;
    for (RHero hero : heroes) {
      if (hero == me) {
        ret += hero.gold * 4;
        ret += hero.life;
        ret += hero.mines.size() * 1000;
      } else {
        ret -= hero.gold / 3;
        ret -= hero.life / 3;
        ret -= hero.mines.size() * 30;
      }
    }

    int closestMine = 100000;
    int closestTavern = 100000;
    for (int i = 0; i < map.length; i++) {
      if (map[i] == '$') {
        int x = i % mapSize;
        int y = i / mapSize;
        int dist = Math.abs(me.x - x) + Math.abs(me.y - y);
        if (!me.mines.contains(i)) {
          closestMine = Math.min(closestMine, dist);
        }
      } else if (map[i] == 'T') {
        int x = i % mapSize;
        int y = i / mapSize;
        int dist = Math.abs(me.x - x) + Math.abs(me.y - y);
        closestTavern = Math.min(closestTavern, dist);
      }
    }

    if (me.life > 60) {
      // give points for getting closer to other mines
      if (closestMine != 100000) {
        ret -= closestMine * 2;
      }
    } else {
      // give points for heading to a tavern
      ret -= closestTavern * 5;
    }

    return ret;
  }

  // this returns up to 125 snapshots
  public List<Snapshot> mutate(Dir myDir) {
    if (!canMove(me, myDir)) {
      return ImmutableList.of();
    }

    List<Snapshot> ret = Lists.newArrayListWithCapacity(125);

    for (int i = 0; i < heroes.length; i++) {
      final RHero hero = heroes[i];
      for (int j = 0; j < dirs.length; j++) {
        possibleDirs[i][j] = canMove(hero, dirs[j]);
      }
    }

    mutate(0, new Dir[4], myDir, ret);

    return ret;
  }

  private void mutate(int index, Dir[] heroDirs, Dir myDir, List<Snapshot> buffer) {
    if (index == heroDirs.length) {
      Snapshot snapshot = mutate(heroDirs);
      if (snapshot != null) {
        buffer.add(snapshot);
      }
      return;
    }

    if (!validHeroes[index]) {
      mutate(index + 1, heroDirs, myDir, buffer);
      return;
    }

    if (heroes[index] == me) {
      heroDirs[index] = myDir;
      mutate(index + 1, heroDirs, myDir, buffer);
    } else {
      for (int i = 0; i < dirs.length; i++) {
        if (possibleDirs[index][i]) {
          heroDirs[index] = dirs[i];
          mutate(index + 1, heroDirs, myDir, buffer);
        }
      }
    }
  }

  private Snapshot mutate(Dir[] heroDirs) {
    RHero[] heroCopies = new RHero[heroes.length];
    RHero meCopy = null;
    for (int i = 0; i < heroes.length; i++) {
      heroCopies[i] = heroes[i].copy();
      if (heroes[i] == me) {
        meCopy = heroCopies[i];
      }
    }

    for (int i = 0; i < heroes.length; i++) {
      Dir dir = heroDirs[i];
      if (dir == null) {
        continue;
      }

      RHero hero = heroes[i];
      RHero copy = heroCopies[i];

      int toX = hero.x + dir.vx;
      int toY = hero.y + dir.vy;

      for (RHero h : heroCopies) {
        if (copy != h && h.x == toX && h.y == toY) {
          continue; // can't move into another hero
        }
      }

      char tile = getTile(toX, toY);
      if (tile == ' ') {
        copy.x = toX;
        copy.y = toY;
      } else if (tile == '$') {
        int mineId = toX + toY * mapSize;
        if (copy.mines.contains(mineId)) {
          // prune case where someone tries to take mine they already own
          return null;
        } else {
          if (copy.life <= 20) {
            // prune case where someone suicides
            return null;
          } else {
            copy.life -= 20;
            copy.mines.add(mineId);
          }
        }
      } else if (tile == 'T') {
        if (copy.life == 100) {
          return null;
        }
        copy.life += 50;
        copy.gold -= 2;
      } else if (tile == '#' || tile == '\0') {
        throw new IllegalStateException();
      }
    }

    // resolve fights
    for (int i = 0; i < 4; i++) {
      for (int j = 0; j < 4; j++) {
        if (i == j) {
          continue;
        }
        RHero attacker = heroCopies[i];
        RHero defender = heroCopies[j];
        if (adjacent(attacker, defender)) {
          defender.life -= 20;
          if (defender.life <= 0) {
            respawn(defender);
            attacker.mines.addAll(defender.mines);
            defender.mines.clear();
          }
        }
      }
    }

    for (RHero hero : heroCopies) {
      hero.gold += hero.mines.size();
      if (hero.life > 1) {
        hero.life--; // thirst
      }
    }

    return new Snapshot(map, heroCopies, meCopy, validHeroes);
  }

  private void respawn(RHero hero) {
    hero.life = 100;
    hero.x = hero.hero.spawnX;
    hero.y = hero.hero.spawnY;
  }

  private boolean adjacent(RHero a, RHero b) {
    int diff = Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
    return diff == 1;
  }

  public boolean canMove(Dir dir) {
    return canMove(me, dir);
  }

  private boolean canMove(RHero hero, Dir dir) {
    char tile = getTile(hero.x + dir.vx, hero.y + dir.vy);
    return tile != '#' && tile != '\0';
  }

  private char getTile(int x, int y) {
    if (x < 0 || y < 0 || x >= mapSize || y >= mapSize) {
      return '\0';
    }
    int index = x + y * mapSize;
    return map[index];
  }


  private Snapshot(char[] map, RHero[] heroes, RHero me, boolean[] validHeroes) {
    this.map = map;
    this.heroes = heroes;
    this.me = me;
    this.validHeroes = validHeroes;
  }

  public Snapshot(GameState state) {
    Tile[][] tiles = state.board;
    mapSize = tiles.length;
    map = new char[tiles.length * tiles[0].length];
    Map<Hero, RHero> heroMap = Maps.newHashMap();
    List<RHero> heroes = Lists.newArrayList();
    for (Hero hero : state.heroes.values()) {
      RHero rhero = new RHero(hero);
      heroes.add(rhero);
      heroMap.put(hero, rhero);
    }
    me = heroMap.get(state.me);
    this.heroes = heroes.toArray(new RHero[heroes.size()]);

    int c = 0;
    for (int j = 0; j < tiles[0].length; j++) {
      for (int i = 0; i < tiles.length; i++) {
        Tile t = tiles[i][j];
        Type type = t.type;
        if (type == Type.EMPTY || type == Type.HERO) {
          map[c] = ' ';
        } else if (type == Type.WOOD) {
          map[c] = '#';
        } else if (type == Type.GOLD) {
          map[c] = '$';
          if (t.hero != null) {
            heroMap.get(t.hero).mines.add(i + j * mapSize);
          }
        } else if (type == Type.TAVERN) {
          map[c] = 'T';
        } else {
          throw new IllegalStateException("Don't know: " + type);
        }
        c++;
      }
    }

    validHeroes = new boolean[4];
    for (int i = 0; i < 4; i++) {
      validHeroes[i] = this.heroes[i] == me;
      // int dist = Math.abs(this.heroes[i].x - me.x) + Math.abs(this.heroes[i].y - me.y);
      // validHeroes[i] = dist < 3;
    }
  }

}
