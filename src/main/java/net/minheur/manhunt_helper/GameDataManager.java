package net.minheur.manhunt_helper;

import net.minecraft.server.network.ServerPlayerEntity;

public class GameDataManager {

    public static int timerTicks = 2400;
    public static Phase phase = Phase.WAITING;
    public static int runnerLeft = 0;

    public static boolean allowChooseTeam = true;

    public enum Phase {
        WAITING("waiting"),
        CONFIG("config"),
        PREPARE("prep"),
        HEAD_START("head_start"),
        PLAYING("playing"),
        FINISHED("finished");

        public final String coded;

        Phase(String coded) {
            this.coded = coded;
        }
    }

}
