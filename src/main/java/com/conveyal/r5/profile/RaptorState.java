package com.conveyal.r5.profile;

import java.util.Arrays;
import java.util.BitSet;

/**
 * Tracks the state of a RAPTOR search. We have a separate class because we need to clone it when doing Monte Carlo
 * frequency searches.
 *
 * @author mattwigway
 */
public class RaptorState {
    /** Previous state (one less transfer) */
    public RaptorState previous;

    /** departure time for this state */
    public int departureTime;

    /** Best times to reach stops, whether via a transfer or via transit directly */
    public int[] bestTimes;

    /** The best times for reaching stops via transit rather than via a transfer from another stop */
    public int[] bestNonTransferTimes;


    /**
     * The previous pattern used to get to this stop, parallel to bestNonTransferTimes. Used to apply transfer rules. This is conceptually
     * similar to the "parent pointer" used in the RAPTOR paper to allow reconstructing paths. This could
     * be used to reconstruct a path (although potentially not the one that was used to get to a particular
     * location, as a later round may have found a faster but more-transfers way to get there). A path
     * reconstructed this way will thus be optimal in the earliest-arrival sense but may not have the
     * fewest transfers; in fact, it will tend not to.
     *
     * Consider the case where there is a slower one-seat ride and a quicker route with a transfer
     * to get to a transit center. At the transit center you board another vehicle. If it turns out
     * that you still catch that vehicle at the same time regardless of which option you choose,
     * general utility theory would suggest that you would choose the one seat ride due to a) the
     * inconvenience of the transfer and b) the fact that most people have a smaller disutility for
     * in-vehicle time than waiting time, especially if the waiting is exposed to the elements, etc.
     *
     * However, this implementation will find the more-transfers trip because it doesn't know where you're
     * going from the transit center, whereas true RAPTOR would find both. It's not non-optimal in the
     * earliest arrival sense, but it's also not the only optimal option.
     *
     * All of that said, we could reconstruct paths simply by storing one more parallel array with
     * the index of the stop that you boarded a particular pattern at. Then we can do the typical
     * reverse-optimization step.
     */
    public int[] previousPatterns;

    /** The stop the previous pattern was boarded at */
    public int[] previousStop;

    /** If this stop is optimally reached via a transfer, the stop we transferred from */
    public int[] transferStop;

    public RaptorState (int nStops) {
        this.bestTimes = new int[nStops];
        this.bestNonTransferTimes = new int[nStops];

        Arrays.fill(bestTimes, RaptorWorker.UNREACHED);
        Arrays.fill(bestNonTransferTimes, RaptorWorker.UNREACHED);

        this.previousPatterns = new int[nStops];
        this.previousStop = new int[nStops];
        this.transferStop = new int[nStops];
        Arrays.fill(previousPatterns, -1);
        Arrays.fill(previousStop, -1);
        Arrays.fill(transferStop, -1);
    }

    /**
     * copy constructor, use only when progressing from one round to the next to maintain consistent reachedThisRound data
     */
    private RaptorState(RaptorState state) {
        this.bestTimes = Arrays.copyOf(state.bestTimes, state.bestTimes.length);
        this.bestNonTransferTimes = Arrays.copyOf(state.bestNonTransferTimes, state.bestNonTransferTimes.length);
        this.previousPatterns = Arrays.copyOf(state.previousPatterns, state.previousPatterns.length);
        this.previousStop = Arrays.copyOf(state.previousStop, state.previousStop.length);
        this.transferStop = Arrays.copyOf(state.transferStop, state.transferStop.length);
        this.departureTime = state.departureTime;
        this.previous = state;
    }

    /** Copy this raptor state to progress to the next round. Clears reachedThisRound so should be used only to progress to the next round. */
    public RaptorState copy () {
        return new RaptorState(this);
    }

    /**
     * Set this state to the min values found in this state or the other passed in (used in Range RAPTOR).
     */
    public void min (RaptorState other) {
        int nStops = this.bestTimes.length;
        for (int stop = 0; stop < nStops; stop++) {
            // prefer times from other when breaking tie as other is earlier in RAPTOR search and thus has fewer transfers
            if (other.bestTimes[stop] <= this.bestTimes[stop]) {
                this.bestTimes[stop] = other.bestTimes[stop];
                this.transferStop[stop] = other.transferStop[stop];
            }

            if (other.bestNonTransferTimes[stop] <= this.bestNonTransferTimes[stop]) {
                this.bestNonTransferTimes[stop] = other.bestNonTransferTimes[stop];
                this.previousPatterns[stop] = other.previousPatterns[stop];
                this.previousStop[stop] = other.previousStop[stop];
            }
        }
    }

    /** dump this as a string */
    public String dump (int stop) {
        Path p = new Path(this, stop);

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < p.length; i++) {
            sb.append(String.format("Stop %5d at %5d, reached by pattern %5d from stop %5d\n", p.alightStops[i], p.alightTimes[i], p.patterns[i], p.boardStops[i]));
        }

        return sb.toString();
    }

    /** Do a deep copy of this RaptorState and all parent raptor states. */
    public RaptorState deepCopy() {
        RaptorState state = this;
        RaptorState ret = this.copy();
        RaptorState copy = ret;

        while (state.previous != null) {
            copy.previous = state.previous.copy();
            copy.previous.previous = null;
            state = state.previous;
            copy = copy.previous;
        }

        return ret;
    }
}