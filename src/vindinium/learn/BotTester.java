package vindinium.learn;

import java.util.List;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import vindinium.bot.AI;
import vindinium.bot.StrategyBot;
import vindinium.learn.VindDB.Scenario;
import vindinium.model.Dir;

public class BotTester {

  private static final Logger logger = Logger.getLogger(BotTester.class);

  private static int BREAKPOINT = 0;

  private final AI ai = new StrategyBot();

  private final VindDB db = VindDB.get();

  private void run() {
    int successCount = 0;

    List<Scenario> scenarios = db.getScenarios();
    int c = 1;

    boolean displayed = false;

    for (Scenario s : scenarios) {
      if (BREAKPOINT > 0 && c != BREAKPOINT) {
        c++;
        continue;
      }
      logger.debug("Scenario " + c);
      c++;

      Dir botAction;
      try {
        botAction = ai.act(s.state);
      } catch (Exception e) {
        e.printStackTrace();
        botAction = null;
      }

      boolean success = true;
      if (botAction == null) {
        success = false;
        logger.warn("Bot died.");
      } else {
        if (s.badMoves.contains(botAction)) {
          success = false;
          logger.warn("Bot tried to make a bad move!");
        }
        if (!s.goodMoves.isEmpty() && !s.goodMoves.contains(botAction)) {
          success = false;
          logger.warn("Bot went " + botAction + "  good move: " + s.goodMoves);
        }
      }

      if (success) {
        successCount++;
      } else {
        if (!displayed) {
          displayed = true;
          ReplayUI.display(s);
        }
      }
    }

    logger.info(successCount + "/" + scenarios.size() + " were successful.");
  }

  public static void main(String[] args) {
    BasicConfigurator.configure();

    new BotTester().run();
  }

}
