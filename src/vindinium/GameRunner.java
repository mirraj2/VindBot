package vindinium;

import jasonlib.IO;
import java.awt.Desktop;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import vindinium.bot.AI;
import vindinium.bot.ValueBot;
import vindinium.model.Dir;
import vindinium.model.GameState;
import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import static com.google.common.base.Preconditions.checkNotNull;

public class GameRunner {

  private static final String KEY = "4303bqe3";
  private static final boolean TRAINING = false;
  private static final String baseURL = "http://vindinium.org/api/" + (TRAINING ? "training" : "arena");

  private final AI ai;

  private boolean writeToFile = true;

  public static void main(String[] args) throws Exception {
    new GameRunner(new ValueBot());
  }

  public GameRunner(AI ai) {
    this.ai = ai;

    run();
  }

  private void run() {
    try {
      GameState state = post(baseURL);
      System.out.println("Got initial state!");
      Desktop.getDesktop().browse(new URL(state.viewURL).toURI());

      int turn = 1;
      while (!state.finished) {
        Stopwatch watch = Stopwatch.createStarted();
        Dir dir;
        try {
          dir = ai.act(state);
          checkNotNull(dir);
        } catch (Exception e) {
          dir = Dir.STAY;
          e.printStackTrace();
        }
        System.out.println("Making move: " + dir + " (" + watch + ")");
        if (writeToFile) {
          state.json.with("myMove", dir);
          IO.from(state.json).to(new File("C:/shit/vind/" + turn++ + ".json"));
        }
        state = post(state.playURL, dir);
      }
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }
  }

  private static GameState post(String url) throws Exception {
    return post(url, null);
  }

  private static GameState post(String url, Dir dir) throws Exception {
    URL u = new URL(url);

    HttpURLConnection conn = (HttpURLConnection) u.openConnection();
    conn.setRequestMethod("POST");
    conn.setDoOutput(true);
    conn.setDoInput(true);

    StringBuilder sb = new StringBuilder();
    sb.append("key=" + KEY);

    if (dir != null) {
      sb.append("&dir=" + dir);
    }

    IO.from(sb.toString()).to(conn.getOutputStream());

    int responseCode = conn.getResponseCode();
    if (responseCode != 200) {
      throw new RuntimeException("Got response code: " + responseCode);
    }

    return new GameState(IO.from(conn.getInputStream()).toJson());
  }

}
