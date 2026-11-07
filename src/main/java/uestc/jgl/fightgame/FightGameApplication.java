package uestc.jgl.fightgame;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 启动类：替代原控制台版 Main 的入口
 */
@SpringBootApplication
public class FightGameApplication {

    public static void main(String[] args) {
        SpringApplication.run(FightGameApplication.class, args);
    }
}
