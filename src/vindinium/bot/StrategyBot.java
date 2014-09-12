package vindinium.bot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import vindinium.model.Dir;
import vindinium.model.GameState;
import vindinium.model.Hero;
import vindinium.model.Tile;
import vindinium.model.Tile.Type;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import static com.google.common.base.Preconditions.checkNotNull;

public class StrategyBot implements AI {

  private GameState state;
  private Hero me;
  private Tile myTile;
  private List<Tile> tiles = Lists.newArrayList();

  /**
   * Marks how valuable different tiles are.
   * 
   * Tiles are lower-value if they are near a hero we don't want to fight.
   * Tiles are higher-value if they are near a mine
   */
  private Map<Tile, Double> valueTown = Maps.newHashMap();

  /*
   * Possible goals:
   * 1. Get closer to the best possible gold mining area
   * -----> best to find mines that are far from other players
   * -----> best to find mines that are owned by other players (esp the winning player)
   * 2. Stay close to my tavern
   * 3. Slay a nearby player who has low health (esp if they have a lot of mines)
   * 4. Go back to my tavern to restore health
   * 5. Run from other players (if I'm near death).
   * 6. Look for opportunities to kill myself in order to spawn on another player.
   * 7. Don't get spawned on.
   */
  // don't fight someone who is at a tavern
  // stay at tavern if nearby people and you are winning

  @Override
  @SuppressWarnings("unchecked")
  public Dir act(GameState state) {
    this.state = state;
    this.me = state.me;

    tiles.clear();
    for (int i = 0; i < state.board.length; i++) {
      for (int j = 0; j < state.board[i].length; j++) {
        tiles.add(state.board[i][j]);
      }
    }

    computeValueTown();

    myTile = state.getTile(me.x, me.y);

    Tile bestTavern = getBest(combine(type(Type.TAVERN), safeProximity));
    Tile bestGold = getBest(combine(type(Type.GOLD), safeProximity, notMe));
    Tile closestEnemy = getBest(combine(type(Type.HERO), proximity, notMe));

    int distanceToEnemy = getPath(myTile, closestEnemy).size() - 1;

    int distanceToTavern = getPath(myTile, bestTavern).size();
    if (distanceToTavern == 2 && this.me.life < 90) {
      return moveTo(bestTavern);
    }

    if (distanceToEnemy <= 4) {
      int distanceFromSpawn = getPath(closestEnemy, closestEnemy.hero.spawn).size() - 1;
      int enemyToTavern = getPath(closestEnemy, bestTavern).size() - 1;
      if (closestEnemy.hero.life <= 20 && me.life > 20 + distanceToEnemy
          && closestEnemy.hero.mineCount > 0 && distanceFromSpawn >= 3 && enemyToTavern > 1) {
        return moveTo(closestEnemy);
      } else if (me.life < closestEnemy.hero.life - 20) {
        return moveTo(bestTavern);
      }
    }

    if (bestGold == null) {
      if (distanceToTavern > 2) {
        return moveTo(bestTavern);
      }
    } else {
      int distanceToGold = getPath(myTile, bestGold).size() - 1;
      if (me.life < 20 + distanceToGold) {
        return moveTo(bestTavern);
      }
      return moveTo(bestGold);
    }

    return Dir.STAY;
  }

  private void computeValueTown() {
    valueTown.clear();
    for (Tile tile : tiles) {
      valueTown.put(tile, 0d);
    }

    // every one distance in map traversal is 10 points
    // terror values will be relative to that.

    for (Hero hero : state.getHeroes()) {
      if (hero == me) {
        continue;
      }

      addTerror(hero.tile, 500);
      traverse(hero.tile, new Searcher() {
        @Override
        public Command handle(Tile current, Tile next, int depth) {
          addTerror(next, 500.0 / Math.pow(2, depth));
          return next.isWalkable() ? Command.CONTINUE : Command.SKIP;
        }
      });
    }

    for (Tile tile : tiles) {

    }
  }

  private void addTerror(Tile tile, double d) {
    valueTown.put(tile, valueTown.get(tile) + d);
  }

  private Dir moveTo(Tile target) {
    List<Tile> path = getPath(myTile, target);
    if (path == null) {
      return null;
    }
    return getDir(path.get(0), path.get(1));
  }

  private Tile getBest(Function<Tile, Double> heuristic) {
    Tile best = null;
    double bestScore = 0;
    for (Tile tile : tiles) {
      double score = heuristic.apply(tile);
      if (score > bestScore) {
        best = tile;
        bestScore = score;
      }
    }

    return best;
  }

  @SafeVarargs
  private final Function<Tile, Double> combine(final Function<Tile, Double>... heuristics) {
    return new Function<Tile, Double>() {
      @Override
      public Double apply(Tile tile) {
        double ret = 1;
        for (Function<Tile, Double> f : heuristics) {
          ret *= f.apply(tile);
          if(ret == 0){
            break;
          }
        }
        return ret;
      }
    };
  }

  private final Function<Tile, Double> type(final Type type) {
    return new Function<Tile, Double>() {
      @Override
      public Double apply(Tile tile) {
        return tile.type == type ? 1d : 0d;
      }
    };
  }

  private final Function<Tile, Double> notMe = new Function<Tile, Double>() {
      @Override
      public Double apply(Tile tile) {
        return tile.hero == me ? 0d : 1d;
      }
  };

  private final Function<Tile, Double> proximity = new Function<Tile, Double>() {
    @Override
    public Double apply(Tile tile) {
      List<Tile> path = getPath(myTile, tile);
      if (path == null) {
        return 0d;
      }
      return (double) (100000 - path.size());
    }
  };

  private final Function<Tile, Double> safeProximity = new Function<Tile, Double>() {
    @Override
    public Double apply(Tile tile) {
      List<Tile> path = getPath(myTile, tile);
      if (path == null) {
        return 0d;
      }
      double ret = (100000 - path.size() * 10);

      for (Tile t : path.subList(1, path.size())) {
        ret -= valueTown.get(t);
      }

      return ret;
    }
  };

  /**
   * The returned list starts with 'from' and ends with 'to'.
   * 
   * Returns 'null' if there is no path.
   */
  private List<Tile> getPath(Tile from, final Tile to) {
    checkNotNull(from);
    checkNotNull(to);

    if (from == to) {
      return ImmutableList.of(from);
    }

    final Map<Tile, Tile> breadcrumbs = Maps.newHashMap();

    boolean found = traverse(from, new Searcher() {
      @Override
      public Command handle(Tile current, Tile next, int depth) {
        breadcrumbs.put(next, current);

        if (next == to) {
          return Command.HALT;
        }

        if (!next.isWalkable()) {
          return Command.SKIP;
        }

        return Command.CONTINUE;
      }
    });

    if (!found) {
      return null;
    }

    return constructPath(from, to, breadcrumbs);
  }

  private boolean traverse(Tile from, Searcher searcher) {
    // we reuse this buffer for performance
    List<Tile> buffer = new ArrayList<>(4);

    Map<Tile, Integer> depthMap = Maps.newHashMap();
    Queue<Tile> q = Lists.newLinkedList();
    q.add(from);
    depthMap.put(from, 0);

    while (!q.isEmpty()) {
      Tile current = q.poll();
      int depth = depthMap.get(current);
      for (Tile next : getAdjacentTiles(current, buffer)) {
        if (next == null || depthMap.containsKey(next)) {
          continue;
        }

        Command command = searcher.handle(current, next, depth + 1);
        if (command == Command.HALT) {
          return true;
        } else if (command == Command.SKIP) {
          continue;
        } else if (command == Command.CONTINUE) {
          q.add(next);
          depthMap.put(next, depth + 1);
        }
      }
    }
    return false;
  }

  private static interface Searcher {
    public Command handle(Tile current, Tile next, int depth);
  }

  public static enum Command {
    HALT, SKIP, CONTINUE;
  }

  private Iterable<Tile> getAdjacentTiles(Tile tile, List<Tile> buffer) {
    buffer.clear();

    buffer.add(state.getTile(tile.i - 1, tile.j));
    buffer.add(state.getTile(tile.i + 1, tile.j));
    buffer.add(state.getTile(tile.i, tile.j - 1));
    buffer.add(state.getTile(tile.i, tile.j + 1));

    return buffer;
  }

  private List<Tile> constructPath(Tile from, Tile to, Map<Tile, Tile> breadcrumbs) {
    List<Tile> ret = Lists.newArrayList();
    Tile current = to;
    while (current != from) {
      ret.add(current);
      current = breadcrumbs.get(current);
      checkNotNull(current);
    }
    ret.add(from);
    return Lists.reverse(ret);
  }

  private Dir getDir(Tile a, Tile b) {
    if (a.i < b.i) {
      return Dir.EAST;
    } else if (a.i > b.i) {
      return Dir.WEST;
    } else if (a.j < b.j) {
      return Dir.SOUTH;
    } else if (a.j > b.j) {
      return Dir.NORTH;
    }
    return Dir.STAY;
  }

}
