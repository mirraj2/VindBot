package vindinium.bot.brute;

import vindinium.bot.AI;
import vindinium.model.Dir;
import vindinium.model.GameState;
import com.google.common.base.Stopwatch;

public class BruteBot implements AI {

  // create the massive tree
  // in each of the four branches, ask "what is the worst possible outcome"
  // choose the branch that has the best worst outcome.

  int totalStates = 0;
  int MAX_DEPTH;
  int turn = 0;


  @Override
  public Dir act(GameState state) {
    turn++;
    boolean showTop = false;
    totalStates = 0;

    Stopwatch watch = Stopwatch.createStarted();
    Snapshot snapshot = new Snapshot(state);

    if (showTop) {
      for (int i = 0; i < 10; i++) {
        MAX_DEPTH = i;
        Action action = getBestAction(snapshot, 0, new Node());
        System.out.println("depth " + i + " -> " + action);
      }
    }

    MAX_DEPTH = 9;

    Node root = new Node();
    Dir ret = getBestAction(snapshot, 0, root).dir;

    Node current = root;
    while (current != null && current.action != null) {
      System.out.println(current.action);
      current = current.next();
    }

    System.out.println(totalStates + " states (" + watch + ")");
    return ret;
  }

  private Action getBestAction(Snapshot snapshot, int depth, Node node) {
    Action bestAction = new Action();
    for (Dir dir : Dir.values()) {
      if (!snapshot.canMove(dir)) {
        continue;
      }
      Node next = new Node();
      node.links[dir.ordinal()] = next;
      double score = getScore(snapshot, dir, depth, next);
      if (score > bestAction.score) {
        bestAction.dir = dir;
        bestAction.score = score;
      }
    }

    node.action = bestAction;

    return bestAction;
  }

  private double getScore(Snapshot state, Dir dir, int depth, Node node) {
    if (depth == MAX_DEPTH) {
      totalStates++;
      return state.computeScore();
    }

    Action worstAction = null;
    for (Snapshot nextState : state.mutate(dir)) {
      // figure out which of these mutated states is the worst for us.
      // that is the score of going this direction.
      Action action = getBestAction(nextState, depth + 1, node);
      if (worstAction == null || worstAction.score > action.score) {
        worstAction = action;
      }
    }

    if (worstAction == null) {
      return -100000;
    }

    return worstAction.score;
  }

  private static class Action {
    private Dir dir;
    private double score = -100000;

    @Override
    public String toString() {
      return dir + " " + score;
    }
  }

  private static class Node {
    private final Node[] links = new Node[5]; // 1 for each direction
    private Action action;

    public Node next() {
      return links[action.dir.ordinal()];
    }
  }

}
