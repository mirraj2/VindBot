package vindinium.bot.brute;

import java.util.List;
import vindinium.model.Hero;
import com.google.common.collect.Lists;


public class RHero {

  public final Hero hero;
  public int x, y, life, gold;
  public final List<Integer> mines;

  public RHero(Hero hero) {
    this.hero = hero;
    this.x = hero.x;
    this.y = hero.y;
    this.life = hero.life;
    this.gold = hero.gold;
    this.mines = Lists.newArrayList();
  }

  public RHero(Hero hero, int x, int y, int life, int gold, List<Integer> mines) {
    this.hero = hero;
    this.x = x;
    this.y = y;
    this.life = life;
    this.gold = gold;
    this.mines = mines;
  }

  public RHero copy() {
    return new RHero(hero, x, y, life, gold, Lists.newArrayList(mines));
  }

}
