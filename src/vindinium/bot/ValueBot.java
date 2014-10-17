package vindinium.bot;

import java.awt.Point;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import vindinium.model.Dir;
import vindinium.model.GameState;
import vindinium.model.Hero;
import vindinium.model.Tile;
import vindinium.model.Tile.Type;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class ValueBot implements AI {

  private static final DecimalFormat format = new DecimalFormat("#");

  private static final Point debugTile = new Point(-16, 2);

  private GameState state;
  private Hero me;
  private Tile myTile;
  private List<Tile> tiles = Lists.newArrayList();
  private Dir lastDir = null;

  /**
   * Marks how valuable different tiles are.
   * 
   * Tiles are lower-value if they are near a hero we don't want to fight.
   * Tiles are higher-value if they are near a mine
   */
  public Map<Tile, Double> valueTown = Maps.newHashMap();

  // tiles which I can get to first.
  private Set<Tile> firstStrikeTiles = Sets.newHashSet();

  // tiles which have a first-strike path to a tavern
  public Set<Tile> safeZoneTiles = Sets.newHashSet();

  // map from a tile to the distance to the nearest tavern
  private Map<Tile, Integer> tavernZones = Maps.newHashMap();

  // map of enemy to their distance from us
  private Map<Hero, Integer> heroDistances = Maps.newHashMap();

  private int distToNearestEnemy;

  private int totalMines;

  private void computeValueTown() {
    calculateSafeZone();
    calculateTavernZones();
    final double NO_VALUE = -100000;

    double myValue = 200 * me.mineCount;
    if (totalMines == 4) {
      myValue *= 2;
    }
    if (totalMines > 8) {
      myValue = Math.max(myValue, 200);
    }

    // double missingLife = 1 - (me.life / 100.0);
    double p = me.life / 100.0;
    p = p * p * 1.5;
    if (p > 1) {
      p = 1;
    }

    double tavernValue = myValue * (1 - p);

    int healthRequiredToMine = 20;
    for (Entry<Hero, Integer> e : heroDistances.entrySet()) {
      if (e.getValue() <= 2) {
        healthRequiredToMine = Math.max(healthRequiredToMine, e.getKey().life + 40);
      }
    }

    if (distToNearestEnemy > 10 && me.life > 50) {
      tavernValue = 0;
    } else {
      for (Entry<Hero, Integer> e : heroDistances.entrySet()) {
        if (safeZoneTiles.contains(myTile) && e.getKey().life < 20) {
          continue;
        }
        int dist = e.getValue();
        if (dist < 3) {
          tavernValue *= 4;
        } else if (dist < 6) {
          tavernValue *= 2;
        } else if (dist < 10) {
          tavernValue *= 1.3;
        }
      }
    }

    valueTown.clear();
    for (Tile tile : tiles) {
      double value = 0;
      if (tile.type == Type.WOOD) {
        value = NO_VALUE;
      } else if (tile.type == Type.GOLD) {
        if (tile.hero != me && me.life > healthRequiredToMine) {
          value = totalMines >= 24 ? 1000 : 600;
          for (int dist : heroDistances.values()) {
            if (dist <= 3) {
              value -= 200;
            } else if (dist <= 4) {
              value -= 100;
            }
          }
        } else {
          value = NO_VALUE;
        }
      } else if (tile.type == Type.TAVERN) {
        if (me.life > 90 || (me.life > 50 && distToNearestEnemy == 1)) {
          value = NO_VALUE;
        } else {
          value = tavernValue * 5;
        }
      }
      valueTown.put(tile, value);
    }

    calculateTactics();

    if (distToNearestEnemy > 1) {
      addValue(myTile, -400, "Standing Still"); // adjust away from standing still.
    }

    computeHeroValues(myValue);

    for (Tile tile : tiles) {
      if (tile.isWalkable()) {
        addValue(tile, tavernValue / Math.pow(1.4, tavernZones.get(tile)), "Tavern");
      }

      if (tile.type == Type.GOLD && tile.hero != me) {
        final double baseValue = 400 - (100 - me.life) * 5;
        traverse(tile, new TileVisitor() {
          @Override
          public void visit(Tile current, Tile next, int depth) {
            addValue(next, baseValue / Math.pow(1.5, depth), "Gold");
          }
        });
      }
    }
  }

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
    init(state);

    return valueAlgo();
  }

  private Dir valueAlgo() {
    Tile best = getBest(combine(onOrAdjacent, value));

    // override algorithm if there is a high-value
    // tile that we can get to safely
    // double bestVal = valueTown.get(best);
    // for (Tile tile : safeZoneTiles) {
    // if (valueTown.get(tile) > bestVal) {
    // bestVal = valueTown.get(tile);
    // best = tile;
    // }
    // }

    List<Tile> path = getPath(myTile, best);
    Dir ret = getDir(myTile, path.size() == 1 ? path.get(0) : path.get(1));
    lastDir = ret;
    return ret;
  }

  private void init(GameState state) {
    this.state = state;
    this.me = state.me;

    tiles.clear();
    for (int i = 0; i < state.board.length; i++) {
      for (int j = 0; j < state.board[i].length; j++) {
        tiles.add(state.board[i][j]);
      }
    }

    myTile = state.getTile(me.x, me.y);

    totalMines = 0;
    for (Tile tile : tiles) {
      if (tile.type == Type.GOLD) {
        totalMines++;
      }
    }

    heroDistances.clear();
    distToNearestEnemy = 1000;
    for (Hero hero : state.heroes.values()) {
      if (hero != me) {
        int dist = getPath(myTile, hero.tile).size() - 1;
        distToNearestEnemy = Math.min(distToNearestEnemy, dist);
        heroDistances.put(hero, dist);
      }
    }

    computeValueTown();
  }

  /**
   * Safe zone is where I can reach a tavern
   */
  private void calculateSafeZone() {
    firstStrikeTiles.clear();
    safeZoneTiles.clear();

    final Map<Tile, Integer> enemyReach = Maps.newHashMap();
    for (Tile tile : tiles) {
      enemyReach.put(tile, 10000);
    }

    for (Hero hero : state.getHeroes()) {
      if (hero == me) {
        continue;
      }

      enemyReach.put(hero.tile, 0);
      traverse(hero.tile, new TileVisitor() {
        @Override
        public void visit(Tile current, Tile next, int depth) {
          enemyReach.put(next, Math.min(depth, enemyReach.get(next)));
        }
      });
    }

    firstStrikeTiles.add(myTile);
    traverse(myTile, new TileVisitor() {
      @Override
      public void visit(Tile current, Tile next, int depth) {
        if (enemyReach.get(next) > depth + 1) {
          firstStrikeTiles.add(next);
        }
      }
    });

    for (Tile tile : firstStrikeTiles) {
      if (isSafe(tile)) {
        safeZoneTiles.add(tile);
      }
    }
  }

  private void calculateTavernZones() {
    tavernZones.clear();

    for (Tile tile : tiles) {
      if (!tile.isWalkable()) {
        continue;
      }

      final AtomicInteger distance = new AtomicInteger(-1);
      traverse(tile, new Searcher() {
        @Override
        public Command handle(Tile current, Tile next, int depth) {
          if (next.type == Type.TAVERN) {
            distance.set(depth);
            return Command.HALT;
          }
          if (!next.isWalkable()) {
            return Command.SKIP;
          }
          return Command.CONTINUE;
        }
      });
      checkState(distance.get() != -1);
      tavernZones.put(tile, distance.get());
    }
  }

  private void calculateTactics() {
    // don't run away from a hero who is at lower health than us
    // because they will keep stabbing us in the back :(
    for (Entry<Hero, Integer> e : heroDistances.entrySet()) {
      if (e.getValue() == 1) {
        Dir badDir = getDir(e.getKey().tile, myTile);
        Tile badTile = state.getTile(myTile.i + badDir.vx, myTile.j + badDir.vy);
        if (badTile != null) {
          addValue(badTile, -1000, "Tactics");
        }
      }
    }

    if (me.mineCount < totalMines / 2 && lastDir != null) {
      Tile badTile = state.getTile(myTile.i + lastDir.vx * -1, myTile.j + lastDir.vy * -1);
      if (badTile != null) {
        addValue(badTile, -1000, "No-looping");
      }
    }
  }

  private void computeHeroValues(double myValue) {
    for (Hero hero : state.getHeroes()) {
      if (hero == me) {
        continue;
      }
      double v = 0;

      boolean canKill = hero.life < me.life - 20;

      if (canKill) {
        // make sure they aren't next to a tavern or on their spawn
        if (hero.tile == hero.spawn || isNextTo(hero.tile, Type.TAVERN)) {
          canKill = false;
        }
      }

      if (heroDistances.get(hero) <= 2 && me.mineCount == 0 && me.life <= 20) {
        v = 200;
      } else {
        if (canKill) {
          if (hero.mineCount > 0) {
            v = 100 * hero.mineCount * hero.mineCount * .9;
          } else {
            v = -50;
          }
        } else {
          v = -myValue - 3;
        }
      }

      final double heroValue = v;

      Tile heroTile = timeTravel(hero);

      addValue(heroTile, heroValue, "Enemy");
      traverse(heroTile, new TileVisitor() {
        @Override
        public void visit(Tile current, Tile next, int depth) {
          if (heroValue < 0 && safeZoneTiles.contains(next)) {
            addValue(next, -1 / Math.pow(1.6, depth), "Safe Enemy");
          } else {
            addValue(next, heroValue / Math.pow(1.6, depth), "Enemy");
          }
        }
      });
      final double spawnValue = hero.life < 22 ? -200 : -10;
      addValue(hero.spawn, spawnValue, "Spawn");
      traverse(hero.spawn, new TileVisitor() {
        @Override
        public void visit(Tile current, Tile next, int depth) {
          if (safeZoneTiles.contains(next)) {
            addValue(next, -1 / Math.pow(1.5, depth), "Near Safe Spawn");
          } else {
            addValue(next, spawnValue / Math.pow(1.5, depth), "Near Spawn");
          }
        }
      });
    }
  }

  // guess where their hero is going so that we can better intercept them
  private Tile timeTravel(Hero hero) {
    int dist = heroDistances.get(hero);
    int turnsForward = dist / 2;
    if (hero.life <= 20) {
      // they are going to the nearest Tavern
      List<Tile> bestPath = null;
      for (Tile t : tiles) {
        if (t.type == Type.TAVERN) {
          List<Tile> path = getPath(hero.tile, t);
          if (bestPath == null || path.size() < bestPath.size()) {
            bestPath = path;
          }
        }
      }

      return bestPath.get(Math.min(turnsForward, bestPath.size() - 1));
    }

    // no prediction
    return hero.tile;
  }

  private void addValue(Tile tile, double d, String description) {
    valueTown.put(tile, valueTown.get(tile) + d);
    if (tile.i == debugTile.x && tile.j == debugTile.y && Math.abs(d) >= 1) {
      System.err.println(description + ": " + format.format(d) +
          " (total = " + format.format(valueTown.get(tile)) + ")");
      // if (Math.abs(d) >= 100 && description.contains("")) {
      // Thread.dumpStack();
      // }
    }
  }

  private Tile getBest(Function<Tile, Double> heuristic) {
    Tile best = null;
    double bestScore = -100000;
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
          if (ret == 0) {
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

  private final Function<Tile, Double> onOrAdjacent = new Function<Tile, Double>() {
    @Override
    public Double apply(Tile tile) {
      int dist = Math.abs(tile.i - myTile.i) + Math.abs(tile.j - myTile.j);
      return dist <= 1 ? 1d : 0d;
    }
  };

  private final Function<Tile, Double> value = new Function<Tile, Double>() {
    @Override
    public Double apply(Tile tile) {
      return valueTown.get(tile) + 100000;
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

  private boolean isSafe(Tile from) {
    return traverse(from, new Searcher() {
      @Override
      public Command handle(Tile current, Tile next, int depth) {
        if (next.type == Type.TAVERN) {
          return Command.HALT;
        }

        if (!firstStrikeTiles.contains(next) || !next.isWalkable()) {
          return Command.SKIP;
        }

        return Command.CONTINUE;
      }
    });
  }

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

  private static abstract class TileVisitor implements Searcher {
    @Override
    public final Command handle(Tile current, Tile next, int depth) {
      if (!next.isWalkable()) {
        return Command.SKIP;
      }
      visit(current, next, depth);
      return Command.CONTINUE;
    }

    public abstract void visit(Tile current, Tile next, int depth);
  }

  public static enum Command {
    HALT, SKIP, CONTINUE;
  }

  private boolean isNextTo(Tile tile, Type type) {
    for (Tile n : getAdjacentTiles(tile)) {
      if (n != null && n.type == type) {
        return true;
      }
    }
    return false;
  }

  private List<Tile> getAdjacentTiles(Tile tile) {
    return getAdjacentTiles(tile, Lists.<Tile>newArrayList());
  }

  private List<Tile> getAdjacentTiles(Tile tile, List<Tile> buffer) {
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
    if (a == b) {
      return Dir.STAY;
    }

    checkState(getAdjacentTiles(a).contains(b));

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
