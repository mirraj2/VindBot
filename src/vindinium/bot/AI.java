package vindinium.bot;

import vindinium.model.Dir;
import vindinium.model.GameState;

public interface AI {

  public Dir act(GameState state);

}
