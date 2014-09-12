package vindinium.learn;

import jasonlib.IO;
import jasonlib.Json;
import jasonlib.Rect;
import jasonlib.swing.Graphics3D;
import jasonlib.swing.component.GFrame;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.apache.log4j.BasicConfigurator;
import vindinium.learn.VindDB.Scenario;
import vindinium.model.Dir;
import vindinium.model.GameState;
import vindinium.model.Tile;
import vindinium.model.Tile.Type;

public class ReplayUI extends JComponent {

  private static final File dir = new File("C:/shit/vind");
  private static final Font font = new Font("Arial", Font.BOLD, 12);

  private final VindDB db = VindDB.get();

  private int index = 1;
  private GameState state;
  private int size;

  public ReplayUI() {
    this(null);
  }

  public ReplayUI(GameState state) {
    if (state == null) {
      listen();
      refresh();
    } else {
      this.state = state;
    }
  }

  @Override
  protected void paintComponent(Graphics gg) {
    Graphics3D g = Graphics3D.create(gg);

    size = getWidth() / state.board.length;

    paintBoard(g);
    paintGrid(g);
    paintMove(g);
  }

  private void paintMove(Graphics3D g) {
    Dir dir = Dir.valueOf(state.json.get("myMove"));
    Rect r = new Rect(state.me.x * size, state.me.y * size, size, size);
    r = r.translate(dir.vx * size, dir.vy * size);
    g.color(new Color(0, 255, 0, 100)).fill(r);
  }

  private void paintGrid(Graphics3D g) {
    g.color(Color.black);
    for (int i = size; i < getWidth(); i += size) {
      g.line(i, 0, i, getHeight());
    }
    for (int i = size; i < getHeight(); i += size) {
      g.line(0, i, getWidth(), i);
    }
  }

  private void paintBoard(Graphics3D g) {
    Tile[][] tiles = state.board;
    int size = getWidth() / tiles.length;
    for (int i = 0; i < tiles.length; i++) {
      for (int j = 0; j < tiles.length; j++) {
        Tile tile = tiles[i][j];
        Rect r = new Rect(i * size, j * size, size, size);
        if (tile.type == Type.EMPTY || tile.type == Type.HERO) {
          g.color(Color.gray).fill(r);
        } else if (tile.type == Type.WOOD) {
          g.color(Color.black).fill(r);
        } else if (tile.type == Type.TAVERN) {
          g.color(Color.CYAN).fill(r);
        } else if (tile.type == Type.GOLD) {
          g.color(Color.orange).fill(r);
        }
        if (tile.hero != null) {
          g.font(font).color(Color.black).text(tile.hero.name, r);
          if (tile.type != Type.GOLD) {
            g.text(tile.hero.life + "", r.moveY(15));
          }
        }
      }
    }
  }

  private void refresh() {
    File file = new File(dir, index + ".json");
    Json json = IO.from(file).toJson();
    GameState state = new GameState(json);
    this.state = state;
    repaint();
  }

  private void listen() {
    addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_LEFT) {
          index = Math.max(index - 1, 1);
          refresh();
        } else if (code == KeyEvent.VK_RIGHT) {
          index++;
          refresh();
        } else if (code == KeyEvent.VK_W) {
          db.logGoodMove(state, Dir.NORTH);
        } else if (code == KeyEvent.VK_A) {
          db.logGoodMove(state, Dir.WEST);
        } else if (code == KeyEvent.VK_S) {
          db.logGoodMove(state, Dir.SOUTH);
        } else if (code == KeyEvent.VK_D) {
          db.logGoodMove(state, Dir.EAST);
        }
      }
    });

    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        int button = e.getButton();
        if (button == MouseEvent.BUTTON3) {
          db.logBadMove(state);
        }
      }
    });
  }

  public static void display(Scenario s) {
    ReplayUI ui = new ReplayUI(s.state);
    GFrame.create().content(ui).size(666, 688).start();
  }

  public static void main(String[] args) {
    BasicConfigurator.configure();

    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        ReplayUI ui = new ReplayUI();
        GFrame.create().content(ui).size(666, 688).start();
        ui.requestFocusInWindow();
      }
    });
  }

}
