package comvyne.patrickstar.internal;

import java.util.Collection;
import java.util.Comparator;
import java.util.function.Function;

/** Pure, deterministic ranking and lease logic used by the Mob controller. */
public final class PathPolicy {
    private PathPolicy() {
    }

    public record Score(int priority, boolean reachable, double distance, long ticket) {
        public Score {
            if (priority < 0 || priority > 65_535 || !Double.isFinite(distance) || distance < 0) {
                throw new IllegalArgumentException("invalid candidate score");
            }
        }
    }

    private static final Comparator<Score> ORDER = Comparator
            .comparingInt(Score::priority).reversed()
            .thenComparing(Comparator.comparing(Score::reachable).reversed())
            .thenComparingDouble(Score::distance)
            .thenComparingLong(Score::ticket);

    public static <T> T choose(Collection<T> candidates, Function<T, Score> score) {
        return candidates.stream().min(Comparator.comparing(score, ORDER)).orElse(null);
    }

    public static boolean canPreempt(int currentPriority, long lockDeadlineNanos,
                                     int newPriority, long nowNanos) {
        return newPriority > currentPriority && nowNanos - lockDeadlineNanos >= 0;
    }

    /**
     * A one-shot can interrupt a non-deferring passive request regardless of
     * relative priority, but never breaks an explicit lock. While a one-shot
     * runs, only a higher-priority one-shot can interrupt it.
     */
    public static boolean canInterrupt(boolean currentPersistent, boolean currentDeferOneShot,
                                       int currentPriority, long lockDeadlineNanos,
                                       boolean candidatePersistent, int candidatePriority,
                                       long nowNanos) {
        if (nowNanos - lockDeadlineNanos < 0) {
            return false;
        }
        if (!currentPersistent) {
            return !candidatePersistent && candidatePriority > currentPriority;
        }
        return candidatePersistent ? candidatePriority > currentPriority : !currentDeferOneShot;
    }
}
