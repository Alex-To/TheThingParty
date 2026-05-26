package com.helltoxx.thethingparty.game;

/**
 * Фазы игрового цикла The Thing Party.
 *
 * LOBBY      - ожидание команды старта, роли не назначены, способности доступны через дебаг.
 * HEAD_START - "Фаза Форы", роли назначены, но способности Нечто заблокированы.
 * PARANOIA   - основная фаза, всё доступно, идёт таймер тайм-аута победы Нечто.
 * ENDED      - игра завершена, ждём ручного /thingparty game stop для возврата в LOBBY.
 */
public enum GamePhase {
    LOBBY,
    HEAD_START,
    PARANOIA,
    ENDED
}
