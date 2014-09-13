package vindinium.learn;

import jasonlib.Json;
import java.util.EnumSet;
import java.util.List;
import org.apache.log4j.Logger;
import vindinium.model.Dir;
import vindinium.model.GameState;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import ez.DB;
import ez.Row;
import ez.Table;

public class VindDB {

  private static final Logger logger = Logger.getLogger(VindDB.class);

  private static final VindDB instance = new VindDB();

  private final DB db;

  private VindDB() {
    db = new DB("localhost", "root", "", "vind");
    logger.info("Connected to DB.");

    if (!db.hasTable("lessons")) {
      db.addTable(new Table("lessons")
          .idColumn()
          .varchar("json", 15000)
          .column("good_moves", String.class)
          .column("bad_moves", String.class)
          .column("enabled", Boolean.class)
          );
    }
  }

  public List<Scenario> getScenarios() {
    List<Scenario> ret = Lists.newArrayList();
    for (Row row : db.select("SELECT * FROM lessons WHERE enabled = TRUE")) {
      GameState state = new GameState(new Json(row.get("json")));
      EnumSet<Dir> goodMoves = EnumSet.noneOf(Dir.class);
      EnumSet<Dir> badMoves = EnumSet.noneOf(Dir.class);
      Splitter splitter = Splitter.on(' ').omitEmptyStrings();
      for (String s : splitter.split(row.get("good_moves"))) {
        goodMoves.add(Dir.valueOf(s));
      }
      for (String s : splitter.split(row.get("bad_moves"))) {
        badMoves.add(Dir.valueOf(s));
      }
      ret.add(new Scenario(row.getLong("id"), state, goodMoves, badMoves));
    }
    return ret;
  }

  public void logBadMove(GameState state) {
    Dir dir = Dir.valueOf(state.json.get("myMove"));
    db.insert("lessons", new Row()
        .with("json", state.json.toString())
        .with("good_moves", "")
        .with("bad_moves", dir)
        .with("enabled", true));

    logger.info("Logged bad move.");
  }

  public void logGoodMove(GameState state, Dir dir) {
    db.insert("lessons", new Row()
        .with("json", state.json.toString())
        .with("good_moves", dir)
        .with("bad_moves", "")
        .with("enabled", true));

    logger.info("Logged good move.");
  }

  public static class Scenario {
    public final long id;
    public final GameState state;
    public final EnumSet<Dir> goodMoves, badMoves;

    public Scenario(long id, GameState state, EnumSet<Dir> goodMoves, EnumSet<Dir> badMoves) {
      this.id = id;
      this.state = state;
      this.goodMoves = goodMoves;
      this.badMoves = badMoves;
    }
  }

  public static VindDB get() {
    return instance;
  }
}
