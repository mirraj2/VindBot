package vindinium.bot;

import jasonlib.swing.component.GFrame;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JPanel;
import vindinium.GameRunner;
import vindinium.model.Dir;
import vindinium.model.GameState;

public class ManualBot extends JPanel implements AI {

  private Dir action = Dir.STAY;

  public ManualBot() {
    addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_W) {
          action = Dir.NORTH;
        } else if (code == KeyEvent.VK_D) {
          action = Dir.EAST;
        } else if (code == KeyEvent.VK_S) {
          action = Dir.SOUTH;
        } else if (code == KeyEvent.VK_A) {
          action = Dir.WEST;
        } else if (code == KeyEvent.VK_SPACE) {
          action = Dir.STAY;
        }
      }
    });

    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        requestFocus();
      }
    });
  }

  @Override
  public Dir act(GameState state) {
    return action;
  }

  public static void main(String[] args) {
    ManualBot hehe = new ManualBot();
    new GFrame().content(hehe).start();
    hehe.requestFocusInWindow();
    new GameRunner(hehe);
    hehe.requestFocus();
  }

}
